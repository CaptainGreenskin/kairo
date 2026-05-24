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
package io.kairo.expertteam.role;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskFeatureExtractorTest {

    @Test
    void extractsLanguageFromText() {
        TaskFeatures f = TaskFeatureExtractor.extract("Fix the Java authentication module");
        assertThat(f.languages()).contains("java");
    }

    @Test
    void extractsMultipleLanguages() {
        TaskFeatures f = TaskFeatureExtractor.extract("Convert Python script to TypeScript module");
        assertThat(f.languages()).containsExactlyInAnyOrder("python", "typescript");
    }

    @Test
    void extractsFrameworks() {
        TaskFeatures f =
                TaskFeatureExtractor.extract("Add Spring Boot endpoint with Redis caching");
        assertThat(f.frameworks()).containsExactlyInAnyOrder("spring", "redis");
    }

    @Test
    void extractsDomains() {
        TaskFeatures f = TaskFeatureExtractor.extract("Write unit tests for the auth service");
        assertThat(f.domains()).contains("testing");
        assertThat(f.domains()).contains("security");
    }

    @Test
    void extractsActions() {
        TaskFeatures f = TaskFeatureExtractor.extract("Implement a new REST API endpoint");
        assertThat(f.actions()).contains("implement");
    }

    @Test
    void extractsDebugAction() {
        TaskFeatures f =
                TaskFeatureExtractor.extract("Fix the login bug in the authentication flow");
        assertThat(f.actions()).contains("debug");
        assertThat(f.domains()).contains("security");
    }

    @Test
    void extractsReviewAction() {
        TaskFeatures f = TaskFeatureExtractor.extract("Review the database migration code");
        assertThat(f.actions()).contains("review");
        assertThat(f.domains()).contains("database");
    }

    @Test
    void extractsDesignAction() {
        TaskFeatures f = TaskFeatureExtractor.extract("Design the new architecture for the API");
        assertThat(f.actions()).contains("design");
        assertThat(f.domains()).contains("architecture");
    }

    @Test
    void handlesNullInput() {
        TaskFeatures f = TaskFeatureExtractor.extract(null);
        assertThat(f.isEmpty()).isTrue();
    }

    @Test
    void handlesBlankInput() {
        TaskFeatures f = TaskFeatureExtractor.extract("   ");
        assertThat(f.isEmpty()).isTrue();
    }

    @Test
    void caseInsensitive() {
        TaskFeatures f = TaskFeatureExtractor.extract("JAVA SPRING REACT");
        assertThat(f.languages()).contains("java");
        assertThat(f.frameworks()).containsExactlyInAnyOrder("spring", "react");
    }

    @Test
    void normalizesAliases() {
        TaskFeatures f = TaskFeatureExtractor.extract("golang k8s ts");
        assertThat(f.languages()).containsExactlyInAnyOrder("go", "typescript");
        assertThat(f.frameworks()).contains("kubernetes");
    }

    @Test
    void extractsComplexSentence() {
        TaskFeatures f =
                TaskFeatureExtractor.extract(
                        "Investigate the performance issue in the React frontend and fix the SQL query optimization");
        assertThat(f.languages()).contains("sql");
        assertThat(f.frameworks()).contains("react");
        assertThat(f.domains()).containsAnyOf("performance", "frontend");
        assertThat(f.actions()).containsAnyOf("research", "debug");
    }

    @Test
    void extractsDevopsDomain() {
        TaskFeatures f =
                TaskFeatureExtractor.extract("Set up CI pipeline with Docker and deploy to K8s");
        assertThat(f.frameworks()).containsExactlyInAnyOrder("docker", "kubernetes");
        assertThat(f.domains()).contains("devops");
    }
}
