/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.channel.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.Channel;
import io.kairo.channel.dingtalk.DingTalkChannel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DingTalkChannelAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(DingTalkChannelAutoConfiguration.class));

    @Test
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(DingTalkChannel.class));
    }

    @Test
    void enabledWiresChannelBean() {
        runner.withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.signing-secret=test-secret",
                        "kairo.channel.dingtalk.webhook-url=https://oapi.dingtalk.com/robot/send?access_token=t",
                        "kairo.channel.dingtalk.channel-id=ding-test")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(DingTalkChannel.class);
                            // Same instance should also satisfy the gateway Channel SPI so the
                            // gateway starter picks it up automatically.
                            assertThat(ctx.getBean(DingTalkChannel.class))
                                    .isInstanceOf(Channel.class);
                            assertThat(ctx.getBean(Channel.class).id()).isEqualTo("ding-test");
                        });
    }

    @Test
    void missingSecretFails() {
        runner.withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.webhook-url=https://x/y")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void missingWebhookFails() {
        runner.withPropertyValues(
                        "kairo.channel.dingtalk.enabled=true",
                        "kairo.channel.dingtalk.signing-secret=s")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
