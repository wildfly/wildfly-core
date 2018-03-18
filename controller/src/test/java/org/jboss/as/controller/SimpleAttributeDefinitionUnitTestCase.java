/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.controller;

import static org.junit.Assert.fail;

import java.util.List;
import java.util.stream.Collectors;
import org.jboss.as.controller.capability.RuntimeCapability;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit Tests of {@link SimpleAttributeDefinition}
 */
public class SimpleAttributeDefinitionUnitTestCase {

    static enum TestEnum {
        A, B, C
    }

    @Test
    public void testAllowedValuesWithValidator() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setValidator(new EnumValidator<TestEnum>(TestEnum.class, false, false, TestEnum.A, TestEnum.B))
                .build();

        ParameterValidator pv = ad.getValidator();
        Assert.assertTrue(pv instanceof AllowedValuesValidator);
        List<ModelNode> allowed = ((AllowedValuesValidator) pv).getAllowedValues();
        Assert.assertNotNull(allowed);
        Assert.assertEquals(2, allowed.size());
        Assert.assertTrue(allowed.contains(new ModelNode("A")));
        Assert.assertTrue(allowed.contains(new ModelNode("B")));

        ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .build();

        pv = ad.getValidator();
        Assert.assertTrue(pv instanceof AllowedValuesValidator);
        allowed = ((AllowedValuesValidator) pv).getAllowedValues();
        Assert.assertNull(allowed);
    }

    @Test
    public void testAllowedValues() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setAllowedValues("A","B")
                .build();

        List<ModelNode> allowed = ad.getAllowedValues();
        Assert.assertNotNull(allowed);
        Assert.assertEquals(2, allowed.size());
        Assert.assertTrue(allowed.contains(new ModelNode("A")));
        Assert.assertTrue(allowed.contains(new ModelNode("B")));

        ModelNode model = ad.getNoTextDescription(false);
        allowed = model.get(ModelDescriptionConstants.ALLOWED).asList();
        Assert.assertNotNull(allowed);
        Assert.assertEquals(2, allowed.size());
        Assert.assertTrue(allowed.contains(new ModelNode("A")));
        Assert.assertTrue(allowed.contains(new ModelNode("B")));
    }

    @Test
    public void testMinMax() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.INT)
                .setValidator(new IntRangeValidator(5, 10, false, false))
                .build();

        ParameterValidator pv = ad.getValidator();
        Assert.assertTrue(pv instanceof MinMaxValidator);
        Long min = ((MinMaxValidator) pv).getMin();
        Assert.assertNotNull(min);
        Assert.assertEquals(Long.valueOf(5), min);
        Long max = ((MinMaxValidator) pv).getMax();
        Assert.assertNotNull(max);
        Assert.assertEquals(Long.valueOf(10), max);

        ad = new SimpleAttributeDefinitionBuilder("test", ModelType.BOOLEAN)
                .build();

        pv = ad.getValidator();
        Assert.assertTrue(pv instanceof MinMaxValidator);
        min = ((MinMaxValidator) pv).getMin();
        Assert.assertNull(min);
        max = ((MinMaxValidator) pv).getMax();
        Assert.assertNull(max);
    }

    /** For WFCORE-3521 scenarios */
    @Test
    public void testCapabilityRequirements() {
        SimpleAttributeDefinition nameAttribute = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING)
                .build();
        SimpleAttributeDefinition factoryAttribute = new SimpleAttributeDefinitionBuilder("factory", ModelType.STRING)
                .setCapabilityReference("org.wildfly.test.network")
                .build();
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("stack", ModelType.STRING)
                .setCapabilityReference("org.wildfly.test.socket", factoryAttribute, nameAttribute)
                .build();
        ModelNode description = ad.getNoTextDescription(false);
        Assert.assertTrue(description.hasDefined("capability-reference"));
        Assert.assertEquals("org.wildfly.test.socket", description.get("capability-reference").asString());
        Assert.assertTrue(description.hasDefined("capability-reference-pattern-elements"));
        Assert.assertEquals("org.wildfly.test.socket.$factory.$name.$stack", getCapabilityName("org.wildfly.test.socket", description));
        description = nameAttribute.getNoTextDescription(false);
        Assert.assertFalse(description.hasDefined("capability-reference"));
        Assert.assertFalse(description.hasDefined("capability-reference-pattern-elements"));
        description = factoryAttribute.getNoTextDescription(false);
        Assert.assertTrue(description.hasDefined("capability-reference"));
        Assert.assertEquals("org.wildfly.test.network", description.get("capability-reference").asString());
        Assert.assertFalse(description.hasDefined("capability-reference-pattern-elements")); //shouldn't appear since attribute name is the pattern
    }

    private String getCapabilityName(String requirementCp, ModelNode description) {
        return RuntimeCapability.buildDynamicCapabilityName(requirementCp, description.get("capability-reference-pattern-elements").asList().stream().map(ModelNode::asString).map(s -> "$" + s).collect(Collectors.toList()).toArray(new String[0]));
    }
    /** For WFCORE-1590 scenarios */
    @Test
    public void testMinMaxSize() {

        ModelNode toValidate = new ModelNode();
        ModelNode paramVal = toValidate.get("test");

        // Test that explicitly configured validator controls.
        // This isn't some sort of ideal behavior, so if we can make setMin/MaxSize take precedence
        // that's fine and this part of the test can be changed. This is more a sanity check
        // that the current code is doing what's expected.
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setValidator(new StringLengthValidator(2, 3, false, false))
                .setMinSize(1)
                .setMaxSize(4)
                .build();

        paramVal.set("a");
        validate(ad, toValidate, true);
        paramVal.set("ab");
        validate(ad, toValidate, false);
        paramVal.set("abc");
        validate(ad, toValidate, false);
        paramVal.set("abcd");
        validate(ad, toValidate, true);

        // Test that setMin/MaxSize control in the absence of explicit validation
        ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setMinSize(2)
                .setMaxSize(3)
                .build();

        paramVal.set("a");
        validate(ad, toValidate, true);
        paramVal.set("ab");
        validate(ad, toValidate, false);
        paramVal.set("abc");
        validate(ad, toValidate, false);
        paramVal.set("abcd");
        validate(ad, toValidate, true);

        // Test that default min is 1
        ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setMaxSize(3)
                .build();

        paramVal.set("");
        validate(ad, toValidate, true);
        paramVal.set("a");
        validate(ad, toValidate, false);
        paramVal.set("ab");
        validate(ad, toValidate, false);
        paramVal.set("abc");
        validate(ad, toValidate, false);
        paramVal.set("abcd");
        validate(ad, toValidate, true);

        // Test that default max is big
        ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .build();

        paramVal.set("");
        validate(ad, toValidate, true);
        paramVal.set("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
        validate(ad, toValidate, false);
    }

    private static void validate(AttributeDefinition ad, ModelNode operation, boolean expectFail) {
        try {
            ad.validateOperation(operation);
            if (expectFail) {
                fail("Didn't fail validation");
            }
        } catch (OperationFailedException ofe) {
            if (!expectFail) {
                fail("Failed validation " + ofe.toString());
            }
        }
    }

    @Test
    public void testRejectComplexExpressions() throws OperationFailedException {


        ModelNode op = new ModelNode();
        op.get("test").add("${test:1}");
        complexExpressionTest(ModelType.LIST, op);
        op = new ModelNode();
        op.get("test", "foo").set("${test:1}");
        complexExpressionTest(ModelType.OBJECT, op);

        op = new ModelNode();
        op.get("test").set("foo", "${test:1}");
        complexExpressionTest(ModelType.PROPERTY, op);
    }

    private void complexExpressionTest(ModelType type, ModelNode op) throws OperationFailedException {

        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", type)
                .setAllowExpression(true)
                .build();

        try {
            ad.validateAndSet(op, new ModelNode());
            fail("Did not reject " + type);
        } catch (IllegalStateException ok) {
            // good
        }

        try {
            ad.validateOperation(op);
            fail("Did not reject " + type);
        } catch (IllegalStateException ok) {
            // good
        }

    }
}
