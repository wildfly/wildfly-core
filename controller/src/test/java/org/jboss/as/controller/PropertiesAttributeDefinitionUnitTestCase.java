/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link PropertiesAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class PropertiesAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException {
        ModelNode defaultValue = new ModelNode();
        defaultValue.add("key","value");
        defaultValue.add("key2","value");
        PropertiesAttributeDefinition ld = new PropertiesAttributeDefinition.Builder("test", true)
                .setAllowExpression(true)
                .setDefaultValue(defaultValue)
                .build();

        ModelNode op = new ModelNode();
        op.get("test", "int").set("int");
        op.get("test", "exp").set("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        Assert.assertEquals(op.get("test", "int"), validated.get("int"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "exp").asString())), validated.get("exp"));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(op.get("test", "int"), model.get("test", "int"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "exp").asString())), model.get("test", "exp"));


        //it should include default value here
        Assert.assertEquals("Expected size is wrong", 2, ld.unwrap(ExpressionResolver.SIMPLE, new ModelNode()).size());
        ModelNode value = new ModelNode();
        value.get(ld.getName()).add("one", "value");
        Assert.assertEquals("Expected size is wrong", 1, ld.unwrap(ExpressionResolver.SIMPLE, value).size());

    }
}
