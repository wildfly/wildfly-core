/*
 * JBoss, Home of Professional Open Source
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
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
