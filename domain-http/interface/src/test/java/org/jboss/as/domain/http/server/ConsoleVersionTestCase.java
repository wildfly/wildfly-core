/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ConsoleVersionTestCase {

    @Test
    public void testSortConsoleVersions() {
        ConsoleVersion versionMain = new ConsoleVersion("main");
        ConsoleVersion version001 = new ConsoleVersion("0.0.1");
        ConsoleVersion version002 = new ConsoleVersion("0.0.2");
        ConsoleVersion version010 = new ConsoleVersion("0.1.0");
        ConsoleVersion version020 = new ConsoleVersion("0.2.0");
        ConsoleVersion version100 = new ConsoleVersion("1.0.0");
        ConsoleVersion version101 = new ConsoleVersion("1.0.1");
        ConsoleVersion version102 = new ConsoleVersion("1.0.2");
        ConsoleVersion version110 = new ConsoleVersion("1.1.0");
        ConsoleVersion version111 = new ConsoleVersion("1.1.1");
        ConsoleVersion version120 = new ConsoleVersion("1.2.0");
        ConsoleVersion version122 = new ConsoleVersion("1.2.2");
        ConsoleVersion version200 = new ConsoleVersion("2.0.0");
        ConsoleVersion version201 = new ConsoleVersion("2.0.1");
        ConsoleVersion version210 = new ConsoleVersion("2.1.0");

        TreeSet<ConsoleVersion> set = new TreeSet<ConsoleVersion>();
        set.add(versionMain);
        set.add(version001);
        set.add(version002);
        set.add(version010);
        set.add(version020);
        set.add(version100);
        set.add(version101);
        set.add(version102);
        set.add(version110);
        set.add(version111);
        set.add(version120);
        set.add(version122);
        set.add(version200);
        set.add(version201);
        set.add(version210);

        Iterator<ConsoleVersion> it = set.iterator();
        Assert.assertEquals(version210, it.next());
        Assert.assertEquals(version201, it.next());
        Assert.assertEquals(version200, it.next());
        Assert.assertEquals(version122, it.next());
        Assert.assertEquals(version120, it.next());
        Assert.assertEquals(version111, it.next());
        Assert.assertEquals(version110, it.next());
        Assert.assertEquals(version102, it.next());
        Assert.assertEquals(version101, it.next());
        Assert.assertEquals(version100, it.next());
        Assert.assertEquals(version020, it.next());
        Assert.assertEquals(version010, it.next());
        Assert.assertEquals(version002, it.next());
        Assert.assertEquals(version001, it.next());
        Assert.assertEquals(versionMain, it.next());
        Assert.assertFalse(it.hasNext());

        ArrayList<ConsoleVersion> list = new ArrayList<ConsoleVersion>(set);
        for (int i = 1 ; i < list.size() - 1; i++) {
            final ConsoleVersion current = list.get(i);
            for (int j = 0 ; j < i ;  j++) {
                final ConsoleVersion higher = list.get(j);
                assertTrue(higher.compareTo(current) < 0);
                assertTrue(current.compareTo(higher) > 0);
            }

            for (int j = i + 1 ; j < list.size() ; j++) {
                final ConsoleVersion lower = list.get(j);
                assertTrue(current.compareTo(lower) < 0);
                assertTrue(lower.compareTo(current) > 0);
            }
        }
    }
}
