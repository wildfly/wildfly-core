/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.*;
import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Unit tests of {@link InetAddressMatchInterfaceCriteria}
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class InetAddressMatchInterfaceCriteriaTestCase {

    @Test
    public void testBasicMatch() throws Exception {
        if (allCandidates.size() < 1) {
            return;
        }

        for (Map.Entry<NetworkInterface, Set<InetAddress>> entry : allCandidates.entrySet()) {
            InetAddress target = entry.getValue().iterator().next();
            InterfaceCriteria criteria = new InetAddressMatchInterfaceCriteria(target);
            Map<NetworkInterface, Set<InetAddress>> accepted = criteria.getAcceptableAddresses(allCandidates);
            assertNotNull(accepted);
            accepted = OverallInterfaceCriteria.pruneAliasDuplicates(accepted);
            assertEquals(1, accepted.size());
            Set<InetAddress> set = accepted.get(entry.getKey());
            if (set == null) {
                Enumeration<NetworkInterface> subs = entry.getKey().getSubInterfaces();
                while (subs.hasMoreElements()) {
                    set = accepted.get(subs.nextElement());
                    if (set != null) {
                        break;
                    }
                }
            }
            assertNotNull(set);
            assertEquals(1, set.size());
            assertTrue(set.contains(target));
        }
    }

    @Test
    public void testAmbiguousScopeId() throws Exception {

        if (allInterfaces.size() < 2) {
            return;
        }

        Map<NetworkInterface, Set<InetAddress>> candidates = new HashMap<NetworkInterface, Set<InetAddress>>();
        int i = 1;
        for (Iterator<NetworkInterface> iter = allInterfaces.iterator(); iter.hasNext(); i++) {
            Set<InetAddress> set = Collections.unmodifiableSet(Collections.singleton(InetAddress.getByName("::1%" + i)));
            candidates.put(iter.next(), set);
        }

        String ambiguous = "::1";
        InterfaceCriteria criteria = new InetAddressMatchInterfaceCriteria(ambiguous);
        Map<NetworkInterface, Set<InetAddress>> accepted = criteria.getAcceptableAddresses(candidates);
        assertNotNull(accepted);
        assertEquals(0, accepted.size());
    }
}
