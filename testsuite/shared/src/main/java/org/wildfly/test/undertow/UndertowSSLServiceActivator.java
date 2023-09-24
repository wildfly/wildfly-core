/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.undertow;

import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

import io.undertow.server.HttpHandler;

public class UndertowSSLServiceActivator extends UndertowServiceActivator {

    @Override
    protected void createService(String address, int port, HttpHandler handler, ServiceBuilder<?> builder) {
        Supplier<SSLContext> res = builder.requires(ServiceName.parse("org.wildfly.security.ssl-context.test-context"));
        UndertowSSLService service = new UndertowSSLService("0.0.0.0", port, handler, res);
        builder.setInstance(service);
    }
}
