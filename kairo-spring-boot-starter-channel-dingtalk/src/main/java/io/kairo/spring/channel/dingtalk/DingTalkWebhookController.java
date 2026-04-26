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

import io.kairo.api.channel.ChannelAck;
import io.kairo.channel.dingtalk.DingTalkChannel;
import io.kairo.channel.dingtalk.DingTalkSignatureVerifier;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhook endpoint for DingTalk custom bots.
 *
 * <p>Verifies the {@code timestamp} + {@code sign} request headers against the configured signing
 * secret, then dispatches the raw JSON body into the channel. On signature mismatch returns HTTP
 * 401 without invoking the handler; on handler rejection returns HTTP 500. Happy path returns HTTP
 * 200 with a tiny ok envelope.
 *
 * @since v0.9.1
 */
@RestController
@RequestMapping("/kairo/channel/dingtalk")
public class DingTalkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(DingTalkWebhookController.class);

    private final DingTalkChannel channel;
    private final DingTalkSignatureVerifier verifier;

    public DingTalkWebhookController(DingTalkChannel channel, DingTalkSignatureVerifier verifier) {
        this.channel = channel;
        this.verifier = verifier;
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestHeader(value = "timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "sign", required = false) String signHeader,
            @RequestBody String body) {
        if (timestampHeader == null || signHeader == null) {
            log.debug("DingTalk webhook rejected: missing timestamp/sign headers");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("errcode", 401, "errmsg", "missing signature headers"));
        }
        long ts;
        try {
            ts = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("errcode", 401, "errmsg", "malformed timestamp"));
        }
        if (!verifier.verify(ts, signHeader)) {
            log.debug("DingTalk webhook rejected: signature mismatch or replay window exceeded");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("errcode", 401, "errmsg", "signature mismatch"));
        }

        ChannelAck ack = channel.dispatchInbound(body).block();
        if (ack != null && ack.success()) {
            return ResponseEntity.ok(Map.of("errcode", 0, "errmsg", "ok"));
        }
        String detail = ack == null ? "no ack" : ack.detail();
        log.debug("DingTalk webhook handler returned failure: {}", detail);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("errcode", 500, "errmsg", detail == null ? "handler failed" : detail));
    }
}
