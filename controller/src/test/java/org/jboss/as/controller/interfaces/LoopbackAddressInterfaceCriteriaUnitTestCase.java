/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.*;
import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Unit test of {@link LoopbackAddressInterfaceCriteria}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class LoopbackAddressInterfaceCriteriaUnitTestCase {

    @Test
    public void testBasic() throws Exception {

        if (allCandidates.size() < 1) {
            return;
        }

        InetAddress target = InetAddress.getByName("127.0.0.2");
        LoopbackAddressInterfaceCriteria testee = new LoopbackAddressInterfaceCriteria(target);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertEquals(loopbackInterfaces.size(), result.size());
        if (result.size() > 0) {
            for (Set<InetAddress> set : result.values()) {
                assertEquals(1, set.size());
                assertTrue(set.contains(target));
            }
        }
    }

    @Test
    public void testNoLoopback() throws Exception {

        if (nonLoopBackInterfaces.size() < 1) {
            return;
        }

        InetAddress target = InetAddress.getByName("127.0.0.2");
        LoopbackAddressInterfaceCriteria testee = new LoopbackAddressInterfaceCriteria(target);
        Map<NetworkInterface, Set<InetAddress>> candidates = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : nonLoopBackInterfaces) {
            candidates.put(ni, allCandidates.get(ni));
        }
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(candidates);
        assertEquals(0, result.size());
    }
}
