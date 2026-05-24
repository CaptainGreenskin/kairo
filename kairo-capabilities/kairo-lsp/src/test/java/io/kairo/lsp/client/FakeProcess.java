/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal in-memory Process. {@link #stdinSink} is what the client writes to (we read it on the
 * server side). {@link #serverWriter} is what the server writes to (the client reads from {@link
 * #getInputStream()}).
 */
final class FakeProcess extends Process {

    final PipedInputStream clientStdinReader = new PipedInputStream(16 * 1024);
    final PipedOutputStream stdinSink = new PipedOutputStream();
    final PipedInputStream clientStdoutReader = new PipedInputStream(16 * 1024);
    final PipedOutputStream serverWriter = new PipedOutputStream();
    final InputStream emptyStderr =
            new InputStream() {
                @Override
                public int read() {
                    return -1;
                }
            };
    private final AtomicBoolean alive = new AtomicBoolean(true);

    FakeProcess() throws IOException {
        stdinSink.connect(clientStdinReader);
        serverWriter.connect(clientStdoutReader);
    }

    @Override
    public OutputStream getOutputStream() {
        return stdinSink;
    }

    @Override
    public InputStream getInputStream() {
        return clientStdoutReader;
    }

    @Override
    public InputStream getErrorStream() {
        return emptyStderr;
    }

    @Override
    public int waitFor() {
        while (alive.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
        return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (alive.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return !alive.get();
    }

    @Override
    public int exitValue() {
        if (alive.get()) throw new IllegalThreadStateException("still running");
        return 0;
    }

    @Override
    public void destroy() {
        alive.set(false);
        try {
            stdinSink.close();
        } catch (IOException ignore) {
        }
        try {
            serverWriter.close();
        } catch (IOException ignore) {
        }
    }

    @Override
    public Process destroyForcibly() {
        destroy();
        return this;
    }
}
