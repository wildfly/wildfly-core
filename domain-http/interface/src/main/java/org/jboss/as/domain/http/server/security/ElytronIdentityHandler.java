/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
