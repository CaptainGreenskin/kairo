/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.gateway.Attachment;
import io.kairo.api.gateway.MessageType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentTest {

    @Test
    void localFactoryDerivesSizeAndName(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("img.png");
        Files.writeString(file, "hello");
        Attachment att = Attachment.ofLocal(MessageType.IMAGE, file, "image/png");
        assertThat(att.localPath()).isEqualTo(file);
        assertThat(att.fileName()).isEqualTo("img.png");
        assertThat(att.sizeBytes()).isEqualTo(5);
        assertThat(att.mimeType()).isEqualTo("image/png");
    }

    @Test
    void remoteFactoryStoresUrl() {
        Attachment att = Attachment.ofRemote(MessageType.VIDEO, "https://x/y.mp4", "video/mp4");
        assertThat(att.remoteUrl()).isEqualTo("https://x/y.mp4");
        assertThat(att.localPath()).isNull();
    }

    @Test
    void rejectsBothNull() {
        assertThatThrownBy(
                        () -> new Attachment(MessageType.AUDIO, null, null, "audio/ogg", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
