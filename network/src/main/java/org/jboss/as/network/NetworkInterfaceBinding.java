/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collection;

/**
 * The resolved network interface bindings.
 *
 * @author Emanuel Muckenhuber
 */
public final class NetworkInterfaceBinding {

    private final InetAddress address;
    private final Collection<NetworkInterface> networkInterfaces;

    public NetworkInterfaceBinding(Collection<NetworkInterface> networkInterfaces, InetAddress address) {
        this.address = address;
        this.networkInterfaces = networkInterfaces;
    }

    /**
     * Get the network address.
     *
     * @return the network address
     */
    public InetAddress getAddress() {
        return this.address;
    }

    /**
     * Get the resolved network interfaces.
     *
     * @return the networkInterfaces
     */
    public Collection<NetworkInterface> getNetworkInterfaces() {
        return networkInterfaces;
    }

}
