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
 * Unit tests of {@link ObjectListAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ObjectListAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException {

        AttributeDefinition a = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
        AttributeDefinition b = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();

        ObjectTypeAttributeDefinition otad = new ObjectTypeAttributeDefinition.Builder("", a, b)
                .setAllowExpression(true)
                .build();

        ObjectListAttributeDefinition ld = ObjectListAttributeDefinition.Builder.of("test", otad).build();

        ModelNode op = new ModelNode();
        ModelNode one = op.get("test").add();
        one.get("a").set(2);
        one.get("b").set(true);
        ModelNode two = op.get("test").add();
        two.get("a").set(2);
        two.get("b").set("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        Assert.assertEquals(op.get("test").get(0), validated.get(0));
        Assert.assertEquals(op.get("test").get(1).get("a"), validated.get(1).get("a"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).get("b").asString())), validated.get(1).get("b"));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(op.get("test").get(0), model.get("test").get(0));
        Assert.assertEquals(op.get("test").get(1).get("a"), model.get("test").get(1).get("a"));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).get("b").asString())), model.get("test").get(1).get("b"));

        op = new ModelNode();
        one = op.get("test").add();
        one.get("a").set(2);
        one.get("b").set(true);
        two = op.get("test").add();
        two.get("a").set("${test:1}");
        two.get("b").set(false);

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

    /**
     * WFCORE-3255
     */
    @Test
    public void testParameterCorrector() throws OperationFailedException {
        final AttributeDefinition aBoolean = SimpleAttributeDefinitionBuilder.create("a-boolean", ModelType.BOOLEAN)
                .setCorrector(new FalseTrueCorrector())
                .build();
        final AttributeDefinition anInt = SimpleAttributeDefinitionBuilder.create("an-int", ModelType.INT)
                .setRequired(false)
                .build();
        final ObjectTypeAttributeDefinition anObject = ObjectTypeAttributeDefinition.Builder.of("an-object", aBoolean, anInt)
                .setCorrector(new ObjectCorrectionCorrector())
                .build();
        final AttributeDefinition anObjectList = ObjectListAttributeDefinition.Builder.of("an-object-list", anObject)
                .setCorrector(new ListDuplicationCorrector())
                .build();

        ModelNode testOperation = new ModelNode();
        ModelNode testList = testOperation.get("an-object-list");
        testList.add().get("a-boolean").set(false);

        ModelNode aModel = new ModelNode();
        anObjectList.validateAndSet(testOperation, aModel);

        ModelNode aList = aModel.get("an-object-list");
        // Check that the overall list corrector ran (duplicating the list elements from 1 provided to 2)
        assertEquals(aModel.toString(), 2, aList.asInt());
        for (ModelNode anElement : aList.asList()) {
            // Field must be corrected
            assertTrue(aModel.toString(), anElement.get("a-boolean").asBoolean());
            // Overall object corrector must execute as well
            assertEquals(aModel.toString(), 5, anElement.get("an-int").asInt());
        }
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

    /** Corrector for the overall list */
    private static class ListDuplicationCorrector implements ParameterCorrector {

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            int size = newValue.asInt();
            for (int i = 0; i < size; i++) {
                newValue.add(newValue.get(i));
            }
            return newValue;
        }
    }
}
