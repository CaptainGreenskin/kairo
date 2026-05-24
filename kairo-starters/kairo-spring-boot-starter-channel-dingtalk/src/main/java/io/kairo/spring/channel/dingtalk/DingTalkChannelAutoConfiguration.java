/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.spring.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.gateway.Channel;
import io.kairo.channel.dingtalk.DingTalkChannel;
import io.kairo.channel.dingtalk.DingTalkMessageMapper;
import io.kairo.channel.dingtalk.DingTalkOutboundClient;
import io.kairo.channel.dingtalk.DingTalkSignatureVerifier;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the DingTalk gateway channel starter. Opt-in via {@code
 * kairo.channel.dingtalk.enabled=true}.
 *
 * <p>Registers {@link DingTalkChannel} as a {@link io.kairo.api.gateway.Channel} bean — the
 * kairo-gateway starter then picks it up automatically and wires it into the single {@code Gateway}
 * instance. No separate channel-handler wiring needed.
 *
 * @since v1.2 (post gateway collapse)
 */
@AutoConfiguration
@ConditionalOnClass({DingTalkChannel.class, Channel.class})
@ConditionalOnProperty(
        prefix = "kairo.channel.dingtalk",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(DingTalkProperties.class)
public class DingTalkChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper kairoDingTalkObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpClient kairoDingTalkHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public DingTalkSignatureVerifier kairoDingTalkSignatureVerifier(DingTalkProperties props) {
        if (props.getSigningSecret() == null || props.getSigningSecret().isBlank()) {
            throw new IllegalStateException(
                    "kairo.channel.dingtalk.signing-secret must be configured when "
                            + "kairo.channel.dingtalk.enabled=true");
        }
        return new DingTalkSignatureVerifier(
                props.getSigningSecret(), props.getReplayWindow(), java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public DingTalkMessageMapper kairoDingTalkMessageMapper(
            DingTalkProperties props, ObjectMapper objectMapper) {
        return new DingTalkMessageMapper(props.getChannelId(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DingTalkOutboundClient kairoDingTalkOutboundClient(
            DingTalkProperties props,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            DingTalkSignatureVerifier signer) {
        if (props.getWebhookUrl() == null || props.getWebhookUrl().isBlank()) {
            throw new IllegalStateException(
                    "kairo.channel.dingtalk.webhook-url must be configured when "
                            + "kairo.channel.dingtalk.enabled=true");
        }
        return new DingTalkOutboundClient(
                httpClient,
                objectMapper,
                props.getWebhookUrl(),
                signer,
                props.getOutboundTimeout());
    }

    /**
     * The DingTalk channel itself — exposed as both {@link DingTalkChannel} (so the webhook
     * controller can call {@code dispatchInbound}) and {@link Channel} (so the gateway picks it
     * up). Spring resolves both injection points to the same bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public DingTalkChannel kairoDingTalkChannel(
            DingTalkProperties props,
            DingTalkOutboundClient outboundClient,
            DingTalkMessageMapper mapper) {
        return new DingTalkChannel(
                props.getChannelId(), outboundClient, mapper, props.getAtMobiles());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DingTalkChannel.class, DingTalkSignatureVerifier.class})
    public DingTalkWebhookController kairoDingTalkWebhookController(
            DingTalkChannel channel, DingTalkSignatureVerifier verifier) {
        return new DingTalkWebhookController(channel, verifier);
    }
}
