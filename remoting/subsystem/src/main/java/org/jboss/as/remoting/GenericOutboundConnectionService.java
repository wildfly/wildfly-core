/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;

import java.net.URI;

import javax.net.ssl.SSLContext;

import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * A {@link GenericOutboundConnectionService} manages a remote outbound connection which is configured via
 * a {@link URI}. Unlike the remote outbound connection and the local outbound connection where we know the protocol
 * of the connection URI, in the case of generic outbound connection, the protocol can be anything (but needs an appropriate
 * {@link org.jboss.remoting3.spi.ConnectionProviderFactory})
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class GenericOutboundConnectionService extends AbstractOutboundConnectionService implements Service<GenericOutboundConnectionService> {

    static final ServiceName GENERIC_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("generic-outbound-connection");

    private final Consumer<GenericOutboundConnectionService> serviceConsumer;
    private volatile URI destination;

    GenericOutboundConnectionService(final Consumer<GenericOutboundConnectionService> serviceConsumer, final URI destination) {
        Assert.checkNotNullParam("destination", destination);
        this.serviceConsumer = serviceConsumer;
        this.destination = destination;
    }

    @Override
    public void start(final StartContext startContext) {
        serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext stopContext) {
        serviceConsumer.accept(null);
    }

    @Override
    public GenericOutboundConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    void setDestination(final URI uri) {
        this.destination = uri;
    }

    public URI getDestinationUri() {
        return destination;
    }

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return AuthenticationConfiguration.empty();
    }

    public SSLContext getSSLContext() {
        return null;
    }

}
