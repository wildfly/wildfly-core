/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ObjectTypeAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ObjectTypeAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException {

        AttributeDefinition a = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
        AttributeDefinition b = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();

        ObjectTypeAttributeDefinition ld = new ObjectTypeAttributeDefinition.Builder("test", a, b)
                .setAllowExpression(true)
                .build();

        ModelNode op = new ModelNode();
        op.get("test", "a").set(2);
        op.get("test", "b").set("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        Assert.assertEquals(op.get("test", "a"), validated.get("a"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "b").asString())), validated.get("b"));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(op.get("test", "a"), model.get("test", "a"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test", "b").asString())), model.get("test", "b"));

        op = new ModelNode();
        op.get("test", "a").set("${test:1}");
        op.get("test", "b").set(true);

        try {
            ld.validateOperation(op);
            org.junit.Assert.fail("Did not reject " + op);
        } catch (OperationFailedException good) {
            //
        }

        try {
            ld.validateAndSet(op, new ModelNode());
            org.junit.Assert.fail("Did not reject " + op);
        } catch (OperationFailedException good) {
            //
        }
    }

    /** WFCORE-2249 */
    @Test
    public void testMissingFields() throws OperationFailedException {
        AttributeDefinition a = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).setRequired(true).build();
        AttributeDefinition b = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setRequired(false).build();

        ObjectTypeAttributeDefinition ld = new ObjectTypeAttributeDefinition.Builder("test", a, b)
                .setAllowExpression(true)
                .build();

        ModelNode op = new ModelNode();
        op.get("test", "b").set(true);

        try {
            ld.validateOperation(op);
            org.junit.Assert.fail("Did not reject " + op);
        } catch (OperationFailedException good) {
            //
        }

        op = new ModelNode();
        op.get("test", "a").set(1);

        ModelNode validated = ld.validateOperation(op);
        org.junit.Assert.assertEquals(validated.toString(), 1, validated.get("a").asInt());
    }

    /** WFCORE-3255 */
    @Test
    public void testParameterCorrector() throws OperationFailedException {
        final AttributeDefinition aBoolean = SimpleAttributeDefinitionBuilder.create("a-boolean", ModelType.BOOLEAN)
            .setCorrector(new FalseTrueCorrector())
            .build();
        final AttributeDefinition anInt = SimpleAttributeDefinitionBuilder.create("an-int", ModelType.INT)
                .setRequired(false)
                .build();
        final AttributeDefinition anObject = ObjectTypeAttributeDefinition.Builder.of("an-object", aBoolean, anInt)
                .setCorrector(new ObjectCorrectionCorrector())
                .build();
        final AttributeDefinition aNestedObject = ObjectTypeAttributeDefinition.Builder.of("a-nested-object", aBoolean, anInt, anObject).build();

        ModelNode testOperation = new ModelNode();
        testOperation.get("an-object", "a-boolean").set(false);

        ModelNode aModel = new ModelNode();
        anObject.validateAndSet(testOperation, aModel);

        // Field must be corrected
        assertTrue(aModel.toString(), aModel.get("an-object", "a-boolean").asBoolean());
        // Overall object corrector must execute as well
        assertEquals(aModel.toString(), 5, aModel.get("an-object", "an-int").asInt());

        // Now nest the object in another one to confirm that works too
        testOperation = new ModelNode();
        testOperation.get("a-nested-object","an-object", "a-boolean").set(false);
        testOperation.get("a-nested-object", "a-boolean").set(false);


        aModel = new ModelNode();

        aNestedObject.validateAndSet(testOperation, aModel);

        assertTrue(aModel.toString(), aModel.get("a-nested-object", "a-boolean").asBoolean());
        assertTrue(aModel.toString(), aModel.get("a-nested-object", "an-object", "a-boolean").asBoolean());
        assertEquals(aModel.toString(), 5, aModel.get("a-nested-object", "an-object", "an-int").asInt());
    }

    /** Corrector for a field in an object */
    private static class FalseTrueCorrector implements ParameterCorrector {

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            newValue.set(true);
            return newValue;
        }
    }

    /** Corrector for the overall object */
    private static class ObjectCorrectionCorrector implements ParameterCorrector {

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            newValue.get("an-int").set(5);
            return newValue;
        }
    }
}
