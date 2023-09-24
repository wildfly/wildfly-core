/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.net.URI;

import javax.net.ssl.SSLContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * A {@link LocalOutboundConnectionService} manages a local remoting connection (i.e. a connection created with local:// URI scheme).
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class LocalOutboundConnectionService extends AbstractOutboundConnectionService implements Service<LocalOutboundConnectionService> {

    static final ServiceName LOCAL_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("local-outbound-connection");

    private final Consumer<LocalOutboundConnectionService> serviceConsumer;
    private final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier;

    LocalOutboundConnectionService(final Consumer<LocalOutboundConnectionService> serviceConsumer, final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier) {
        this.serviceConsumer = serviceConsumer;
        this.outboundSocketBindingSupplier = outboundSocketBindingSupplier;
    }

    @Override
    public void start(final StartContext context) {
        serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
    }

    @Override
    public LocalOutboundConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public URI getDestinationUri() {
        return URI.create("local:-");
    }

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return AuthenticationConfiguration.empty();
    }

    public SSLContext getSSLContext() {
        return null;
    }

}
