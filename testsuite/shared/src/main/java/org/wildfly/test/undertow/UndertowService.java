/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class UndertowService implements Service<UndertowService> {
    public static final ServiceName DEFAULT_SERVICE_NAME = ServiceName.JBOSS.append("undertow-test-server");

    private Undertow undertow;
    private final String address;
    private final int port;
    private final HttpHandler handler;

    public UndertowService(final String address, final int port, final HttpHandler handler) {
        this.address = address;
        this.port = port;
        this.handler = handler;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        undertow = Undertow.builder().addHttpListener(port, address).setHandler(handler).build();
        undertow.start();
        Logger.getLogger(UndertowService.class).infof("Started Undertow on %s:%d", address, port);
    }

    @Override
    public synchronized void stop(final StopContext context) {
        undertow.stop();
        undertow = null;
    }

    @Override
    public UndertowService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
