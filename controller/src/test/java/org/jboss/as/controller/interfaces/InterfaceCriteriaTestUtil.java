/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.OverallInterfaceCriteria.PREFER_IPV4_STACK;
import static org.jboss.as.controller.interfaces.OverallInterfaceCriteria.PREFER_IPV6_ADDRESSES;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for interface criteria testing.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class InterfaceCriteriaTestUtil {

    static final Set<NetworkInterface> loopbackInterfaces;
    static final Set<NetworkInterface> nonLoopBackInterfaces;
    static final Set<NetworkInterface> allInterfaces;
    static final Map<NetworkInterface, Set<InetAddress>> allCandidates;

    static {

        Set<NetworkInterface> loop = new HashSet<NetworkInterface>();
        Set<NetworkInterface> notLoop = new HashSet<NetworkInterface>();
        Set<NetworkInterface> all = new HashSet<NetworkInterface>();
        Map<NetworkInterface, Set<InetAddress>> candidates = new HashMap<NetworkInterface, Set<InetAddress>>();

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                all.add(nic);
                if (nic.isLoopback()) {
                    loop.add(nic);
                } else {
                    notLoop.add(nic);
                }
                Set<InetAddress> addresses = new HashSet<InetAddress>();
                Enumeration<InetAddress> addressEnumeration = nic.getInetAddresses();
                while (addressEnumeration.hasMoreElements()) {
                    InetAddress inetAddress = addressEnumeration.nextElement();
                    if (inetAddress != null) {
                        addresses.add(inetAddress);
                    }
                }
                if (addresses.size() > 0) {
                    candidates.put(nic, Collections.unmodifiableSet(addresses));
                }else{
                    notLoop.remove(nic);
                }

                Enumeration<NetworkInterface> subs = nic.getSubInterfaces();
                while (subs.hasMoreElements()) {
                    nic = subs.nextElement();
                    all.add(nic);
                    if (nic.isLoopback()) {
                        loop.add(nic);
                    } else {
                        notLoop.add(nic);
                    }
                    addresses = new HashSet<InetAddress>();
                    addressEnumeration = nic.getInetAddresses();
                    while (addressEnumeration.hasMoreElements()) {
                        addresses.add(addressEnumeration.nextElement());
                    }
                    if (addresses.size() > 0) {
                        candidates.put(nic, Collections.unmodifiableSet(addresses));
                    }else{
                        notLoop.remove(nic);
                    }

                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        loopbackInterfaces = Collections.unmodifiableSet(loop);
        nonLoopBackInterfaces = Collections.unmodifiableSet(notLoop);
        allInterfaces = Collections.unmodifiableSet(all);
        allCandidates = Collections.unmodifiableMap(candidates);
    }

    static Set<InetAddress> getRightTypeAddresses(Set<InetAddress> all) {
        //Local so we can override the value later
        final boolean preferIPv4Stack = Boolean.getBoolean(PREFER_IPV4_STACK);
        final boolean preferIPv6Stack = Boolean.getBoolean(PREFER_IPV6_ADDRESSES);

        Set<InetAddress> result = new HashSet<InetAddress>();
        for (InetAddress address : all) {

            if (preferIPv4Stack && !preferIPv6Stack && !(address instanceof Inet4Address)) {
                continue;
            } else if (preferIPv6Stack && !preferIPv4Stack && !(address instanceof Inet6Address)) {
                continue;
            } else if (!preferIPv6Stack && !preferIPv4Stack && address instanceof Inet6Address && InetAddress.getLoopbackAddress() instanceof Inet4Address) {
                //checking it does not support ipv6
                continue;
            }
            result.add(address);
        }
        return result;
    }
}
