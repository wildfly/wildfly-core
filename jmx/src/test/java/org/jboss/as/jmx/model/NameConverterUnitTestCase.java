/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import org.jboss.as.controller.PathElement;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class NameConverterUnitTestCase {

    @Test
    public void testConvertName() {
        Assert.assertEquals("test", NameConverter.convertToCamelCase("test"));
    }

    @Test
    public void testConvertNameWithReplacement() {
        Assert.assertEquals("testOne", NameConverter.convertToCamelCase("test-one"));
    }

    @Test
    public void testCreateAddChildNameWildCard() {
        Assert.assertEquals("addTest", NameConverter.createValidAddOperationName(PathElement.pathElement("test")));
    }

    @Test
    public void testCreateAddChildNameNoWildCard() {
        Assert.assertEquals("addTestOne", NameConverter.createValidAddOperationName(PathElement.pathElement("test", "one")));
    }

    @Test
    public void testCreateAddChildNameWildCardWithReplacement() {
        Assert.assertEquals("addTestOne", NameConverter.createValidAddOperationName(PathElement.pathElement("test-one")));
    }

    @Test
    public void testCreateAddChildNameNoWildCardWithReplacement() {
        Assert.assertEquals("addTestOneTwoThree", NameConverter.createValidAddOperationName(PathElement.pathElement("test-one", "two-three")));
    }

}
