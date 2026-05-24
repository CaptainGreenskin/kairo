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
package io.kairo.spring.eventstream;

import io.kairo.api.Experimental;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.eventstream.EventStreamAuthorizationException;
import io.kairo.eventstream.sse.KairoEventStreamSseHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * HTTP controller wrapping {@link KairoEventStreamSseHandler}. The mapping path is parameterized by
 * {@code kairo.event-stream.sse.path}; when that property is overridden the starter configures an
 * alternative bean path (Spring Boot evaluates {@code @GetMapping} against the raw string so SpEL
 * placeholders must be literal at bind time).
 *
 * @since v0.9 (Experimental)
 */
@Experimental("SSE controller — contract may change in v0.10")
@RestController
public class KairoEventStreamSseController {

    private final KairoEventStreamSseHandler handler;

    public KairoEventStreamSseController(KairoEventStreamSseHandler handler) {
        this.handler = handler;
    }

    @GetMapping(
            path = "${kairo.event-stream.sse.path:/kairo/event-stream/sse}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam(name = "domain", required = false) List<String> domains,
            @RequestParam(name = "eventType", required = false) List<String> eventTypes,
            @RequestParam(name = "bufferCapacity", required = false) Integer bufferCapacity,
            @RequestParam(name = "policy", required = false) String policyName,
            ServerHttpRequest request) {

        BackpressurePolicy policy = parsePolicy(policyName);
        Map<String, String> authContext = extractAuthContext(request);
        return handler.stream(domains, eventTypes, bufferCapacity, policy, authContext);
    }

    /** Maps a deny decision to HTTP 403 with the authorizer-supplied reason. */
    @ExceptionHandler(EventStreamAuthorizationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String onDenied(EventStreamAuthorizationException ex) {
        return ex.getMessage() == null ? "forbidden" : ex.getMessage();
    }

    private static BackpressurePolicy parsePolicy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return BackpressurePolicy.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, String> extractAuthContext(ServerHttpRequest request) {
        Map<String, String> out = new HashMap<>();
        request.getHeaders()
                .forEach(
                        (name, values) -> {
                            if (!values.isEmpty()) {
                                out.put(name.toLowerCase(Locale.ROOT), values.get(0));
                            }
                        });
        return out;
    }
}
