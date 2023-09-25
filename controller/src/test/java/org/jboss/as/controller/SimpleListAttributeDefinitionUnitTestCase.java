/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ListValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link SimpleListAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class SimpleListAttributeDefinitionUnitTestCase {

    @Test
    public void testExpressions() throws OperationFailedException, XMLStreamException {

        AttributeDefinition ad = SimpleAttributeDefinitionBuilder.create("x", ModelType.INT).setAllowExpression(true).build();
        SimpleListAttributeDefinition ld = SimpleListAttributeDefinition.Builder.of("test", ad)
                .setAllowExpression(true)
                .setValidator(new IntRangeValidator(1, false, true))
                .build();

        ModelNode op = new ModelNode();
        op.get("test").add(2).add("${test:1}");

        ModelNode validated = ld.validateOperation(op);
        Assert.assertEquals(op.get("test").get(0), validated.get(0));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), validated.get(1));

        ModelNode model = new ModelNode();
        ld.validateAndSet(op, model);
        Assert.assertEquals(op.get("test").get(0), model.get("test").get(0));
        Assert.assertEquals(new ModelNode().set(new ValueExpression(op.get("test").get(1).asString())), model.get("test").get(1));

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

        ad = SimpleAttributeDefinitionBuilder.create("x", ModelType.PROPERTY).setAllowExpression(true).build();
        ld = SimpleListAttributeDefinition.Builder.of("test", ad)
                .setAllowExpression(true)
                .setValidator(new ModelTypeValidator(ModelType.PROPERTY, false, true))
                .build();

        op = new ModelNode();
        op.get("test").add("foo", 2).add("bar", "${test:1}");

        try {
            ld.validateOperation(op);
            org.junit.Assert.fail("Did not reject " + op);
        } catch (IllegalStateException good) {
            //
        }

        try {
            ld.validateAndSet(op, new ModelNode());
            org.junit.Assert.fail("Did not reject " + op);
        } catch (IllegalStateException good) {
            //
        }

    }

    @Test
    public void testBuilderCopyPreservesListValidator() throws OperationFailedException {

        // List can contain at most 2 items. The value of the item can not null
        ParameterValidator pv = new ModelTypeValidator(ModelType.PROPERTY, false, true);
        ListValidator lv = new ListValidator(pv, false, 1, 2);

        AttributeDefinition ad = SimpleAttributeDefinitionBuilder.create("x", ModelType.PROPERTY).build();
        SimpleListAttributeDefinition original = SimpleListAttributeDefinition.Builder.of("test", ad)
                .setListValidator(lv)
                .build();
        SimpleListAttributeDefinition copy = new SimpleListAttributeDefinition.Builder(original).build();

        ModelNode validValue = new ModelNode();
        validValue.add("foo", "bar");

        // too many elements
        ModelNode invalidValue = new ModelNode();
        invalidValue.add("foo", "bar");
        invalidValue.add("foo2", "baz");
        invalidValue.add("foo3", "bat");

        // undefined is not a valid element value
        ModelNode invalidValue2 = new ModelNode();
        invalidValue2.add(new ModelNode());

        // the original and copy attribute definition must validate the same way
        validateOperation(original, validValue, true);
        validateOperation(copy, validValue, true);

        validateOperation(original, invalidValue, false);
        validateOperation(copy, invalidValue, false);

        validateOperation(original, invalidValue2, false);
        validateOperation(copy, invalidValue2, false);
    }

    @Test
    public void testSpaceSeparatedParserAndMarshaller() throws XMLStreamException {
        AttributeDefinition attributeDefinition = new StringListAttributeDefinition.Builder("connectors")
                .setXmlName("connectors")
                .setAttributeParser(AttributeParser.STRING_LIST)
                .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
                .build();

        // parse the XML attribute
        ModelNode model = new ModelNode();
        attributeDefinition.getParser().parseAndSetParameter(attributeDefinition, "foo bar", model, null);

        Assert.assertEquals(2, model.get(attributeDefinition.getName()).asList().size());
        Assert.assertEquals("foo", model.get(attributeDefinition.getName()).asList().get(0).asString());
        Assert.assertEquals("bar", model.get(attributeDefinition.getName()).asList().get(1).asString());

        StringWriter stringWriter = new StringWriter();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(stringWriter);

        // marshall the XML attribute
        writer.writeStartElement("resource");
        attributeDefinition.getMarshaller().marshallAsAttribute(attributeDefinition, model, true, writer);
        writer.writeEndElement();
        writer.close();

        Assert.assertEquals("<resource connectors=\"foo bar\"></resource>", stringWriter.toString());

        // Validate that parsing and marshalling of empty string is symmetric (WFCORE-763)
        model = new ModelNode();
        attributeDefinition.getParser().parseAndSetParameter(attributeDefinition, "", model, null);

        Assert.assertEquals(0, model.get(attributeDefinition.getName()).asList().size());

        stringWriter = new StringWriter();
        writer = factory.createXMLStreamWriter(stringWriter);

        // marshall the XML attribute
        writer.writeStartElement("resource");
        attributeDefinition.getMarshaller().marshallAsAttribute(attributeDefinition, model, true, writer);
        writer.writeEndElement();
        writer.close();

        Assert.assertEquals("<resource connectors=\"\"></resource>", stringWriter.toString());
    }

    @Test
    public void testSimpleListAttributeDefinitionDefaultMarshaller() throws XMLStreamException {
        AttributeDefinition ad = SimpleAttributeDefinitionBuilder.create("x", ModelType.INT)
                .setAttributeMarshaller(AttributeMarshallers.SIMPLE_ELEMENT_UNWRAP).build();
        SimpleListAttributeDefinition attributeDefinition = SimpleListAttributeDefinition.Builder.of("test", ad).build();

        // parse the XML attribute
        ModelNode model = new ModelNode();
        attributeDefinition.getParser().parseAndSetParameter(attributeDefinition, "10 20", model, null);

        Assert.assertEquals(2, model.get(attributeDefinition.getName()).asList().size());
        Assert.assertEquals(10, model.get(attributeDefinition.getName()).asList().get(0).asInt());
        Assert.assertEquals(20, model.get(attributeDefinition.getName()).asList().get(1).asInt());

        StringWriter stringWriter = new StringWriter();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(stringWriter);

        // marshall the XML attribute
        attributeDefinition.getMarshaller().marshall(attributeDefinition, model, true, writer);
        writer.close();

        Assert.assertEquals("<test><x>10</x><x>20</x></test>", stringWriter.toString());

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(stringWriter.toString()));
        model = new ModelNode();
        attributeDefinition.getParser().parseAndSetParameter(attributeDefinition, "", model, reader);
        reader.close();
        Assert.assertEquals(0, model.get(attributeDefinition.getName()).asList().size());

        stringWriter = new StringWriter();
        writer = factory.createXMLStreamWriter(stringWriter);

        // marshall the XML attribute
        attributeDefinition.getMarshaller().marshall(attributeDefinition, model, true, writer);
        writer.close();

        Assert.assertEquals("<test></test>", stringWriter.toString());
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
