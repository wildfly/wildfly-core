/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.virtualthread;

import io.undertow.server.HttpHandler;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.test.undertow.UndertowServiceActivator;

class BaseActivator extends UndertowServiceActivator {
    private final boolean demanding;

    BaseActivator(boolean demanding) {
        this.demanding = demanding;
    }

    @Override
    protected void createService(String address, int port, HttpHandler handler, ServiceBuilder<?> builder) {
        super.createService(address, port, handler, builder);
        if (demanding) {
            builder.requires(ServiceName.parse("org.wildfly.extension.core-management.virtual-thread-pinning"));
        }
    }

    @Override
    protected HttpHandler getHttpHandler() {
        return new PinningHandler();
    }
}
