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
package io.kairo.api.task;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskApiTest {

    @Test
    void taskBuilderDefaults() {
        Task task = Task.builder().subject("Fix bug").build();

        assertNotNull(task.id());
        assertEquals("Fix bug", task.subject());
        assertNull(task.description());
        assertEquals(TaskStatus.PENDING, task.status());
        assertNull(task.owner());
        assertTrue(task.blockedBy().isEmpty());
        assertTrue(task.blocks().isEmpty());
        assertTrue(task.metadata().isEmpty());
        assertNotNull(task.createdAt());
        assertNotNull(task.updatedAt());
    }

    @Test
    void taskBuilderAllFields() {
        Task task =
                Task.builder()
                        .id("t-1")
                        .subject("Deploy")
                        .description("Deploy to prod")
                        .status(TaskStatus.IN_PROGRESS)
                        .owner("agent-1")
                        .addBlockedBy("t-0")
                        .addBlocks("t-2")
                        .metadata("priority", "high")
                        .build();

        assertEquals("t-1", task.id());
        assertEquals("Deploy", task.subject());
        assertEquals("Deploy to prod", task.description());
        assertEquals(TaskStatus.IN_PROGRESS, task.status());
        assertEquals("agent-1", task.owner());
        assertTrue(task.blockedBy().contains("t-0"));
        assertTrue(task.blocks().contains("t-2"));
        assertEquals("high", task.metadata().get("priority"));
    }

    @Test
    void taskSubjectRequired() {
        assertThrows(NullPointerException.class, () -> Task.builder().build());
    }

    @Test
    void taskMutableSetters() {
        Task task = Task.builder().subject("test").build();

        task.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, task.status());

        task.setOwner("new-owner");
        assertEquals("new-owner", task.owner());

        task.setSubject("updated subject");
        assertEquals("updated subject", task.subject());

        task.setDescription("new desc");
        assertEquals("new desc", task.description());
    }

    @Test
    void taskDependencyManagement() {
        Task task = Task.builder().subject("test").build();
        assertTrue(task.isUnblocked());

        task.addBlockedBy("dep-1");
        assertFalse(task.isUnblocked());
        assertTrue(task.blockedBy().contains("dep-1"));

        task.removeBlockedBy("dep-1");
        assertTrue(task.isUnblocked());
    }

    @Test
    void taskBlocksManagement() {
        Task task = Task.builder().subject("test").build();

        task.addBlocks("child-1");
        assertTrue(task.blocks().contains("child-1"));

        task.removeBlocks("child-1");
        assertFalse(task.blocks().contains("child-1"));
    }

    @Test
    void taskToString() {
        Task task = Task.builder().id("t-1").subject("Fix bug").build();
        String str = task.toString();
        assertTrue(str.contains("t-1"));
        assertTrue(str.contains("Fix bug"));
        assertTrue(str.contains("PENDING"));
    }

    @Test
    void taskStatusValues() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals(5, values.length);
        assertNotNull(TaskStatus.valueOf("PENDING"));
        assertNotNull(TaskStatus.valueOf("IN_PROGRESS"));
        assertNotNull(TaskStatus.valueOf("COMPLETED"));
        assertNotNull(TaskStatus.valueOf("FAILED"));
        assertNotNull(TaskStatus.valueOf("CANCELLED"));
    }

    @Test
    void blockedByAndBlocksReturnCopies() {
        Task task = Task.builder().subject("test").addBlockedBy("x").build();
        assertThrows(UnsupportedOperationException.class, () -> task.blockedBy().add("y"));
        assertThrows(UnsupportedOperationException.class, () -> task.blocks().add("y"));
    }

    @Test
    void metadataReturnsCopy() {
        Task task = Task.builder().subject("test").metadata("k", "v").build();
        assertThrows(UnsupportedOperationException.class, () -> task.metadata().put("k2", "v2"));
    }
}
