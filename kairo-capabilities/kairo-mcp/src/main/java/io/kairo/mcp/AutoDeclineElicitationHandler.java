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
package io.kairo.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Security-first {@link ElicitationHandler} default that declines all elicitation requests.
 *
 * <p>Use {@link McpClientBuilder#onElicitation(ElicitationHandler)} to provide an explicit
 * interactive handler when trusted servers require user prompts.
 */
public class AutoDeclineElicitationHandler implements ElicitationHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(AutoDeclineElicitationHandler.class);

    @Override
    public Mono<ElicitationResponse> handle(ElicitationRequest request) {
        logger.warn(
                "Auto-declining elicitation request by default: message='{}', schema={}",
                request.message(),
                request.requestedSchema());
        return Mono.just(ElicitationResponse.decline());
    }
}
