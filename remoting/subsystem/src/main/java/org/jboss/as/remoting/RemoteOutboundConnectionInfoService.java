/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.xnio.OptionMap;

/**
 * An MSC service that computes the outbound connection URI from the socket binding
 * and holds the connection creation options.
 */
final class RemoteOutboundConnectionInfoService implements Service, ConnectionInfo {

    private final Consumer<ConnectionInfo> serviceConsumer;
    private final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier;

    private final OptionMap connectionCreationOptions;
    private final String username;
    private final String protocol;

    private volatile URI destination;

    RemoteOutboundConnectionInfoService(
            final Consumer<ConnectionInfo> serviceConsumer,
            final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier,
            final OptionMap connectionCreationOptions, final String username, final String protocol) {
        this.serviceConsumer = serviceConsumer;
        this.outboundSocketBindingSupplier = outboundSocketBindingSupplier;
        this.connectionCreationOptions = connectionCreationOptions;
        this.username = username;
        this.protocol = protocol;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        final OutboundSocketBinding binding = outboundSocketBindingSupplier.get();
        final String hostName = NetworkUtils.formatPossibleIpv6Address(binding.getUnresolvedDestinationAddress());
        final int port = binding.getDestinationPort();
        try {
            this.destination = new URI(protocol, username, hostName, port, null, null, null);
        } catch (URISyntaxException e) {
            throw new StartException(e);
        }
        this.serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
        destination = null;
    }

    public URI getDestinationUri() {
        return destination;
    }

    public String getUsername() {
        return username;
    }

    public OptionMap getConnectionCreationOptions() {
        return connectionCreationOptions;
    }
}
