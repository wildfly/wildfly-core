/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.remoting3.Channel;
import org.jboss.remotingjmx.ServerMessageInterceptor;
import org.jboss.remotingjmx.ServerMessageInterceptorFactory;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link ServerMessageInterceptorFactory} responsible for supplying a {@link ServerMessageInterceptor} for associating the
 * Subject of the remote user with the current request.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ServerInterceptorFactory implements ServerMessageInterceptorFactory {

    @Override
    public ServerMessageInterceptor create(Channel channel) {
        return new Interceptor(channel);
    }

    private static class Interceptor implements ServerMessageInterceptor {

        private final Channel channel;

        private Interceptor(final Channel channel) {
            this.channel = channel;
        }

        @Override
        public void handleEvent(final Event event) throws IOException {
            final SecurityIdentity localIdentity = channel.getConnection().getLocalIdentity();

            InetSocketAddress peerSocketAddress = channel.getConnection().getPeerAddress(InetSocketAddress.class);
            final InetAddress remoteAddress = peerSocketAddress != null ? peerSocketAddress.getAddress() : null;

            try {
                AccessAuditContext.doAs(localIdentity, remoteAddress, new PrivilegedExceptionAction<Void>() {

                    @Override
                    public Void run() throws IOException {
                        SecurityActions.currentAccessAuditContext().setAccessMechanism(AccessMechanism.JMX);
                        event.run();

                        return null;
                    }
                });
            } catch (PrivilegedActionException e) {
                Exception cause = e.getException();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw new IOException(cause);
                }
            }

        }

    }

}
