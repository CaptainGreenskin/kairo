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

/**
 * Technology Compatibility Kit (TCK) for the expert-team SPIs (ADR-016).
 *
 * <p>Third parties who supply their own {@link io.kairo.api.team.TeamCoordinator} or {@link
 * io.kairo.api.team.EvaluationStrategy} implementation extend the abstract JUnit 5 classes in this
 * package to validate contract compliance.
 *
 * <p>The TCK ships inside the {@code kairo-expert-team} main artifact (rather than as a separate
 * {@code test-jar}) so consumers do not need Maven test-jar wiring; JUnit / AssertJ / Reactor-test
 * are declared as {@code optional} compile-scope dependencies on the module POM.
 *
 * @since v0.10 (Experimental)
 */
package io.kairo.expertteam.tck;
