/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import io.kairo.api.gateway.Channel;
import io.kairo.gateway.tck.GatewayAdapterTCK;

/** Smoke-test the TCK against the in-tree FakeAdapter so regressions in the TCK itself catch. */
class FakeAdapterTCKTest extends GatewayAdapterTCK {

    @Override
    protected Channel adapter() {
        return new FakeAdapter("fake");
    }
}
