/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.tools.exec;

import io.kairo.api.sandbox.ExecutionSandbox;
import io.kairo.api.sandbox.SandboxExit;
import io.kairo.api.sandbox.SandboxHandle;
import io.kairo.api.sandbox.SandboxOutputChunk;
import io.kairo.api.sandbox.SandboxRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Default {@link ExecutionSandbox} implementation: forks {@code /bin/sh -c <command>} via {@link
 * ProcessBuilder} in the calling JVM.
 *
 * <p>Behaviour contract (all enforced here, asserted by {@code ExecutionSandboxTCK}):
 *
 * <ul>
 *   <li>stderr is merged into stdout via {@link ProcessBuilder#redirectErrorStream(boolean)} —
 *       every chunk is delivered as {@link SandboxOutputChunk.Stdout}
 *   <li>working directory is {@link SandboxRequest#workspaceRoot()}
 *   <li>environment is reset to the process default plus the {@link SandboxRequest#env()} delta
 *   <li>a watchdog kills the process when {@link SandboxRequest#timeout()} elapses; the resulting
 *       {@link SandboxExit#timedOut()} is {@code true}
 *   <li>once {@link SandboxRequest#maxOutputBytes()} bytes have been forwarded, subsequent bytes
 *       are drained silently and {@link SandboxExit#truncated()} is {@code true}
 *   <li>{@link SandboxHandle#cancel()} and {@link SandboxHandle#close()} are idempotent
 * </ul>
 *
 * <p>Instances are stateless and thread-safe; reuse the {@link #INSTANCE} singleton unless a custom
 * scheduler is required.
 *
 * @since v1.1
 */
public final class LocalProcessSandbox implements ExecutionSandbox {

    public static final LocalProcessSandbox INSTANCE = new LocalProcessSandbox();

    private static final Logger log = LoggerFactory.getLogger(LocalProcessSandbox.class);

    /** Maximum bytes read per pump iteration; small enough to keep memory bounded. */
    private static final int READ_BUFFER_SIZE = 4 * 1024;

    @Override
    public SandboxHandle start(SandboxRequest request) {
        Objects.requireNonNull(request, "request");
        if (!Files.isDirectory(request.workspaceRoot())) {
            throw new IllegalArgumentException(
                    "workspaceRoot is not a directory: " + request.workspaceRoot());
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", request.command());
            pb.redirectErrorStream(true);
            pb.directory(request.workspaceRoot().toFile());
            if (!request.env().isEmpty()) {
                Map<String, String> environment = pb.environment();
                environment.putAll(request.env());
            }
            Process process = pb.start();
            return new LocalHandle(process, request);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start sandbox process: " + e.getMessage(), e);
        }
    }

    private static final class LocalHandle implements SandboxHandle {

        private final Process process;
        private final SandboxRequest request;
        private final Sinks.Many<SandboxOutputChunk> outputSink;
        private final Sinks.One<SandboxExit> exitSink;
        private final ScheduledExecutorService scheduler;
        private final ScheduledFuture<?> watchdog;
        private final Thread reader;
        private final AtomicBoolean timedOut = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean truncated = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        LocalHandle(Process process, SandboxRequest request) {
            this.process = process;
            this.request = request;
            this.outputSink = Sinks.many().multicast().onBackpressureBuffer();
            this.exitSink = Sinks.one();
            this.scheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "kairo-sandbox-watchdog");
                                t.setDaemon(true);
                                return t;
                            });
            this.watchdog =
                    scheduler.schedule(
                            this::onTimeout, request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            this.reader = new Thread(this::pumpAndComplete, "kairo-sandbox-reader");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        @Override
        public Flux<SandboxOutputChunk> output() {
            return outputSink.asFlux();
        }

        @Override
        public Mono<SandboxExit> exit() {
            return exitSink.asMono();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true) && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            cancel();
            watchdog.cancel(true);
            scheduler.shutdownNow();
        }

        private void onTimeout() {
            if (process.isAlive()) {
                timedOut.set(true);
                process.destroyForcibly();
            }
        }

        private void pumpAndComplete() {
            long emitted = 0L;
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[READ_BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) != -1) {
                    long remaining = request.maxOutputBytes() - emitted;
                    if (remaining <= 0) {
                        truncated.set(true);
                        continue; // drain to let the process finish writing
                    }
                    int slice = (int) Math.min(n, remaining);
                    if (slice < n) {
                        truncated.set(true);
                    }
                    byte[] chunk = new byte[slice];
                    System.arraycopy(buf, 0, chunk, 0, slice);
                    outputSink.tryEmitNext(new SandboxOutputChunk.Stdout(chunk));
                    emitted += slice;
                }
            } catch (IOException e) {
                log.debug("Sandbox stream read interrupted: {}", e.getMessage());
            } finally {
                completeExit();
            }
        }

        private void completeExit() {
            int code;
            String signal = null;
            try {
                process.waitFor(5, TimeUnit.SECONDS);
                code = process.isAlive() ? -1 : process.exitValue();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            if (timedOut.get()) {
                signal = "TIMEOUT";
                code = -1;
            } else if (cancelled.get()) {
                signal = "CANCELLED";
                code = -1;
            }
            outputSink.tryEmitComplete();
            exitSink.tryEmitValue(new SandboxExit(code, signal, timedOut.get(), truncated.get()));
            watchdog.cancel(true);
            scheduler.shutdownNow();
        }
    }
}
