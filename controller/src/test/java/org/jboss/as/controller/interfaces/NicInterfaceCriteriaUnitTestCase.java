/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Unit test of {@link NicInterfaceCriteria}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NicInterfaceCriteriaUnitTestCase {

    @Test
    public void testBasic() throws Exception {

        if (allCandidates.size() < 1) {
            return;
        }

        for (Map.Entry<NetworkInterface, Set<InetAddress>> entry : allCandidates.entrySet()) {
            NetworkInterface nic = entry.getKey();
            String target = nic.getName();
            NicInterfaceCriteria testee = new NicInterfaceCriteria(target);
            Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
            assertEquals(1, result.size());
            Set<InetAddress> addresses = result.get(nic);
            assertNotNull(addresses);
            // WFLY-786 NicInterfaceCriteria doesn't prune based on IPv6/v4 preference
            // so we shouldn't test that it does. Pruning is done by OverallInterfaceCriteria
//            Set<InetAddress> rightType = getRightTypeAddresses(entry.getValue());
//            assertEquals(rightType, addresses);
//            assertTrue(addresses.containsAll(rightType));
        }
    }

    @Test
    public void testBogus() throws Exception {

        if (allCandidates.size() < 1) {
            return;
        }

        if (allCandidates.containsKey("bogus")) {
            // LOL  Oh well; we won't run this test on this machine :-D
            return;
        }

        NicInterfaceCriteria testee = new NicInterfaceCriteria("bogus");
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertEquals(0, result.size());
    }
}
