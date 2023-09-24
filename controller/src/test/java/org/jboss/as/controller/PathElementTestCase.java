/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 *
 * @author <a href="philippe.marschall@gmail.com">Philippe Marschall</a>
 */
public class PathElementTestCase {

    @Test
    public void validKey() {
        String[] validKeys = {
                "*",
                "a",
                "z",
                "A",
                "Z",
                "_",
                "a-a",
                "a-1",
                "a0",
                "a1",
                "a9",
                "_1",
                "_a",
                "__",
        };
        for (String key : validKeys) {
            PathElement pathElement = PathElement.pathElement(key);
            assertEquals(key, pathElement.getKey());
        }
    }

    @Test
    public void invalidkey() {
        String[] invalidKeys = {
                null,
                "",
                "-",
                "1",
                "a-",
                "-a"
        };
            for (String key : invalidKeys) {
                try {
                    PathElement.pathElement(key);
                    fail("key " + key + " should be invalid");
                } catch (IllegalArgumentException e) {
                    // should reach here
                }

            }
    }

    @Test
    public void invalidValue() {
        String[] invalidValues = {
            null,
            ""
        };
        for (String value : invalidValues) {
            try {
                PathElement.pathElement("key", value);
                fail("value " + value + " should be invalid");
            } catch (IllegalArgumentException e) {
                // should reach here
            }
        }
    }
}
