/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.undertow;

import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;

public class UndertowSSLService implements Service<UndertowSSLService> {

    private Undertow undertow;
    private final String address;
    private final int port;
    private final HttpHandler handler;
    private final Supplier<SSLContext> sslContext;

    public UndertowSSLService(final String address, final int port, final HttpHandler handler, Supplier<SSLContext> sslContext) {
        this.address = address;
        this.port = port;
        this.handler = handler;
        this.sslContext = sslContext;
    }

    @Override
    public synchronized void start(final StartContext context) {
        undertow = Undertow.builder()
                .addHttpsListener(port, address, sslContext.get())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setHandler(handler)
                .build();
        undertow.start();
        Logger.getLogger(UndertowSSLService.class).infof("Started Undertow on %s:%d", address, port);
    }

    @Override
    public synchronized void stop(final StopContext context) {
        undertow.stop();
        undertow = null;
    }

    @Override
    public UndertowSSLService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
