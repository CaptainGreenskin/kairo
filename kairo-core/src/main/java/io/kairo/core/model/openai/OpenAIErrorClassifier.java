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
package io.kairo.core.model.openai;

import io.kairo.core.model.ProviderRetry;

/**
 * Classifies errors from the OpenAI API to determine retryability.
 *
 * <p>This class only classifies — {@link ProviderRetry} handles the actual retry wrapping.
 */
public class OpenAIErrorClassifier {

    /**
     * Determine if an error is transient and worth retrying.
     *
     * <p>Delegates to {@link ProviderRetry#isTransientProviderError(Throwable)} so the retry
     * predicate stays aligned with other providers.
     */
    public boolean isRetryableError(Throwable t) {
        return ProviderRetry.isTransientProviderError(t);
    }
}
