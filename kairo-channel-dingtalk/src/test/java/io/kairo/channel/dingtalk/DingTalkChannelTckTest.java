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
package io.kairo.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelMessage;
import io.kairo.channel.tck.ChannelTCK;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Runs {@link ChannelTCK} against {@link DingTalkChannel}. Inbound messages are dispatched via the
 * {@link DingTalkChannel#dispatchInbound(ChannelMessage)} overload so the TCK's record-equality
 * assertions stay well-defined — the mapper's own round-trip is covered by {@link
 * DingTalkMessageMapperTest}.
 */
class DingTalkChannelTckTest extends ChannelTCK {

    @Override
    protected Channel newChannel() {
        DingTalkSignatureVerifier signer = new DingTalkSignatureVerifier("tck-signing-secret");
        ObjectMapper objectMapper = new ObjectMapper();
        DingTalkOutboundClient outbound =
                new DingTalkOutboundClient(
                        HttpClient.newHttpClient(),
                        objectMapper,
                        "https://oapi.dingtalk.com/robot/send?access_token=tck",
                        signer,
                        Duration.ofSeconds(5));
        DingTalkMessageMapper mapper = new DingTalkMessageMapper("dingtalk-tck", objectMapper);
        return new DingTalkChannel("dingtalk-tck", outbound, mapper, List.of());
    }

    @Override
    protected Mono<ChannelAck> simulateInbound(Channel channel, ChannelMessage message) {
        return ((DingTalkChannel) channel).dispatchInbound(message);
    }
}
