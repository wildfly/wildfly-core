/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonSecurityPropertiesTestCase {

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
