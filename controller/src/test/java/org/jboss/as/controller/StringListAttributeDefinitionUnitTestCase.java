/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link StringListAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class StringListAttributeDefinitionUnitTestCase {
    static final StringListAttributeDefinition LIST_DEFINITION = new StringListAttributeDefinition.Builder("test")
            .setRequired(false)
            .setAllowExpression(true)
            .setElementValidator(new StringLengthValidator(1, false, true))
            .setDefaultValue(new ModelNode().add("element1").add("element2"))
            .build();


    @Test
    public void testExpressions() throws OperationFailedException {

        ModelNode op = new ModelNode();
        op.get("test").add("abc").add("${test:1}");

        ModelNode validated = LIST_DEFINITION.validateOperation(op);
        Assert.assertEquals(op.get("test").get(0), validated.get(0));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), validated.get(1));

        ModelNode model = new ModelNode();
        LIST_DEFINITION.validateAndSet(op, model);
        Assert.assertEquals(op.get("test").get(0), model.get("test").get(0));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), model.get("test").get(1));


    }

    @Test
    public void testDefaultValue() throws OperationFailedException {
        //it should include default value here
        Assert.assertEquals("Expected size is wrong", 2, LIST_DEFINITION.unwrap(ExpressionResolver.SIMPLE, new ModelNode()).size());
        ModelNode value = new ModelNode();
        value.get(LIST_DEFINITION.getName()).add("one");
        Assert.assertEquals("Expected size is wrong", 1, LIST_DEFINITION.unwrap(ExpressionResolver.SIMPLE, value).size());
    }


}
