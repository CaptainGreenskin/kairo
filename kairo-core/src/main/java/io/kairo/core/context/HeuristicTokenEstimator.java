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
package io.kairo.core.context;

import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Conservative fallback estimator based on message character length.
 *
 * <p>Uses {@code chars * 4 / 3}. This deliberately overestimates in most cases to bias toward
 * earlier compaction rather than context overflow.
 */
public class HeuristicTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(List<Msg> messages) {
        int totalChars = 0;
        for (Msg msg : messages) {
            totalChars += msg.text().length();
        }
        return totalChars * 4 / 3;
    }
}
