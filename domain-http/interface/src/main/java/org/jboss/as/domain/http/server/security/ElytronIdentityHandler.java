/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.http.server.security;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import org.jboss.as.controller.AccessAuditContext;
import org.wildfly.security.auth.server.SecurityIdentity;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * HttpHandler to ensure the Subject for the current authenticated user is correctly associated for the request.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronIdentityHandler implements HttpHandler {

    public static final AttachmentKey<SecurityIdentity> IDENTITY_KEY = AttachmentKey.create(SecurityIdentity.class);

    private final HttpHandler wrapped;

    public ElytronIdentityHandler(final HttpHandler toWrap) {
        this.wrapped = toWrap;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        SecurityIdentity securityIdentity = exchange.getAttachment(IDENTITY_KEY);


        SocketAddress peerSocketAddress = exchange.getConnection().getPeerAddress();
        InetAddress remoteAddress = peerSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) peerSocketAddress).getAddress() : null;

        try {
            AccessAuditContext.doAs(securityIdentity, remoteAddress, (PrivilegedExceptionAction<Void>) () -> {
                wrapped.handleRequest(exchange);
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }

}
