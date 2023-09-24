/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.RESTART_ALL_SERVICES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ListValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link PrimitiveListAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class PrimitiveListAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException, XMLStreamException {

        PrimitiveListAttributeDefinition ld = PrimitiveListAttributeDefinition.Builder.of("test", ModelType.INT)
                .setAllowExpression(true)
                .setElementValidator(new IntRangeValidator(1, false, true))
                .build();

        ModelNode op = new ModelNode();
        op.get("test").add(2).add("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        assertEquals(op.get("test").get(0), validated.get(0));
        assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), validated.get(1));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        assertEquals(op.get("test").get(0), model.get("test").get(0));
        assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), model.get("test").get(1));
        ModelNode resolved = ld.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, model);
        Assert.assertTrue(resolved.toString(), resolved.isDefined());
        Assert.assertEquals(resolved.toString(), 2, resolved.get(0).asInt());
        Assert.assertEquals(resolved.toString(), 1, resolved.get(1).asInt());

        // Check an expression passed in that resolves to what a list would look like in the xml
        System.setProperty("WFCORE-3448", "2 1");
        op = new ModelNode();
        ld.getParser().parseAndSetParameter(ld, "${WFCORE-3448}", op, null);

        Assert.assertEquals(op.toString(), "${WFCORE-3448}", op.get("test").get(0).asExpression().getExpressionString());

        validated = ld.validateOperation(op);
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(0).asString())), validated.get(0));

        model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(0).asString())), model.get("test").get(0));

        resolved = ld.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, model);
        Assert.assertTrue(resolved.toString(), resolved.isDefined());
        Assert.assertEquals(resolved.toString(), 2, resolved.get(0).asInt());
        Assert.assertEquals(resolved.toString(), 1, resolved.get(1).asInt());

        ld = PrimitiveListAttributeDefinition.Builder.of("test", ModelType.PROPERTY)
                .setAllowExpression(true)
                .setElementValidator(new ModelTypeValidator(ModelType.PROPERTY, false, true))
                .build();

        op = new ModelNode();
        op.get("test").add("foo", 2).add("bar", "${test:1}");

        try {
            ld.validateOperation(op);
            fail("Did not reject " + op);
        } catch (IllegalStateException good) {
            //
        }

        try {
            ld.validateAndSet(op, new ModelNode());
            fail("Did not reject " + op);
        } catch (IllegalStateException good) {
            //
        }

    }

    @Test
    public void testBuilderCopyPreservesElementValidator() throws OperationFailedException {
        PrimitiveListAttributeDefinition original = PrimitiveListAttributeDefinition.Builder.of("test", ModelType.STRING)
                .setElementValidator(new StringLengthValidator(1))
                .build();

        // will use the same validator than original
        PrimitiveListAttributeDefinition copy = new PrimitiveListAttributeDefinition.Builder(original)
                // add a flag to distinguish the copy from the original
                .setFlags(RESTART_ALL_SERVICES)
                .build();

        // use a different validator than original & copy
        PrimitiveListAttributeDefinition copyWithOtherValidator = new PrimitiveListAttributeDefinition.Builder(original)
                // add a flag to distinguish the copy from the original
                .setFlags(RESTART_ALL_SERVICES)
                .setElementValidator(new StringLengthValidator(Integer.MAX_VALUE))
                .build();

        assertFalse(original.getFlags().contains(RESTART_ALL_SERVICES));
        assertTrue(copy.getFlags().contains(RESTART_ALL_SERVICES));
        assertTrue(copyWithOtherValidator.getFlags().contains(RESTART_ALL_SERVICES));

        assertSame(original.getElementValidator(), copy.getElementValidator());
        assertNotSame(original.getElementValidator(), copyWithOtherValidator.getElementValidator());

        ModelNode value = new ModelNode();
        value.add("foo");

        validateOperation(original, value, true);
        validateOperation(copy, value, true);
        validateOperation(copyWithOtherValidator, value, false);
    }

    @Test
    public void testBuilderCopyPreservesListValidator() throws OperationFailedException {

        // List can contain at most 2 items. The value of the item can not be > 256
        ParameterValidator ev = new IntRangeValidator(1, 256, false, false);
        ListValidator lv = new ListValidator(ev, false, 1, 2, true);
        PrimitiveListAttributeDefinition original = PrimitiveListAttributeDefinition.Builder.of("test", ModelType.STRING)
                .setListValidator(lv)
                .build();
        PrimitiveListAttributeDefinition copy = new PrimitiveListAttributeDefinition.Builder(original).build();

        ModelNode validValue = new ModelNode();
        validValue.add("1");
        validValue.add("64");

        // too many elements
        ModelNode invalidValue = new ModelNode();
        invalidValue.add("1");
        invalidValue.add("2");
        invalidValue.add("64");

        // 512 is not a valid element value
        ModelNode invalidValue2 = new ModelNode();
        invalidValue2.add("1");
        invalidValue2.add("512");

        // the original and copy attribute definition must validate the same way
        validateOperation(original, validValue, true);
        validateOperation(copy, validValue, true);

        validateOperation(original, invalidValue, false);
        validateOperation(copy, invalidValue, false);

        validateOperation(original, invalidValue2, false);
        validateOperation(copy, invalidValue2, false);
    }

    private void validateOperation(AttributeDefinition attributeDefinition, ModelNode value, boolean expectSuccess) throws OperationFailedException {
        ModelNode operation = new ModelNode();
        operation.get("test").set(value);

        if (expectSuccess) {
            attributeDefinition.validateOperation(operation);
        } else {
            try {
                attributeDefinition.validateOperation(operation);
                fail("operation must fail with invalid value " + value);
            } catch (OperationFailedException e) {
            }
        }
    }
}
