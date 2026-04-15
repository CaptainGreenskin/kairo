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
package io.kairo.api.plan;

/** Status of a plan through its lifecycle. */
public enum PlanStatus {

    /** The plan is in draft state and not yet approved. */
    DRAFT,

    /** The plan has been approved and is ready for execution. */
    APPROVED,

    /** The plan is currently being executed. */
    EXECUTING,

    /** The plan has been completed. */
    COMPLETED
}
