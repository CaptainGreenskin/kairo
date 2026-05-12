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
package io.kairo.api.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToolOutput sealed hierarchy")
class ToolOutputTest {

    @Test
    @DisplayName("Text variant stores content")
    void textVariant() {
        ToolOutput.Text text = new ToolOutput.Text("hello");
        assertInstanceOf(ToolOutput.class, text);
        assertEquals("hello", text.content());
    }

    @Test
    @DisplayName("Structured variant stores data map")
    void structuredVariant() {
        Map<String, Object> data = Map.of("count", 5, "items", "a,b,c");
        ToolOutput.Structured structured = new ToolOutput.Structured(data);
        assertInstanceOf(ToolOutput.class, structured);
        assertEquals(data, structured.data());
    }

    @Test
    @DisplayName("Binary equals/hashCode based on data and mime")
    void binaryEquality() {
        byte[] data1 = {0x01, 0x02, 0x03};
        byte[] data2 = {0x01, 0x02, 0x03};
        byte[] data3 = {0x04, 0x05};

        ToolOutput.Binary b1 = new ToolOutput.Binary(data1, "application/octet-stream");
        ToolOutput.Binary b2 = new ToolOutput.Binary(data2, "application/octet-stream");
        ToolOutput.Binary b3 = new ToolOutput.Binary(data3, "application/octet-stream");
        ToolOutput.Binary b4 = new ToolOutput.Binary(data1, "image/png");

        // Same data + same mime → equal
        assertEquals(b1, b2);
        assertEquals(b1.hashCode(), b2.hashCode());

        // Different data → not equal
        assertNotEquals(b1, b3);

        // Same data but different mime → not equal
        assertNotEquals(b1, b4);
    }

    @Test
    @DisplayName("Binary is instance of ToolOutput")
    void binaryIsToolOutput() {
        ToolOutput.Binary binary = new ToolOutput.Binary(new byte[] {0x00}, "image/png");
        assertInstanceOf(ToolOutput.class, binary);
    }

    @Test
    @DisplayName("Truncated with Optional.empty() fullOutput")
    void truncatedWithEmptyFullOutput() {
        ToolOutput.Truncated truncated =
                new ToolOutput.Truncated("visible part", 10000L, Optional.empty());
        assertInstanceOf(ToolOutput.class, truncated);
        assertEquals("visible part", truncated.visible());
        assertEquals(10000L, truncated.totalBytes());
        assertTrue(truncated.fullOutput().isEmpty());
    }

    @Test
    @DisplayName("Truncated with URI fullOutput")
    void truncatedWithUri() {
        URI uri = URI.create("file:///tmp/full-output.txt");
        ToolOutput.Truncated truncated =
                new ToolOutput.Truncated("partial...", 50000L, Optional.of(uri));
        assertTrue(truncated.fullOutput().isPresent());
        assertEquals(uri, truncated.fullOutput().get());
    }

    @Test
    @DisplayName("Sealed interface permits exactly 4 variants")
    void sealedPermits() {
        assertTrue(ToolOutput.class.isSealed());
        Class<?>[] permitted = ToolOutput.class.getPermittedSubclasses();
        assertNotNull(permitted);
        assertEquals(4, permitted.length);
    }
}
