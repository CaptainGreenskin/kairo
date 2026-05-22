/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.Gateway;
import io.kairo.gateway.mirror.JsonlMirrorStore;
import io.kairo.gateway.mirror.MirrorStore;
import io.kairo.gateway.session.SessionDirectory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link DefaultGateway}. Lets applications wire adapters + optional
 * mirror/session-directory in one call without depending on the impl class directly.
 *
 * <pre>{@code
 * Gateway gw = GatewayBuilder.create()
 *     .adapter(new TelegramAdapter(cfg))
 *     .adapter(new FeishuAdapter(cfg))
 *     .mirrorFile(Path.of("/var/log/kairo/gateway.ndjson"))
 *     .build();
 * gw.start().block();
 * }</pre>
 */
public final class GatewayBuilder {

    private final List<Channel> adapters = new ArrayList<>();
    private SessionDirectory sessions;
    private MirrorStore mirror;

    private GatewayBuilder() {}

    public static GatewayBuilder create() {
        return new GatewayBuilder();
    }

    public GatewayBuilder adapter(Channel adapter) {
        adapters.add(adapter);
        return this;
    }

    public GatewayBuilder sessions(SessionDirectory sessions) {
        this.sessions = sessions;
        return this;
    }

    public GatewayBuilder mirror(MirrorStore mirror) {
        this.mirror = mirror;
        return this;
    }

    /** Convenience: install a JSONL mirror at the given path. */
    public GatewayBuilder mirrorFile(Path file) {
        this.mirror = new JsonlMirrorStore(file);
        return this;
    }

    public Gateway build() {
        return new DefaultGateway(adapters, sessions, mirror);
    }
}
