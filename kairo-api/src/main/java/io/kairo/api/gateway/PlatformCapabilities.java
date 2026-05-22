/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.gateway;

import io.kairo.api.Experimental;

/**
 * Declares which optional gateway features a {@link Channel} supports. The stream consumer and
 * delivery router branch on these capabilities instead of catching UnsupportedOperationException at
 * runtime — both cleaner and lets consumers degrade gracefully (e.g. fall back to one-shot send
 * when {@link #supportsEdit()} is false).
 *
 * <p>Builder pattern lets adapters declare partial support without listing every flag.
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public record PlatformCapabilities(
        boolean supportsTyping,
        boolean supportsEdit,
        boolean supportsDelete,
        boolean supportsDraft,
        boolean supportsImage,
        boolean supportsVideo,
        boolean supportsAudio,
        boolean supportsVoice,
        boolean supportsDocument,
        boolean supportsThreads,
        long maxMessageLength) {

    /**
     * Adapter only handles plain-text send. Reasonable default for webhook / SMS-style channels.
     */
    public static PlatformCapabilities textOnly() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean supportsTyping;
        private boolean supportsEdit;
        private boolean supportsDelete;
        private boolean supportsDraft;
        private boolean supportsImage;
        private boolean supportsVideo;
        private boolean supportsAudio;
        private boolean supportsVoice;
        private boolean supportsDocument;
        private boolean supportsThreads;
        private long maxMessageLength = -1;

        public Builder typing() {
            supportsTyping = true;
            return this;
        }

        public Builder edit() {
            supportsEdit = true;
            return this;
        }

        public Builder delete() {
            supportsDelete = true;
            return this;
        }

        public Builder draft() {
            supportsDraft = true;
            return this;
        }

        public Builder image() {
            supportsImage = true;
            return this;
        }

        public Builder video() {
            supportsVideo = true;
            return this;
        }

        public Builder audio() {
            supportsAudio = true;
            return this;
        }

        public Builder voice() {
            supportsVoice = true;
            return this;
        }

        public Builder document() {
            supportsDocument = true;
            return this;
        }

        public Builder threads() {
            supportsThreads = true;
            return this;
        }

        public Builder maxMessageLength(long v) {
            this.maxMessageLength = v;
            return this;
        }

        public PlatformCapabilities build() {
            return new PlatformCapabilities(
                    supportsTyping,
                    supportsEdit,
                    supportsDelete,
                    supportsDraft,
                    supportsImage,
                    supportsVideo,
                    supportsAudio,
                    supportsVoice,
                    supportsDocument,
                    supportsThreads,
                    maxMessageLength);
        }
    }
}
