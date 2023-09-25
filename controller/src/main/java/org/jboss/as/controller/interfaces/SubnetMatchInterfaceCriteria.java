/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 *
 */
package org.jboss.as.controller.interfaces;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;

import org.wildfly.common.Assert;

/**
 * {@link InterfaceCriteria} that tests whether a given address is on the
 * desired subnet.
 *
 * @author Brian Stansberry
 */
public class SubnetMatchInterfaceCriteria extends AbstractInterfaceCriteria {


    private static final long serialVersionUID = 149404752878332750L;

    private byte[] network;
    private int mask;

    /**
     * Creates a new SubnetMatchInterfaceCriteria
     *
     * @param network an InetAddress in byte[] form.
     *                 Cannot be <code>null</code>
     * @param mask the number of bits in <code>network</code> that represent
     *             the network
     *
     * @throws IllegalArgumentException if <code>network</code> is <code>null</code>
     */
    public SubnetMatchInterfaceCriteria(byte[] network, int mask) {
        Assert.checkNotNullParam("network", network);
        this.network = network;
        this.mask = mask;
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>address</code> if the <code>address</code> is on the correct subnet.
     */
    @Override
    protected InetAddress isAcceptable(NetworkInterface networkInterface, InetAddress address) throws SocketException {
        return verifyAddressByMask(address.getAddress()) ? address : null;
    }

    boolean verifyAddressByMask(byte[] addr) {
        if (addr.length != network.length) {
            // different address type
            return false;
        }

        int max = network.length * 8;
        int maskOffset = 0;
        int bitMask = 0xFF;
        while (maskOffset < mask && maskOffset < max) {
            if ((mask - maskOffset) < 8)
                bitMask = 0xFF << (8 - (mask - maskOffset));

            if ((addr[maskOffset / 8] & bitMask) != (network[maskOffset / 8] & bitMask))
                return false;

            maskOffset += 8;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int i = 17;
        i = 31 * i + mask;
        i = 31 * i + Arrays.hashCode(network);
        return i;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof SubnetMatchInterfaceCriteria)
                && Arrays.equals(network, ((SubnetMatchInterfaceCriteria)o).network)
                && mask == ((SubnetMatchInterfaceCriteria)o).mask;
    }
}
