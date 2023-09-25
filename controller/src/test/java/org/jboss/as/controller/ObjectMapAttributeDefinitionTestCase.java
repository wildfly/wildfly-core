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
 * Unit tests of {@link ObjectMapAttributeDefinitionTestCase}.
 *
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class ObjectMapAttributeDefinitionTestCase {
    private final AttributeDefinition attribute1 = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
    private final AttributeDefinition attribute2 = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();
    private final AttributeDefinition attribute3 = SimpleAttributeDefinitionBuilder.create("c", ModelType.STRING).setAllowExpression(true).build();
    private final ObjectTypeAttributeDefinition complex = ObjectTypeAttributeDefinition.create("complex", attribute1, attribute2, attribute3).build();
    private final ObjectMapAttributeDefinition map = ObjectMapAttributeDefinition.create("map", complex).build();



    @Test
    public void testExpressions() throws OperationFailedException {


        ModelNode op = new ModelNode();
        ModelNode mapAttr = op.get(this.map.getName());
        ModelNode one = mapAttr.get("key1");
        one.get("a").set(2);
        one.get("b").set(true);
        one.get("c").set("some value");
        ModelNode two = mapAttr.get("key2");
        two.get("a").set(5);
        two.get("b").set(new ValueExpression("${test:true}"));
        two.get("c").set(new ValueExpression("${test:value}"));

        ModelNode validated = this.map.validateOperation(op);
        Assert.assertEquals(one, validated.get("key1"));
        Assert.assertEquals(one.get("a"), validated.get("key1").get("a"));
        Assert.assertEquals(two.get("b"), validated.get("key2").get("b"));

        ModelNode model = new ModelNode();
        this.map.validateAndSet(op, model);
        Assert.assertEquals(one, model.get(map.getName()).get("key1"));
        Assert.assertEquals(one.get("a"), model.get(map.getName()).get("key1").get("a"));
        Assert.assertEquals(one.get("b"), model.get(map.getName()).get("key1").get("b"));

        op = new ModelNode();
        one = op.get(map.getName());
        one.get("a").set(2);
        one.get("b").set(true);
        two = op.get(map.getName());
        two.get("a").set("${test:1}");
        two.get("b").set(false);

        try {
            this.map.validateOperation(op);
            Assert.fail("Did not reject " + op);
        } catch (OperationFailedException good) {
            //
        }

        try {
            this.map.validateAndSet(op, new ModelNode());
            Assert.fail("Did not reject " + op);
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
        final AttributeDefinition anObjectMap = ObjectMapAttributeDefinition.Builder.of("an-object-map", anObject)
                .setCorrector(new KeyRenameCorrector())
                .build();

        ModelNode testOperation = new ModelNode();
        ModelNode testMap = testOperation.get("an-object-map");
        testMap.get("a-key","a-boolean").set(false);

        ModelNode aModel = new ModelNode();
        anObjectMap.validateAndSet(testOperation, aModel);

        ModelNode aMap = aModel.get("an-object-map");
        assertEquals(aModel.toString(), 1, aMap.asInt());
        assertTrue(aModel.toString(), aMap.get("a-different-key", "a-boolean").asBoolean());
        assertEquals(aModel.toString(), 5, aMap.get("a-different-key", "an-int").asInt());
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

    /** Corrector for the overall map */
    private static class KeyRenameCorrector implements ParameterCorrector {

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            if (newValue.has("a-key")) {
                newValue.get("a-different-key").set(newValue.remove("a-key"));
            }
            return newValue;
        }
    }
}
