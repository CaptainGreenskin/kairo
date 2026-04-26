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
package io.kairo.spring.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.channel.dingtalk.DingTalkChannel;
import io.kairo.channel.dingtalk.DingTalkMessageMapper;
import io.kairo.channel.dingtalk.DingTalkOutboundClient;
import io.kairo.channel.dingtalk.DingTalkSignatureVerifier;
import io.kairo.spring.channel.ChannelAutoConfiguration;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the DingTalk channel starter. Opt-in via {@code
 * kairo.channel.dingtalk.enabled=true}.
 *
 * <p>When enabled and a {@link ChannelInboundHandler} bean is present, registers a {@link
 * DingTalkChannel}, its signature verifier, and the webhook controller. Without a handler bean, no
 * channel is wired (deny-safe, mirrors the v0.9 {@link ChannelAutoConfiguration} pattern).
 *
 * @since v0.9.1
 */
@AutoConfiguration
@AutoConfigureAfter(ChannelAutoConfiguration.class)
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChannelInboundHandler.class)
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
