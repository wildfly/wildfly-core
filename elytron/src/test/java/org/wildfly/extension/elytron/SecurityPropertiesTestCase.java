/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 */
public class SecurityPropertiesTestCase {

    @Test
    public void testPropertyDiff(){
        Map<String, String> newMap = new HashMap<>();
        Map<String, String> oldMap = new HashMap<>();

        Map<String, String> updated = new HashMap<>();
        Map<String, String> added = new HashMap<>();
        Map<String, String> removed = new HashMap<>();

        newMap.put("key1","value");
        newMap.put("key2","value");
        newMap.put("key3","value");

        SecurityPropertiesWriteHandler.doDifference(newMap, oldMap, added, removed, updated );
        Assert.assertEquals(0, updated.size());
        Assert.assertEquals(3, added.size());
        Assert.assertEquals(0, removed.size());

        oldMap.put("key1","value2");

        updated.clear();
        removed.clear();
        added.clear();

        SecurityPropertiesWriteHandler.doDifference(newMap, oldMap, added, removed, updated);
        Assert.assertEquals(1, updated.size());
        Assert.assertEquals(2, added.size());
        Assert.assertEquals(0, removed.size());


        oldMap.put("key1", "value1");
        oldMap.put("key2", "value2");
        oldMap.put("key3", "value3");

        updated.clear();
        removed.clear();
        added.clear();

        SecurityPropertiesWriteHandler.doDifference(newMap, oldMap, added, removed, updated);
        Assert.assertEquals(3, updated.size());
        Assert.assertEquals(0, added.size());
        Assert.assertEquals(0, removed.size());


        newMap.clear();
        newMap.put("key1", "value");
        newMap.put("key2", "value");
        newMap.put("key4", "value");

        oldMap.clear();
        oldMap.put("key1", "value1");
        oldMap.put("key2", "value2");
        oldMap.put("key3", "value3");

        updated.clear();
        removed.clear();
        added.clear();

        SecurityPropertiesWriteHandler.doDifference(newMap, oldMap, added, removed, updated);
        Assert.assertEquals(2, updated.size());
        Assert.assertEquals(1, added.size());
        Assert.assertEquals(1, removed.size());

    }
}
