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
package io.kairo.api.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillStoreContractTest {

    @Test
    void spiIsExperimental() {
        assertTrue(SkillStore.class.isAnnotationPresent(io.kairo.api.Experimental.class));
    }

    @Test
    void spiExposesOnlyMinimalSurface() {
        long declaredMethods =
                java.util.Arrays.stream(SkillStore.class.getDeclaredMethods()).count();
        assertEquals(4, declaredMethods, "SkillStore SPI must stay at save/get/delete/list");
        List.of("save", "get", "delete", "list")
                .forEach(
                        name -> {
                            boolean present =
                                    java.util.Arrays.stream(SkillStore.class.getDeclaredMethods())
                                            .anyMatch(m -> m.getName().equals(name));
                            assertTrue(present, "SkillStore missing method: " + name);
                        });
    }
}
