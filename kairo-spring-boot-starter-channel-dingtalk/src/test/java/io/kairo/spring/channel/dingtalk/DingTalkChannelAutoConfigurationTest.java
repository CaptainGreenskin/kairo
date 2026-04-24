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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.channel.dingtalk.DingTalkChannel;
import io.kairo.channel.dingtalk.DingTalkSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

class DingTalkChannelAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(DingTalkChannelAutoConfiguration.class));

    @Test
    void disabledByDefault_noBeansWired() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(DingTalkChannel.class);
                    assertThat(ctx).doesNotHaveBean(DingTalkWebhookController.class);
                    assertThat(ctx).doesNotHaveBean(DingTalkSignatureVerifier.class);
                });
    }

    @Test
    void enabledWithoutInboundHandler_channelStaysOffButVerifierExists() {
        runner.withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.signing-secret=s3cret",
                        "kairo.channel.dingtalk.webhook-url=https://oapi.dingtalk.com/robot/send?access_token=stub")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(DingTalkSignatureVerifier.class);
                            assertThat(ctx).doesNotHaveBean(DingTalkChannel.class);
                            assertThat(ctx).doesNotHaveBean(DingTalkWebhookController.class);
                        });
    }

    @Test
    void enabledWithInboundHandler_fullChainWired() {
        runner.withUserConfiguration(HandlerConfig.class)
                .withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.signing-secret=s3cret",
                        "kairo.channel.dingtalk.webhook-url=https://oapi.dingtalk.com/robot/send?access_token=stub",
                        "kairo.channel.dingtalk.channel-id=dingtalk-test")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(DingTalkChannel.class);
                            assertThat(ctx).hasSingleBean(DingTalkWebhookController.class);
                            DingTalkChannel channel = ctx.getBean(DingTalkChannel.class);
                            assertThat(channel.id()).isEqualTo("dingtalk-test");
                        });
    }

    @Test
    void enabledWithoutSigningSecret_failsFast() {
        runner.withUserConfiguration(HandlerConfig.class)
                .withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.webhook-url=https://oapi.dingtalk.com/robot/send?access_token=stub")
                .run(
                        ctx -> {
                            assertThat(ctx).hasFailed();
                            assertThat(ctx.getStartupFailure())
                                    .rootCause()
                                    .hasMessageContaining("signing-secret");
                        });
    }

    @Configuration
    static class HandlerConfig {
        @Bean
        ChannelInboundHandler handler() {
            return message -> Mono.just(ChannelAck.ok());
        }
    }
}
