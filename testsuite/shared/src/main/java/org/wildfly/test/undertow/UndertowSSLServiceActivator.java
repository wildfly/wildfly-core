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
