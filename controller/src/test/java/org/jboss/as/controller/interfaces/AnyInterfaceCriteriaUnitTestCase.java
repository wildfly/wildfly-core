/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;

import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.allCandidates;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.loopbackInterfaces;
import static org.jboss.as.controller.interfaces.InterfaceCriteriaTestUtil.nonLoopBackInterfaces;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Unit tests of {@link AnyInterfaceCriteria}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class AnyInterfaceCriteriaUnitTestCase {

    @Test
    public void testMultipleCriteria() throws Exception {

        if (nonLoopBackInterfaces.size() < 1 || loopbackInterfaces.size() < 1) {
            return;
        }

        Map<NetworkInterface, Set<InetAddress>> correct = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : loopbackInterfaces) {
            // WFLY-786 AnyInterfaceCriteria doesn't prune based on IPv6/v4 preference
            // so we shouldn't test that it does. Pruning is done by OverallInterfaceCriteria
            //correct.put(ni, getRightTypeAddresses(allCandidates.get(ni)));
            correct.put(ni, allCandidates.get(ni));
        }
        String target = null;
        for (NetworkInterface ni : nonLoopBackInterfaces) {
            // WFLY-786 AnyInterfaceCriteria doesn't prune based on IPv6/v4 preference
            // so we shouldn't test that it does. Pruning is done by OverallInterfaceCriteria
            //Set<InetAddress> addresses = getRightTypeAddresses(allCandidates.get(ni));
            Set<InetAddress> addresses = allCandidates.get(ni);
            if (addresses.size() > 0) {
                correct.put(ni, addresses);
                target = ni.getName();
                break;
            }
        }

        if (target == null) {
            return;
        }

        Set<InterfaceCriteria> criterias = new HashSet<InterfaceCriteria>();
        criterias.add(new NicInterfaceCriteria(target));
        criterias.add(LoopbackInterfaceCriteria.INSTANCE);
        AnyInterfaceCriteria testee = new AnyInterfaceCriteria(criterias);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(correct, result);
    }

    @Test
    public void testNoMatch() throws Exception {

        if (allCandidates.size() < 1) {
            return;
        }

        if (allCandidates.containsKey("bogus")) {
            // LOL  Oh well; we won't run this test on this machine :-D
            return;
        }

        AnyInterfaceCriteria testee = new AnyInterfaceCriteria(Collections.singleton((InterfaceCriteria) new NicInterfaceCriteria("bogus")));
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

}
