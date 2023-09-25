/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract superclass for {@link InterfaceCriteria} implementations.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractInterfaceCriteria implements InterfaceCriteria {

    private static final long serialVersionUID = -4266469792905191837L;

    /**
     * Gets whether the given network interface and address are acceptable for
     * use. Acceptance is indicated by returning the address which should be
     * used for binding against the network interface; typically this is the given {@code address}
     * parameter. For those criteria which override the configured address, the override address should
     * be returned.
     *
     * @param networkInterface the network interface. Cannot be <code>null</code>
     * @param address an address that is associated with <code>networkInterface</code>.
     * Cannot be <code>null</code>
     * @return <code>InetAddress</code> the non-null address to bind to if the
     * criteria is met, {@code null} if the criteria is not satisfied
     *
     * @throws SocketException if evaluating the state of {@code networkInterface} results in one
     */
    protected abstract InetAddress isAcceptable(NetworkInterface networkInterface, InetAddress address) throws SocketException;

    public Map<NetworkInterface, Set<InetAddress>> getAcceptableAddresses(final Map<NetworkInterface, Set<InetAddress>> candidates) throws SocketException {

        Map<NetworkInterface, Set<InetAddress>> result = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (Map.Entry<NetworkInterface, Set<InetAddress>> entry : candidates.entrySet()) {
            NetworkInterface ni = entry.getKey();
            HashSet<InetAddress> addresses = null;
            for (InetAddress address : entry.getValue()) {
                InetAddress accepted = isAcceptable(ni, address);
                if (accepted != null) {
                    if (addresses == null) {
                        addresses = new HashSet<InetAddress>();
                        result.put(ni, addresses);
                    }
                    addresses.add(accepted);
                }
            }
        }

        return result;
    }

    public static Map<NetworkInterface, Set<InetAddress>> cloneCandidates(final Map<NetworkInterface, Set<InetAddress>> candidates) {
        final Map<NetworkInterface, Set<InetAddress>> clone = new LinkedHashMap<NetworkInterface, Set<InetAddress>>();

        for (Map.Entry<NetworkInterface, Set<InetAddress>> entry : candidates.entrySet()) {
            clone.put(entry.getKey(), new LinkedHashSet<InetAddress>(entry.getValue()));
        }
        return clone;
    }

    @Override
    public int compareTo(InterfaceCriteria o) {
        if (this.equals(o)) {
            return 0;
        }
        return o instanceof InetAddressMatchInterfaceCriteria ? -1 : 1;
    }
}
