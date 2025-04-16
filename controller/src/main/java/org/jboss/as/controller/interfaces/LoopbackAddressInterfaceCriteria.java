/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.wildfly.common.Assert;

/**
 * A loopback criteria with a specified bind address.
 *
 * @author Scott stark (sstark@redhat.com) (C) 2011 Red Hat Inc.
 * @version $Revision:$
 */
public class LoopbackAddressInterfaceCriteria extends AbstractInterfaceCriteria {

    private static final long serialVersionUID = 1L;

    private final String address;
    private InetAddress resolved;
    private boolean unknownHostLogged;

    /**
     * Creates a new LoopbackAddressInterfaceCriteria
     *
     * @param address a valid value to pass to {@link InetAddress#getByName(String)}
     *                Cannot be {@code null}
     *
     * @throws IllegalArgumentException if <code>network</code> is <code>null</code>
     */
    public LoopbackAddressInterfaceCriteria(final InetAddress address) {
        Assert.checkNotNullParam("address", address);
        this.resolved = address;
        this.address = resolved.getHostAddress();
    }

    /**
     * Creates a new LoopbackAddressInterfaceCriteria
     *
     * @param address a valid value to pass to {@link InetAddress#getByName(String)}
     *                Cannot be {@code null}
     *
     * @throws IllegalArgumentException if <code>network</code> is <code>null</code>
     */
    public LoopbackAddressInterfaceCriteria(final String address) {
        Assert.checkNotNullParam("address", address);
        this.address = address;
    }

    public synchronized InetAddress getAddress() throws UnknownHostException {
        if (resolved == null) {
            resolved = InetAddress.getByName(address);
        }
        return this.resolved;
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>{@link #getAddress()}()</code> if {@link NetworkInterface#isLoopback()} is true, null otherwise.
     */
    @Override
    protected InetAddress isAcceptable(NetworkInterface networkInterface, InetAddress address) {
        try {
            if (networkInterface.isLoopback()) {
                return getAddress();
            }
        } catch (SocketException ex) {
            MGMT_OP_LOGGER.errorInspectingNetworkInterface(ex, networkInterface);
        } catch (UnknownHostException e) {
            // One time only log a warning
            if (!unknownHostLogged) {
                MGMT_OP_LOGGER.cannotResolveAddress(this.address);
                unknownHostLogged = true;
            }
        }
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("LoopbackAddressInterfaceCriteria(");
        sb.append("address=");
        sb.append(address);
        sb.append(",resolved=");
        sb.append(resolved);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof LoopbackAddressInterfaceCriteria)
                && address.equals(((LoopbackAddressInterfaceCriteria)o).address);
    }
}
