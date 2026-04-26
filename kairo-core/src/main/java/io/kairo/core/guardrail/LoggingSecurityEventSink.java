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
package io.kairo.core.guardrail;

import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.guardrail.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link SecurityEventSink} that writes security events to the application log.
 *
 * <p>DENY and MCP_BLOCK events are logged at WARN level; all others at INFO level.
 */
public class LoggingSecurityEventSink implements SecurityEventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingSecurityEventSink.class);

    @Override
    public void record(SecurityEvent event) {
        if (event.type() == SecurityEventType.GUARDRAIL_DENY
                || event.type() == SecurityEventType.MCP_BLOCK) {
            log.warn(
                    "Security event: type={}, agent={}, target={}, phase={}, policy={}, reason={}",
                    event.type(),
                    event.agentName(),
                    event.targetName(),
                    event.phase(),
                    event.policyName(),
                    event.reason());
        } else {
            log.info(
                    "Security event: type={}, agent={}, target={}, phase={}, policy={}",
                    event.type(),
                    event.agentName(),
                    event.targetName(),
                    event.phase(),
                    event.policyName());
        }
    }
}
