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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 * {@link ExecutionSandbox} that runs commands inside a Docker container via {@link ProcessBuilder}.
 *
 * <p>No Docker Java SDK dependency — the {@code docker} CLI must be on the PATH. Probes
 * availability at construction time and throws {@link UnsupportedOperationException} if Docker is
 * not found.
 *
 * <p>The workspace root is mounted read-only as {@code /workspace} inside the container. Commands
 * are executed via {@code /bin/sh -c}.
 *
 * @since v1.2
 */
public final class DockerSandbox implements ExecutionSandbox {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);
    private static final int READ_BUFFER_SIZE = 4 * 1024;

    private final DockerSandboxConfig config;

    public DockerSandbox(DockerSandboxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        ensureDockerAvailable();
    }

    @Override
    public SandboxHandle start(SandboxRequest request) {
        List<String> cmd = buildDockerCommand(request);
        log.debug("DockerSandbox starting: {}", cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (request.workspaceRoot() != null) {
            pb.directory(request.workspaceRoot().toFile());
        }
        try {
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
        cmd.add("--name");
        cmd.add("kairo-sandbox-" + UUID.randomUUID().toString().substring(0, 8));
        cmd.add("--cpus");
        cmd.add(config.cpuLimit());
        cmd.add("--memory");
        cmd.add(config.memoryLimit());
        cmd.add("--network");
        cmd.add(config.networkMode());

        Path workspaceRoot = request.workspaceRoot();
        if (workspaceRoot != null) {
            cmd.add("-v");
            cmd.add(workspaceRoot.toAbsolutePath() + ":/workspace:ro");
            cmd.add("-w");
            cmd.add("/workspace");
        }

        if (request.env() != null) {
            for (Map.Entry<String, String> entry : request.env().entrySet()) {
                cmd.add("-e");
                cmd.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        cmd.add(config.image());
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add(request.command());
        return cmd;
    }

    private void ensureDockerAvailable() {
        try {
            Process probe = new ProcessBuilder("docker", "version").start();
            probe.waitFor(5, TimeUnit.SECONDS);
            if (probe.exitValue() != 0) {
                throw new UnsupportedOperationException(
                        "Docker is not available or not running. Ensure the Docker daemon is started.");
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnsupportedOperationException(
                    "Docker CLI not found on PATH. Install Docker to use DockerSandbox.", e);
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
            if (!closed.compareAndSet(false, true)) return;
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
                    if (slice < n) truncated.set(true);
                    byte[] chunk = new byte[slice];
                    System.arraycopy(buf, 0, chunk, 0, slice);
                    outputSink.tryEmitNext(new SandboxOutputChunk.Stdout(chunk));
                    emitted += slice;
                }
            } catch (IOException e) {
                log.debug("Docker stream read interrupted: {}", e.getMessage());
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
