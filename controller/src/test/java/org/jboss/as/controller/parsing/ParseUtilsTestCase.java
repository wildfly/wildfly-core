/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.parsing;

import static org.junit.Assert.assertEquals;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Unit tests of {@link ParseUtils}.
 *
 * @author Joerg Baesner (c) 2020 Red Hat Inc.
 */
public class ParseUtilsTestCase {

    @Test
    public void testExpressionsWithTrailingWhitespace() {

        String propertyValue = "${key} ";
        ModelType attributeType = ModelType.STRING;

        ModelNode mn = ParseUtils.parseAttributeValue(propertyValue, true, attributeType);

        assertEquals("wrong ModelType,", ModelType.EXPRESSION, mn.getType());

        String parsedPropertyValue = mn.asExpression().getExpressionString();

        assertEquals("wrong parsedAttributeValue,", propertyValue, parsedPropertyValue);
    }
}