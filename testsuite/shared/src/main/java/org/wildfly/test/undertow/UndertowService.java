/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
