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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the DingTalk channel starter.
 *
 * <p>The starter is <b>opt-in</b>: setting {@code kairo.channel.dingtalk.enabled=true} is required
 * for the auto-configuration to wire any {@code DingTalkChannel} or webhook controller beans. This
 * keeps a DingTalk-free classpath inert — no webhook listener, no outbound client.
 *
 * @since v0.9.1
 */
@ConfigurationProperties(prefix = "kairo.channel.dingtalk")
public class DingTalkProperties {

    /** Master switch. Default {@code false}. */
    private boolean enabled = false;

    /** Logical channel id — stamped into every {@code ChannelMessage.identity().channelId()}. */
    private String channelId = "dingtalk";

    /**
     * Outbound custom-bot webhook URL. Format: {@code
     * https://oapi.dingtalk.com/robot/send?access_token=...}.
     */
    private String webhookUrl;

    /**
     * HMAC-SHA256 signing secret shared with DingTalk. Used both to verify inbound webhook
     * deliveries and to stamp outbound requests with {@code timestamp=...&sign=...} when present.
     */
    private String signingSecret;

    /** Optional list of mobile numbers to @-mention on every reply. */
    private List<String> atMobiles = new ArrayList<>();

    /** Outbound HTTP timeout applied to each send. Default 5 s. */
    private Duration outboundTimeout = Duration.ofSeconds(5);

    /**
     * Replay window for inbound signature verification. Default 1 h (DingTalk's recommendation).
     */
    private Duration replayWindow = Duration.ofHours(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public List<String> getAtMobiles() {
        return atMobiles;
    }

    public void setAtMobiles(List<String> atMobiles) {
        this.atMobiles = atMobiles;
    }

    public Duration getOutboundTimeout() {
        return outboundTimeout;
    }

    public void setOutboundTimeout(Duration outboundTimeout) {
        this.outboundTimeout = outboundTimeout;
    }

    public Duration getReplayWindow() {
        return replayWindow;
    }

    public void setReplayWindow(Duration replayWindow) {
        this.replayWindow = replayWindow;
    }
}
