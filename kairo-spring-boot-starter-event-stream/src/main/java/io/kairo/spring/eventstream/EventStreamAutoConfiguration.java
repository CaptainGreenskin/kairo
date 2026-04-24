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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer;
import io.kairo.eventstream.DefaultEventStreamService;
import io.kairo.eventstream.EventStreamRegistry;
import io.kairo.eventstream.EventStreamService;
import io.kairo.eventstream.sse.KairoEventStreamSseHandler;
import io.kairo.eventstream.ws.KairoEventStreamWebSocketHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

/**
 * Auto-configuration for the Kairo event-stream starter. Wires {@link EventStreamService} +
 * transports only when the user opts in ({@code kairo.event-stream.enabled=true}) AND provides a
 * {@link KairoEventStreamAuthorizer} bean.
 *
 * <p><b>Deny-safe:</b> the transports never come up without an authorizer — silently binding an
 * "allow everything" default would be a security footgun. This matches the MCP starter's posture.
 *
 * <p>Transports toggle individually via {@code kairo.event-stream.sse.enabled} (default on) and
 * {@code kairo.event-stream.ws.enabled} (default on).
 *
 * @since v0.9
 */
@AutoConfiguration
@ConditionalOnClass({EventStreamService.class, KairoEventBus.class})
@ConditionalOnProperty(
        prefix = "kairo.event-stream",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(EventStreamProperties.class)
public class EventStreamAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventStreamAutoConfiguration.class);

    /** Per-process subscription registry; one per application context. */
    @Bean
    @ConditionalOnMissingBean
    public EventStreamRegistry eventStreamRegistry() {
        return new EventStreamRegistry();
    }

    /**
     * Default {@link EventStreamService}. Requires a user-provided {@link
     * KairoEventStreamAuthorizer} bean — deny-safe: without one this bean does not exist, and
     * consequently neither do the transports.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({KairoEventBus.class, KairoEventStreamAuthorizer.class})
    public EventStreamService eventStreamService(
            KairoEventBus bus,
            KairoEventStreamAuthorizer authorizer,
            EventStreamRegistry registry) {
        log.info(
                "Kairo event-stream service wired (authorizer={})",
                authorizer.getClass().getName());
        return new DefaultEventStreamService(bus, authorizer, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EventStreamService.class)
    @ConditionalOnProperty(
            prefix = "kairo.event-stream.sse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public KairoEventStreamSseHandler kairoEventStreamSseHandler(
            EventStreamService service, ObjectMapper mapper, EventStreamProperties props) {
        return new KairoEventStreamSseHandler(
                service, mapper, props.getDefaultBufferCapacity(), props.getDefaultPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KairoEventStreamSseHandler.class)
    public KairoEventStreamSseController kairoEventStreamSseController(
            KairoEventStreamSseHandler handler) {
        return new KairoEventStreamSseController(handler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EventStreamService.class)
    @ConditionalOnProperty(
            prefix = "kairo.event-stream.ws",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public KairoEventStreamWebSocketHandler kairoEventStreamWebSocketHandler(
            EventStreamService service, ObjectMapper mapper, EventStreamProperties props) {
        return new KairoEventStreamWebSocketHandler(
                service, mapper, props.getDefaultBufferCapacity(), props.getDefaultPolicy());
    }

    /**
     * Maps the WebSocket handler at the configured path. Uses a negative order so it takes
     * precedence over Spring's default request-mapping handler, matching the pattern Spring's own
     * WebSocket samples use.
     */
    @Bean
    @ConditionalOnMissingBean(name = "kairoEventStreamWebSocketHandlerMapping")
    @ConditionalOnBean(KairoEventStreamWebSocketHandler.class)
    public HandlerMapping kairoEventStreamWebSocketHandlerMapping(
            KairoEventStreamWebSocketHandler wsHandler, EventStreamProperties props) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.<String, WebSocketHandler>of(props.getWs().getPath(), wsHandler));
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KairoEventStreamWebSocketHandler.class)
    public WebSocketHandlerAdapter kairoWebSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
