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
import java.util.ArrayList;
import java.util.List;
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
 * {@link ExecutionSandbox} implementation that runs commands inside a Docker container via the
 * {@code docker} CLI (ProcessBuilder — no Docker Java SDK).
 *
 * <p>Behaviour contract (same as {@link LocalProcessSandbox}):
 *
 * <ul>
 *   <li>stderr merged into stdout; all output delivered as {@link SandboxOutputChunk.Stdout}
 *   <li>workspace mounted as {@code /workspace} (read-only if {@link SandboxRequest#readOnly()})
 *   <li>timeout enforced by a watchdog; {@link SandboxExit#timedOut()} when triggered
 *   <li>output capped at {@link SandboxRequest#maxOutputBytes()}; {@link SandboxExit#truncated()}
 *       when exceeded
 * </ul>
 *
 * <p>If the {@code docker} binary is not on {@code PATH} at start time, {@link #start} throws
 * {@link UnsupportedOperationException}.
 *
 * @since v1.2
 */
public final class DockerSandbox implements ExecutionSandbox {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);
    private static final int READ_BUFFER_SIZE = 4 * 1024;

    private final DockerSandboxConfig config;

    public DockerSandbox(DockerSandboxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public SandboxHandle start(SandboxRequest request) {
        Objects.requireNonNull(request, "request");
        if (!Files.isDirectory(request.workspaceRoot())) {
            throw new IllegalArgumentException(
                    "workspaceRoot is not a directory: " + request.workspaceRoot());
        }
        ensureDockerAvailable();

        List<String> cmd = buildDockerCommand(request);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (!request.env().isEmpty()) {
                pb.environment().putAll(request.env());
            }
            Process process = pb.start();
            return new DockerHandle(process, request);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start docker process: " + e.getMessage(), e);
        }
    }

    private List<String> buildDockerCommand(SandboxRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--cpus");
        cmd.add(config.cpuLimit());
        cmd.add("-m");
        cmd.add(config.memoryLimit());
        cmd.add("--network");
        cmd.add(config.networkMode());

        String mountFlag = request.readOnly() ? ":ro" : "";
        cmd.add("-v");
        cmd.add(request.workspaceRoot().toAbsolutePath() + ":/workspace" + mountFlag);
        cmd.add("-w");
        cmd.add("/workspace");

        // Pass env vars as -e KEY=VALUE
        for (Map.Entry<String, String> entry : request.env().entrySet()) {
            cmd.add("-e");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }

        cmd.add(config.image());
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add(request.command());
        return cmd;
    }

    private static void ensureDockerAvailable() {
        try {
            Process probe =
                    new ProcessBuilder("docker", "version", "--format", "{{.Client.Version}}")
                            .redirectErrorStream(true)
                            .start();
            probe.waitFor(5, TimeUnit.SECONDS);
            if (probe.exitValue() != 0) {
                throw new UnsupportedOperationException(
                        "docker CLI returned non-zero exit; is Docker running?");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnsupportedOperationException("docker availability check interrupted");
        } catch (IOException e) {
            throw new UnsupportedOperationException(
                    "docker CLI not found on PATH: " + e.getMessage());
        }
    }

    private static final class DockerHandle implements SandboxHandle {

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

        DockerHandle(Process process, SandboxRequest request) {
            this.process = process;
            this.request = request;
            this.outputSink = Sinks.many().multicast().onBackpressureBuffer();
            this.exitSink = Sinks.one();
            this.scheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "kairo-docker-watchdog");
                                t.setDaemon(true);
                                return t;
                            });
            this.watchdog =
                    scheduler.schedule(
                            this::onTimeout, request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            this.reader = new Thread(this::pumpAndComplete, "kairo-docker-reader");
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
                        continue;
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
                log.debug("Docker sandbox stream read interrupted: {}", e.getMessage());
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
