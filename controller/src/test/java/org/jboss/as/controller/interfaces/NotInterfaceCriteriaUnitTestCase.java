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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Unit tests of {@link NotInterfaceCriteria}
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NotInterfaceCriteriaUnitTestCase {

    @Test
    public void testMultipleCriteria() throws Exception {
        if (nonLoopBackInterfaces.size() < 1) {
            return;
        }


        if (nonLoopBackInterfaces.contains("bogus")) {
            // LOL  Oh well; we won't run this test on this machine :-D
            return;
        }

        Map<NetworkInterface, Set<InetAddress>> correct = new LinkedHashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : nonLoopBackInterfaces) {
            // WFLY-786 NotInterfaceCriteria doesn't prune based on IPv6/v4 preference
            // so we shouldn't test that it does. Pruning is done by OverallInterfaceCriteria
            //correct.put(ni, getRightTypeAddresses(allCandidates.get(ni)));
            correct.put(ni, allCandidates.get(ni));
        }

        Set<InterfaceCriteria> criterias = new HashSet<InterfaceCriteria>();
        criterias.add(new NicInterfaceCriteria("bogus"));
        criterias.add(LoopbackInterfaceCriteria.INSTANCE);
        NotInterfaceCriteria testee = new NotInterfaceCriteria(criterias);
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(allCandidates);
        assertNotNull(result);
        assertEquals(correct, result);
    }

    @Test
    public void testNoMatch() throws Exception {
        if (loopbackInterfaces.size() < 1) {
            return;
        }

        Map<NetworkInterface, Set<InetAddress>> candidates = new HashMap<NetworkInterface, Set<InetAddress>>();
        for (NetworkInterface ni : loopbackInterfaces) {
            candidates.put(ni, allCandidates.get(ni));
        }
        NotInterfaceCriteria testee = new NotInterfaceCriteria(Collections.singleton((InterfaceCriteria) LoopbackInterfaceCriteria.INSTANCE));
        Map<NetworkInterface, Set<InetAddress>> result = testee.getAcceptableAddresses(candidates);
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
