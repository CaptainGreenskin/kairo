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

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default {@link ElicitationHandler} that automatically accepts all elicitation requests with empty
 * data.
 *
 * <p>This is the fallback handler used when no custom handler is configured. It logs the incoming
 * request at INFO level and returns an ACCEPT response with no data.
 *
 * <p>For production use, consider providing a custom {@link ElicitationHandler} that presents the
 * elicitation request to the user and collects actual input.
 *
 * @see ElicitationHandler
 */
public class AutoApproveElicitationHandler implements ElicitationHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(AutoApproveElicitationHandler.class);

    @Override
    public Mono<ElicitationResponse> handle(ElicitationRequest request) {
        logger.info(
                "Auto-approving elicitation request: message='{}', schema={}",
                request.message(),
                request.requestedSchema());
        return Mono.just(ElicitationResponse.accept(Map.of()));
    }
}
