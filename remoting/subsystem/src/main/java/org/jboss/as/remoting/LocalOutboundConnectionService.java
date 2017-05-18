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

import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.OptionMap;

/**
 * A {@link LocalOutboundConnectionService} manages a local remoting connection (i.e. a connection created with local:// URI scheme).
 *
 * @author Jaikiran Pai
 */
public class LocalOutboundConnectionService extends AbstractOutboundConnectionService implements Service<LocalOutboundConnectionService> {

    public static final ServiceName LOCAL_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("local-outbound-connection");

    private final InjectedValue<OutboundSocketBinding> destinationOutboundSocketBindingInjectedValue = new InjectedValue<OutboundSocketBinding>();

    public LocalOutboundConnectionService(final String connectionName, final OptionMap connectionCreationOptions) {
        super();
    }

    Injector<OutboundSocketBinding> getDestinationOutboundSocketBindingInjector() {
        return this.destinationOutboundSocketBindingInjectedValue;
    }

    @Override
    public LocalOutboundConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public URI getDestinationUri() {
        return URI.create("local:-");
    }

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return AuthenticationConfiguration.EMPTY;
    }

    public SSLContext getSSLContext() {
        return null;
    }
}
