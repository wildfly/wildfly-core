/*
* JBoss, Home of Professional Open Source.
* Copyright 2015, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
