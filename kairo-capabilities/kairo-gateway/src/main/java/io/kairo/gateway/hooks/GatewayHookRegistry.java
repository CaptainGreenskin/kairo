/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.hooks;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway-scoped hooks that fire around every inbound parse and outbound deliver. Distinct from
 * agent-level {@link io.kairo.api.hook hook chain} (which sits inside the ReAct loop) — these give
 * applications a chance to redact, audit, or veto messages at the transport boundary.
 *
 * <p>Hooks are best-effort: they never throw across the SPI, and a registered hook that does throw
 * is logged and skipped so it can't bring down the gateway.
 */
public final class GatewayHookRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewayHookRegistry.class);

    @FunctionalInterface
    public interface InboundHook {
        /** Inspect or transform an inbound message. Return null to veto delivery. */
        ChannelMessage onInbound(ChannelMessage message);
    }

    @FunctionalInterface
    public interface OutboundHook {
        /** Inspect / mutate {@code content} before it hits the adapter; return new text. */
        String onOutbound(DeliveryTarget target, String content);
    }

    @FunctionalInterface
    public interface ResultHook {
        void onResult(DeliveryTarget target, String content, SendResult result);
    }

    private final List<InboundHook> inboundHooks = new CopyOnWriteArrayList<>();
    private final List<OutboundHook> outboundHooks = new CopyOnWriteArrayList<>();
    private final List<ResultHook> resultHooks = new CopyOnWriteArrayList<>();

    public void onInbound(InboundHook hook) {
        inboundHooks.add(hook);
    }

    public void onOutbound(OutboundHook hook) {
        outboundHooks.add(hook);
    }

    public void onResult(ResultHook hook) {
        resultHooks.add(hook);
    }

    /** Run inbound hooks; returns the (possibly modified) message, or null if vetoed. */
    public ChannelMessage fireInbound(ChannelMessage message) {
        ChannelMessage current = message;
        for (InboundHook h : inboundHooks) {
            try {
                current = h.onInbound(current);
            } catch (Exception e) {
                log.warn("Inbound hook threw: {}", e.toString());
            }
            if (current == null) return null;
        }
        return current;
    }

    public String fireOutbound(DeliveryTarget target, String content) {
        String current = content;
        for (OutboundHook h : outboundHooks) {
            try {
                String next = h.onOutbound(target, current);
                if (next != null) current = next;
            } catch (Exception e) {
                log.warn("Outbound hook threw: {}", e.toString());
            }
        }
        return current;
    }

    public void fireResult(DeliveryTarget target, String content, SendResult result) {
        for (ResultHook h : resultHooks) {
            try {
                h.onResult(target, content, result);
            } catch (Exception e) {
                log.warn("Result hook threw: {}", e.toString());
            }
        }
    }
}
