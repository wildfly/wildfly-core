/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

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
}
