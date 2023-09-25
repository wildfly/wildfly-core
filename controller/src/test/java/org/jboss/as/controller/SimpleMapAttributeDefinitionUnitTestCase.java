/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test of {@link SimpleMapAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class SimpleMapAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException {

        SimpleMapAttributeDefinition ld = new SimpleMapAttributeDefinition.Builder("test", false)
                .setAllowExpression(true)
                .setValidator(new IntRangeValidator(1, false, true))
                .build();

        ModelNode op = new ModelNode();
        op.get("test", "int").set(2);
        op.get("test", "exp").set("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        Assert.assertEquals(op.get("test", "int"), validated.get("int"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "exp").asString())), validated.get("exp"));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(op.get("test", "int"), model.get("test", "int"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "exp").asString())), model.get("test", "exp"));
    }
}
