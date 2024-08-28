/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.OverallInterfaceCriteria.PREFER_IPV4_STACK;
import static org.jboss.as.controller.interfaces.OverallInterfaceCriteria.PREFER_IPV6_ADDRESSES;

import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.allCandidates;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.allInterfaces;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.getRightTypeAddresses;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.loopbackInterfaces;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.nonLoopBackInterfaces;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assume;
import org.junit.Test;

/**
 * Unit tests of {@link OverallInterfaceCriteria}
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OverallInterfaceCriteriaUnitTestCase {

    @Test
    public void testBasic() throws Exception {

        Assume.assumeFalse(allCandidates.size() < 1);

        Map.Entry<NetworkInterface, Set<InetAddress>> correct = allCandidates.entrySet().iterator().next();

        InterfaceCriteria criteria = new NicInterfaceCriteria(correct.getKey().getName());
        OverallInterfaceCriteria testee = new OverallInterfaceCriteria("test", Collections.singleton(criteria));
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(1, result.size());

        Map.Entry<NetworkInterface, Set<InetAddress>> entry = result.entrySet().iterator().next();
        assertEquals(correct.getKey(), entry.getKey());
        assertEquals(1, entry.getValue().size());
        Set<InetAddress> set = correct.getValue();
        assertTrue(set.contains(entry.getValue().iterator().next()));
    }

    @Test
    public void testMultipleCriteria() throws Exception {

        Assume.assumeFalse(nonLoopBackInterfaces.size() < 1 || loopbackInterfaces.size() < 1);

        Map<NetworkInterface, Set<InetAddress>> correct = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : loopbackInterfaces) {
            if (ni.isUp() && allCandidates.containsKey(ni)) {
                correct.put(ni, getRightTypeAddresses(allCandidates.get(ni)));
            }
        }

        Assume.assumeFalse(correct.size() == 0);

        Set<InterfaceCriteria> criterias = new HashSet<InterfaceCriteria>();
        criterias.add(UpInterfaceCriteria.INSTANCE);
        criterias.add(LoopbackInterfaceCriteria.INSTANCE);
        OverallInterfaceCriteria testee = new OverallInterfaceCriteria("test", criterias);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(1, result.size());

        Map.Entry<NetworkInterface, Set<InetAddress>> entry = result.entrySet().iterator().next();
        assertEquals(1, entry.getValue().size());
        Set<InetAddress> set = correct.get(entry.getKey());
        assertNotNull(set);
        assertTrue(set.contains(entry.getValue().iterator().next()));
    }

    @Test
    public void testMultipleMatches() throws Exception {

        Assume.assumeFalse(allCandidates.size() < 1);

        Map<NetworkInterface, Set<InetAddress>> correct = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : allInterfaces) {
            if (ni.isUp()) {
                correct.put(ni, getRightTypeAddresses(allCandidates.get(ni)));
            }
        }

        Assume.assumeFalse(correct.size() < 2);

        OverallInterfaceCriteria testee = new OverallInterfaceCriteria("test", Collections.singleton(UpInterfaceCriteria.INSTANCE));
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(1, result.size());

        Map.Entry<NetworkInterface, Set<InetAddress>> entry = result.entrySet().iterator().next();
        assertEquals(1, entry.getValue().size());
        Set<InetAddress> set = correct.get(entry.getKey());
        assertNotNull(set);
        assertTrue(set.contains(entry.getValue().iterator().next()));
    }

    @Test
    public void testNoMatch() throws Exception {

        Assume.assumeFalse(loopbackInterfaces.size() < 1);

        for (NetworkInterface nic : allCandidates.keySet()) {
            Assume.assumeFalse("bogus".equals(nic.getName()));
        }


        Set<InterfaceCriteria> criterias = new LinkedHashSet<InterfaceCriteria>();
        criterias.add(LoopbackInterfaceCriteria.INSTANCE);
        criterias.add(new NicInterfaceCriteria("bogus"));
        OverallInterfaceCriteria testee = new OverallInterfaceCriteria("test", criterias);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    /** WFCORE-2626 */
    @Test
    public void testInetAddressDuplicates() throws Exception {
        Assume.assumeFalse(loopbackInterfaces.size() < 1);
        Assume.assumeFalse(nonLoopBackInterfaces.size() < 1);

        // Build up a fake candidate set where the same addresses appear associated with 2 NICs, one up, one down
        // This simulates an environment with multiple NICs with the same address configured

        NetworkInterface down = null;
        NetworkInterface up = null;
        Set<InetAddress> addresses = null;
        Iterator<NetworkInterface> iter = allCandidates.keySet().iterator();
        while ((down == null || up == null) && iter.hasNext()) {
            NetworkInterface nic = iter.next();
            if (down == null && !nic.isUp()) {
                down = nic;
            } else if (addresses == null && nic.isUp()) {
                Set<InetAddress> nicAddresses = allCandidates.get(nic);
                if (nicAddresses.size() > 0) {
                    addresses = nicAddresses;
                    up = nic;
                }
            }
        }

        Assume.assumeNotNull(down, up); // this will often fail, and this test is ignored
        assert addresses != null;

        Map<NetworkInterface, Set<InetAddress>> map = new HashMap<>();
        map.put(up, addresses);
        map.put(down, addresses);

        // Validate that the 'up' requirement prevents InetAddressMatchInterfaceCriteria rejecting the 'duplicate' InetAddress
        InetAddress toMatch = addresses.iterator().next();

        Set<InterfaceCriteria> criterias = new LinkedHashSet<InterfaceCriteria>();
        criterias.add(UpInterfaceCriteria.INSTANCE);
        criterias.add(new InetAddressMatchInterfaceCriteria(toMatch));
        OverallInterfaceCriteria testee = new OverallInterfaceCriteria("test", criterias);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(map);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(up));
        assertEquals(Collections.singleton(toMatch), result.get(up));

        // Now reverse the order to show it doesn't matter
        criterias = new LinkedHashSet<InterfaceCriteria>();
        criterias.add(new InetAddressMatchInterfaceCriteria(toMatch));
        criterias.add(UpInterfaceCriteria.INSTANCE);
        testee = new OverallInterfaceCriteria("test", criterias);
        result = testee.getAcceptableAddresses(map);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(up));
        assertEquals(Collections.singleton(toMatch), result.get(up));
    }

    @Test
    public void testMultipleMatchesNoIPVPreference() throws Exception {

        boolean preferIPv4Stack = Boolean.getBoolean(PREFER_IPV4_STACK);
        boolean preferIPv6Stack = Boolean.getBoolean(PREFER_IPV6_ADDRESSES);

        System.setProperty(PREFER_IPV4_STACK, "false");
        System.setProperty(PREFER_IPV6_ADDRESSES, "false");

        testMultipleMatches();

        System.setProperty(PREFER_IPV4_STACK, Boolean.toString(preferIPv4Stack));
        System.setProperty(PREFER_IPV6_ADDRESSES, Boolean.toString(preferIPv6Stack));
    }
}
