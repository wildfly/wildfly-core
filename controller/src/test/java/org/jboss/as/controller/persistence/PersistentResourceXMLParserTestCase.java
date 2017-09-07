/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.persistence.PersistentResourceXMLParserTestCase.IdentityMappingObjectDefinition.OBJECT_DEFINITION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectMapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Assert;
import org.junit.Test;
import org.projectodd.vdx.core.XMLStreamValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 * @author Tomaz Cerar
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class PersistentResourceXMLParserTestCase {


    private static String readResource(final String name) throws IOException {

        URL configURL = PersistentResourceXMLParserTestCase.class.getResource(name);
        Assert.assertNotNull(name + " url is null", configURL);

        StringWriter writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
        }
        return writer.toString();
    }

    private static XMLExtendedStreamWriter createXMLStreamWriter(XMLStreamWriter writer) throws Exception {
        return new FormattingXMLStreamWriter(writer);
    }

    @Test
    public void testWrappersAndGroups() throws Exception {
        MyParser parser = new MyParser();
        String xml = readResource("groups-wrappers-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        ModelNode subsystem = opsToModel(operations);

        assertEquals("bar", subsystem.get("resource", "foo", "cluster-attr1").asString());
        assertEquals("baz", subsystem.get("resource", "foo", "cluster-attr2").asString());
        assertEquals("alice", subsystem.get("resource", "foo", "security-my-attr1").asString());
        assertEquals("bob", subsystem.get("resource", "foo", "security-my-attr2").asString());
        assertEquals("bar2", subsystem.get("resource", "foo2", "cluster-attr1").asString());
        assertEquals("baz2", subsystem.get("resource", "foo2", "cluster-attr2").asString());

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance()
                .createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));

    }

    @Test
    public void testGroups() throws Exception {
        MyParser parser = new AttributeGroupParser();
        String xml = readResource("groups-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        ModelNode subsystem = opsToModel(operations);

        assertEquals("bar", subsystem.get("resource", "foo", "cluster-attr1").asString());
        assertEquals("baz", subsystem.get("resource", "foo", "cluster-attr2").asString());
        assertEquals("alice", subsystem.get("resource", "foo", "security-my-attr1").asString());
        assertEquals("bob", subsystem.get("resource", "foo", "security-my-attr2").asString());
        assertEquals("val", subsystem.get("resource", "foo", "props", "prop").asString());
        assertEquals("val", subsystem.get("resource", "foo", "wrapped-properties", "prop").asString());
        assertEquals("bar2", subsystem.get("resource", "foo2", "cluster-attr1").asString());
        assertEquals("baz2", subsystem.get("resource", "foo2", "cluster-attr2").asString());

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance()
                .createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));

    }

    @Test(expected = XMLStreamValidationException.class)
    public void testInvalidMultipleGroups() throws Exception {
        MyParser parser = new AttributeGroupParser();
        String xml = readResource("invalid-multiple-groups-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);
    }

    @Test(expected = XMLStreamValidationException.class)
    public void testInvalidMultipleObjectTypes() throws Exception {
        MyParser parser = new AttributeGroupParser();
        String xml = readResource("invalid-multiple-object-type-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);
    }

    @Test(expected = XMLStreamException.class)
    public void testInvalidGroups() throws Exception {
        MyParser parser = new AttributeGroupParser();
        String xml =
                "<subsystem xmlns=\"" + MyParser.NAMESPACE + "\">" +
                        "   <invalid/>" +
                        "</subsystem>";
        StringReader strReader = new StringReader(xml);
        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);
    }

    @Test
    public void testChildlessResource() throws Exception {
        MyParser parser = new ChildlessParser();
        String xml =
                "<subsystem xmlns=\"" + MyParser.NAMESPACE + "\">" +
                        "   <cluster attr1=\"alice\"/>" +
                        "</subsystem>";
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        ModelNode subsystem = opsToModel(operations);

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance()
                .createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }

    @Test
    public void testSimpleParser() throws Exception {

        MyParser parser = new MyParser();

        String xml = readResource("simple-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(4, operations.size());
        ModelNode subsystem = opsToModel(operations);
        ModelNode resource = subsystem.get("resource","foo");

        ModelNode complexMap = MyParser.COMPLEX_MAP.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, resource);
        Assert.assertEquals("Model type should be map", ModelType.OBJECT, complexMap.getType());
        Assert.assertEquals("Map should have 3 elements", 3, complexMap.asList().size());
        Assert.assertEquals("some.class1", complexMap.get("key1", "name").asString());
        Assert.assertEquals("some.class2", complexMap.get("key2", "name").asString());
        Assert.assertEquals("some.module3", complexMap.get("key3", "module").asString());


        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }


    @Test
    public void testElementParsers() throws Exception {

        MyParser parser = new MyParser();

        String xml = readResource("elements.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(5, operations.size());
        ModelNode subsystem = opsToModel(operations);

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }

    @Test
    public void testMail() throws Exception {

        MyParser parser = new MailParser();

        String xml = readResource("mail-parser.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(4, operations.size());
        ModelNode subsystem = opsToModel(operations);


        ModelNode propsModel = subsystem.get("mail-session", "custom");
        assertTrue("Model should be defined", propsModel.has(MyParser.WRAPPED_PROPERTIES.getName()));
        Map<String,String> props = MyParser.WRAPPED_PROPERTIES.unwrap(ExpressionResolver.TEST_RESOLVER, propsModel);
        Assert.assertEquals(0, props.size());

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }


    @Test
    public void testServerParser() throws Exception {
        ServerParser parser = new ServerParser();

        String xml = readResource("server-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(4, operations.size());
        ModelNode subsystem = opsToModel(operations);

        ModelNode server = subsystem.get("server", "default");
        ModelNode interceptors = MyParser.INTERCEPTORS.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, server);
        Assert.assertEquals("Model type should be list", ModelType.LIST, interceptors.getType());
        Assert.assertEquals("List should have 0 elements", 0, interceptors.asList().size());

        ModelNode complexList = MyParser.COMPLEX_LIST.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, server); //this one should be undefined
        Assert.assertTrue("Should be empty", !complexList.isDefined());

        ModelNode complexListWithDefault = MyParser.COMPLEX_LIST_WITH_DEFAULT.resolveModelAttribute(ExpressionResolver.TEST_RESOLVER, server); //this one should be undefined
        Assert.assertEquals("Model type should be list", ModelType.LIST, complexListWithDefault.getType());
        Assert.assertEquals("List should have 1 elements", 1, complexListWithDefault.asList().size());

        /*List<ModelNode> unwrapped = MyParser.COMPLEX_LIST_WITH_DEFAULT.unwrap(ExpressionResolver.TEST_RESOLVER, server); //this one should be undefined
        Assert.assertEquals("it should contain one element", 1, unwrapped.size());*/


        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }

    @Test
    public void testServerWithComplexAttributeParser() throws Exception {
        ServerParser parser = new ServerParser();

        String xml = readResource("server-complex-attribute.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(MyParser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(5, operations.size());
        ModelNode subsystem = opsToModel(operations);

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }


    @Test
    public void testORBSubsystem() throws Exception {
        IIOPSubsystemParser parser = new IIOPSubsystemParser();

        String xml = readResource("orb-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName("urn:jboss:domain:orb-test:1.0", "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(1, operations.size());
        ModelNode subsystem = opsToModel(operations);

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }


    @Test
    public void testComplexAttributesStuff() throws Exception {
        CoreParser parser = new CoreParser();

        String xml = readResource("core-subsystem.xml");
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName("urn:jboss:domain:core:1.0", "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);

        Assert.assertEquals(2, operations.size());
        Assert.assertEquals(2, operations.get(1).get("listeners").asList().size());
        ModelNode subsystem = opsToModel(operations);

        ModelNode identityAttributes = subsystem.get("service","process-state-listeners","identity-mapping", "new-identity-attributes");
        ModelNode listAttribute = identityAttributes.asList().get(0);
        List<String> list = NewIdentityAttributeObjectDefinition.VALUE.unwrap(ExpressionResolver.SIMPLE, listAttribute);
        Assert.assertEquals("number of elements is wrong", 3, list.size());
        Assert.assertEquals("value with spaces",list.get(0));
        Assert.assertEquals("second element",list.get(1));
        Assert.assertEquals("third",list.get(2));

        StringWriter stringWriter = new StringWriter();
        XMLExtendedStreamWriter xmlStreamWriter = createXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
        SubsystemMarshallingContext context = new SubsystemMarshallingContext(subsystem, xmlStreamWriter);
        mapper.deparseDocument(parser, context, xmlStreamWriter);
        String out = stringWriter.toString();
        Assert.assertEquals(normalizeXML(xml), normalizeXML(out));
    }

    private ModelNode opsToModel(List<ModelNode> operations) {
        ModelNode subsystem = new ModelNode();

        for (ModelNode addResourceOp : operations) {
            ModelNode resource = addResourceOp.clone();

            resource.remove("operation");
            PathAddress address = PathAddress.pathAddress(resource.remove("address"));
            subsystem.get(getAddress(address)).set(resource);
        }
        return subsystem;
    }

    private String[] getAddress(PathAddress address) {
        String[] res = new String[(address.size() - 1) * 2];
        for (int i = 0; i < address.size() - 1; i++) {
            PathElement el = address.getElement(i + 1);
            res[i * 2] = el.getKey();
            res[(i * 2) + 1] = el.getValue();
        }
        return res;
    }

    private static String normalizeXML(String xml) throws Exception {
        // Remove all white space adjoining tags ("trim all elements")
        xml = xml.replaceAll("\\s*<", "<");
        xml = xml.replaceAll(">\\s*", ">");

        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS domLS = (DOMImplementationLS) registry.getDOMImplementation("LS");
        LSParser lsParser = domLS.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);

        LSInput input = domLS.createLSInput();
        input.setStringData(xml);
        Document document = lsParser.parse(input);

        LSSerializer lsSerializer = domLS.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("comments", Boolean.FALSE);
        lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        return lsSerializer.writeToString(document);
    }

    private static class MyParser extends PersistentResourceXMLParser {

        protected static final String NAMESPACE = "urn:jboss:domain:test:1.0";

        protected static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "foo");


        static final AttributeDefinition clusterAttr1 = create("cluster-attr1", ModelType.STRING)
                .setAttributeGroup("cluster")
                .setXmlName("attr1")
                .build();
        static final AttributeDefinition clusterAttr2 = create("cluster-attr2", ModelType.STRING)
                .setAttributeGroup("cluster")
                .setXmlName("attr2")
                .build();

        static final AttributeDefinition securityAttr1 = create("security-my-attr1", ModelType.STRING)
                .setAttributeGroup("security")
                .setXmlName("my-attr1")
                .build();
        static final AttributeDefinition securityAttr2 = create("security-my-attr2", ModelType.STRING)
                .setAttributeGroup("security")
                .setXmlName("my-attr2")
                .build();
        static final AttributeDefinition nonGroupAttr1 = create("non-group-attr", ModelType.STRING)
                .setXmlName("no-attr1")
                .build();
        static final StringListAttributeDefinition ALIAS = new StringListAttributeDefinition.Builder("alias")
                .setRequired(false)
                .setElementValidator(new StringLengthValidator(1))
                .setAttributeParser(AttributeParser.COMMA_DELIMITED_STRING_LIST)
                .setAttributeMarshaller(AttributeMarshaller.COMMA_STRING_LIST)
                .build();
        static final ObjectTypeAttributeDefinition SOURCE_CREDENTIAL_REFERENCE = new ObjectTypeAttributeDefinition.Builder("source-credential-reference", ALIAS)
                .setAttributeGroup("source")
                .setXmlName("credential-reference")
                .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
                .setAttributeParser(AttributeParser.OBJECT_PARSER)
                .setRequired(false)
                .build();
        static final ObjectTypeAttributeDefinition TARGET_CREDENTIAL_REFERENCE = new ObjectTypeAttributeDefinition.Builder("target-credential-reference", ALIAS)
                .setAttributeGroup("target")
                .setXmlName("credential-reference")
                .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
                .setAttributeParser(AttributeParser.OBJECT_PARSER)
                .setRequired(false)
                .build();
        static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder("props", true)
                .setAttributeMarshaller(AttributeMarshaller.PROPERTIES_MARSHALLER_UNWRAPPED)
                .setAttributeParser(AttributeParser.PROPERTIES_PARSER_UNWRAPPED)
                .setAllowExpression(true)
                .build();

        static final PropertiesAttributeDefinition WRAPPED_PROPERTIES_GROUP = new PropertiesAttributeDefinition.Builder("wrapped-properties", true)
                .setAttributeGroup("mygroup")
                .setAllowExpression(true)
                .build();

        static final PropertiesAttributeDefinition WRAPPED_PROPERTIES = new PropertiesAttributeDefinition.Builder("properties", true)
                .setAllowExpression(true)
                .build();

        static final SimpleAttributeDefinition BUFFER_SIZE = new SimpleAttributeDefinitionBuilder("buffer-size", ModelType.INT)
                .setRequired(false)
                .setDefaultValue(new ModelNode(1024))
                .setAllowExpression(true)
                .build();
        static final SimpleAttributeDefinition BUFFERS_PER_REGION = new SimpleAttributeDefinitionBuilder("buffers-per-region", ModelType.INT)
                .setRequired(false)
                .setDefaultValue(new ModelNode(1024))
                .setAllowExpression(true)
                .build();
        static final SimpleAttributeDefinition MAX_REGIONS = new SimpleAttributeDefinitionBuilder("max-regions", ModelType.INT)
                .setRequired(false)
                .setAllowExpression(true)
                .setDefaultValue(new ModelNode(10))
                .build();

        static final SimpleAttributeDefinition STATISTICS_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.STATISTICS_ENABLED, ModelType.BOOLEAN)
                .setAttributeGroup("statistics")
                .setXmlName("enabled")
                .setDefaultValue(new ModelNode(false))
                .setRequired(false)
                .setAllowExpression(true)
                .build();
        static final SimpleAttributeDefinition SECURITY_ENABLED = new SimpleAttributeDefinitionBuilder("security-enabled", ModelType.BOOLEAN)
                .setAttributeGroup("security")
                .setXmlName("enabled")
                .setDefaultValue(new ModelNode(true))
                .setRequired(false)
                .setAllowExpression(true)
                .setRestartAllServices()
                .build();


        static final ObjectTypeAttributeDefinition CLASS = ObjectTypeAttributeDefinition.Builder.of("class",
                create("name", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build(),
                create("module", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build())
                .build();


        static final ObjectTypeAttributeDefinition OBJECT = ObjectTypeAttributeDefinition.Builder.of("object",ALIAS, SECURITY_ENABLED, securityAttr1)
                .build();
        static final ModelNode COMPLEX_LIST_DEFAULT_VALUE = new ModelNode();
        static{

            ModelNode clazz = COMPLEX_LIST_DEFAULT_VALUE.add();
            clazz.get("name").set("some.class");
            clazz.get("module").set("some.module");

        }

        static final ObjectTypeAttributeDefinition USERS_PROPERTIES = ObjectTypeAttributeDefinition.Builder.of("user-properties",
                create("path", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build(),
                create("relative-to", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build())
                .build();


        static final ObjectListAttributeDefinition INTERCEPTORS = ObjectListAttributeDefinition.Builder.of("interceptors", CLASS)
                .setRequired(false)
                .setAllowExpression(false)
                .setMinSize(0)
                .setMaxSize(Integer.MAX_VALUE)
                .setDefaultValue(COMPLEX_LIST_DEFAULT_VALUE)
                .build();

        static final ObjectListAttributeDefinition COMPLEX_LIST = ObjectListAttributeDefinition.Builder.of("complex-list", CLASS)
                .setRequired(false)
                .setAllowExpression(false)
                .build();

        static final ObjectListAttributeDefinition COMPLEX_LIST_WITH_DEFAULT = ObjectListAttributeDefinition.Builder.of("complex-list-with-default", CLASS)
                .setRequired(false)
                .setAllowExpression(false)
                .setDefaultValue(COMPLEX_LIST_DEFAULT_VALUE)
                .build();

        static final ObjectMapAttributeDefinition COMPLEX_MAP = ObjectMapAttributeDefinition.create("complex-map", CLASS)
                .setRequired(false)
                .setAttributeParser(new AttributeParsers.ObjectMapParser("element", true))
                .setAttributeMarshaller(new AttributeMarshallers.ObjectMapAttributeMarshaller(null, "element", true))
                .build();
        static final ObjectMapAttributeDefinition COMPLEX_MAP2 = ObjectMapAttributeDefinition.create("object-map", OBJECT)
                .setRequired(false)
                .setAttributeParser(AttributeParsers.getObjectMapAttributeParser("name")) //change key attribute to name
                .setAttributeMarshaller(AttributeMarshallers.getObjectMapAttributeMarshaller("name"))
                .build();


        static final ObjectMapAttributeDefinition COMPLEX_MAP3 = ObjectMapAttributeDefinition.create("map", CLASS)
                .setRequired(false)
                .build();


        static final PersistentResourceDefinition RESOURCE_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("resource"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(clusterAttr1);
                attributes.add(clusterAttr1);
                attributes.add(securityAttr1);
                attributes.add(securityAttr2);
                attributes.add(nonGroupAttr1);
                attributes.add(PROPERTIES);
                attributes.add(WRAPPED_PROPERTIES_GROUP);
                attributes.add(WRAPPED_PROPERTIES);
                attributes.add(ALIAS);
                attributes.add(COMPLEX_MAP);
                attributes.add(USERS_PROPERTIES);
                return attributes;
            }
        };

        static final PersistentResourceDefinition BUFFER_CACHE_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("buffer-cache"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(BUFFER_SIZE);
                attributes.add(BUFFERS_PER_REGION);
                attributes.add(MAX_REGIONS);
                attributes.add(ALIAS);
                attributes.add(COMPLEX_MAP2);
                return attributes;
            }
        };

        static final PersistentResourceDefinition OBJECT_TYPE_TEST = new PersistentResourceDefinition(PathElement.pathElement("object-type-test"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(USERS_PROPERTIES);
                return attributes;
            }
        };


        static final PersistentResourceDefinition SERVER_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("server"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(STATISTICS_ENABLED);
                attributes.add(SECURITY_ENABLED);
                attributes.add(INTERCEPTORS);
                attributes.add(COMPLEX_LIST);
                attributes.add(COMPLEX_LIST_WITH_DEFAULT);
                attributes.add(COMPLEX_MAP3);
                return attributes;
            }

            @Override
            protected List<? extends PersistentResourceDefinition> getChildren() {
                return Arrays.asList(BUFFER_CACHE_INSTANCE);
            }
        };


        static final PersistentResourceDefinition CUSTOM_SERVER_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("custom"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(BUFFER_SIZE);
                attributes.add(BUFFERS_PER_REGION);
                attributes.add(PROPERTIES);
                return attributes;
            }

            @Override
            protected List<? extends PersistentResourceDefinition> getChildren() {
                return Arrays.asList(BUFFER_CACHE_INSTANCE);
            }
        };


        static final PersistentResourceDefinition SESSION_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("mail-session"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(MAX_REGIONS);
                attributes.add(WRAPPED_PROPERTIES);
                return attributes;
            }

            @Override
            protected List<? extends PersistentResourceDefinition> getChildren() {
                return Arrays.asList(CUSTOM_SERVER_INSTANCE);
            }
        };


        PersistentResourceDefinition SUBSYSTEM_ROOT_INSTANCE = new PersistentResourceDefinition(SUBSYSTEM_PATH, new NonResolvingResourceDescriptionResolver()) {

            @Override
            public Collection<AttributeDefinition> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            protected List<? extends PersistentResourceDefinition> getChildren() {
                List<PersistentResourceDefinition> children = new ArrayList<>();
                children.add(RESOURCE_INSTANCE);
                children.add(BUFFER_CACHE_INSTANCE);
                children.add(OBJECT_TYPE_TEST);
                return children;
            }
        };


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE.getPathElement(), NAMESPACE)
                    .addChild(
                            builder(RESOURCE_INSTANCE.getPathElement())
                                    .setXmlWrapperElement("resources")
                                    .addAttributes(
                                            // cluster group
                                            clusterAttr1,
                                            clusterAttr2,
                                            // security group
                                            securityAttr1,
                                            securityAttr2,
                                            //no group element
                                            nonGroupAttr1,
                                            PROPERTIES,
                                            WRAPPED_PROPERTIES_GROUP,
                                            WRAPPED_PROPERTIES,
                                            ALIAS,
                                            COMPLEX_MAP,
                                            USERS_PROPERTIES
                                    )
                    )
                    .addChild(
                            builder(BUFFER_CACHE_INSTANCE.getPathElement())
                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, MAX_REGIONS)
                                    .addAttribute(ALIAS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
                                    .addAttribute(COMPLEX_MAP2)
                    )
                    .addChild(
                            builder(OBJECT_TYPE_TEST.getPathElement())
                                    .addAttribute(USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
                    )
                    .build();
        }
    }

    private static class AttributeGroupParser extends MyParser {


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE.getPathElement(), NAMESPACE)
                    .addChild(
                            builder(RESOURCE_INSTANCE.getPathElement())
                                    .addAttributes(
                                            // cluster group
                                            clusterAttr1,
                                            clusterAttr2,
                                            // security group
                                            securityAttr1,
                                            securityAttr2,
                                            //no group element
                                            nonGroupAttr1,
                                            PROPERTIES,
                                            WRAPPED_PROPERTIES_GROUP,
                                            ALIAS,
                                            USERS_PROPERTIES,
                                            SOURCE_CREDENTIAL_REFERENCE,
                                            TARGET_CREDENTIAL_REFERENCE
                                    )
                    )
                    .addChild(
                            builder(BUFFER_CACHE_INSTANCE.getPathElement())
                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, MAX_REGIONS)
                                    .addAttribute(ALIAS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
                    )
                    .addChild(
                            builder(OBJECT_TYPE_TEST.getPathElement())
                                    .addAttribute(USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
                    )
                    .build();
        }
    }

    private static class ServerParser extends MyParser {


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE.getPathElement(), NAMESPACE)
                    .addChild(
                            builder(SERVER_INSTANCE.getPathElement())
                                    .addAttributes(SECURITY_ENABLED, STATISTICS_ENABLED)
                                    .addAttribute(INTERCEPTORS)
                                    .addAttribute(COMPLEX_LIST)
                                    .addAttribute(COMPLEX_LIST_WITH_DEFAULT)
                                    .addAttribute(PROPERTIES)
                                    .addAttribute(COMPLEX_MAP3)
                                    .addChild(
                                            builder(BUFFER_CACHE_INSTANCE.getPathElement())
                                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, MAX_REGIONS)
                                                    .addAttribute(ALIAS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
                                    )
                    )
                    .build();
        }
    }

    private static class ChildlessParser extends MyParser {

        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE.getPathElement(), NAMESPACE)
                    .addAttributes(clusterAttr1)
                    .build();
        }
    }

    private static class MailParser extends MyParser {

        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE.getPathElement(), NAMESPACE)
                    .addChild(
                            builder(SESSION_INSTANCE.getPathElement())
                                    .setNameAttributeName("session-name") //custom name attribute for session resources
                                    .addAttributes(MAX_REGIONS, WRAPPED_PROPERTIES)
                                    .addChild(
                                            builder(CUSTOM_SERVER_INSTANCE.getPathElement())
                                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, PROPERTIES)
                                                    .setXmlElementName("custom-server")
                                    )
                    )
                    .build();
        }
    }

    private static class IIOPSubsystemParser extends PersistentResourceXMLParser {

        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(IIOPRootDefinition.INSTANCE.getPathElement(), "urn:jboss:domain:orb-test:1.0")
                    .addAttributes(IIOPRootDefinition.ALL_ATTRIBUTES.toArray(new AttributeDefinition[0]))
                    .build();
        }
    }

    private static class IIOPRootDefinition extends PersistentResourceDefinition {

        static final ModelNode NONE = new ModelNode("none");


        //ORB attributes

        static final AttributeDefinition PERSISTENT_SERVER_ID = new SimpleAttributeDefinitionBuilder(
                Constants.ORB_PERSISTENT_SERVER_ID, ModelType.STRING, true).setAttributeGroup(Constants.ORB)
                .setDefaultValue(new ModelNode().set("1")).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

        static final AttributeDefinition GIOP_VERSION = new SimpleAttributeDefinitionBuilder(Constants.ORB_GIOP_VERSION,
                ModelType.STRING, true).setAttributeGroup(Constants.ORB).setDefaultValue(new ModelNode().set("1.2"))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).setAllowExpression(true).build();

        protected static final AttributeDefinition SOCKET_BINDING = new SimpleAttributeDefinitionBuilder(
                Constants.ORB_SOCKET_BINDING, ModelType.STRING, true).setAttributeGroup(Constants.ORB)
                .setDefaultValue(new ModelNode().set("iiop")).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF).build();

        static final AttributeDefinition SSL_SOCKET_BINDING = new SimpleAttributeDefinitionBuilder(
                Constants.ORB_SSL_SOCKET_BINDING, ModelType.STRING, true).setAttributeGroup(Constants.ORB)
                .setDefaultValue(new ModelNode().set("iiop-ssl")).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF).build();

        //TCP attributes
        static final AttributeDefinition HIGH_WATER_MARK = new SimpleAttributeDefinitionBuilder(
                Constants.TCP_HIGH_WATER_MARK, ModelType.INT, true).setAttributeGroup(Constants.ORB_TCP)
                .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, false))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).setAllowExpression(true).build();

        static final AttributeDefinition NUMBER_TO_RECLAIM = new SimpleAttributeDefinitionBuilder(
                Constants.TCP_NUMBER_TO_RECLAIM, ModelType.INT, true).setAttributeGroup(Constants.ORB_TCP)
                .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, false))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).setAllowExpression(true).build();

        //initializer attributes
        static final AttributeDefinition SECURITY = new SimpleAttributeDefinitionBuilder(
                Constants.ORB_INIT_SECURITY, ModelType.STRING, true)
                .setAttributeGroup(Constants.ORB_INIT)
                .setDefaultValue(NONE)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).setAllowExpression(true)
                .build();

        static final AttributeDefinition TRANSACTIONS = new SimpleAttributeDefinitionBuilder(
                Constants.ORB_INIT_TRANSACTIONS, ModelType.STRING, true)
                .setAttributeGroup(Constants.ORB_INIT)
                .setDefaultValue(NONE)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).setAllowExpression(true).build();

        //Naming attributes

        static final AttributeDefinition ROOT_CONTEXT = new SimpleAttributeDefinitionBuilder(
                Constants.NAMING_ROOT_CONTEXT, ModelType.STRING, true)
                .setAttributeGroup(Constants.NAMING)
                .setDefaultValue(new ModelNode(Constants.ROOT_CONTEXT_INIT_REF))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition EXPORT_CORBALOC = new SimpleAttributeDefinitionBuilder(
                Constants.NAMING_EXPORT_CORBALOC, ModelType.BOOLEAN, true)
                .setAttributeGroup(Constants.NAMING)
                .setDefaultValue(new ModelNode(true))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        //Security attributes

        static final AttributeDefinition SUPPORT_SSL = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_SUPPORT_SSL, ModelType.BOOLEAN, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode(false))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_SECURITY_DOMAIN, ModelType.STRING, true)
                .setAttributeGroup(Constants.SECURITY)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
                .build();

        static final AttributeDefinition ADD_COMPONENT_INTERCEPTOR = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_ADD_COMP_VIA_INTERCEPTOR, ModelType.BOOLEAN, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode(true))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition CLIENT_SUPPORTS = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_CLIENT_SUPPORTS, ModelType.STRING, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode().set("MutualAuth"))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition CLIENT_REQUIRES = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_CLIENT_REQUIRES, ModelType.STRING, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode("None"))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition SERVER_SUPPORTS = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_SERVER_SUPPORTS, ModelType.STRING, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode("MutualAuth"))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition SERVER_REQUIRES = new SimpleAttributeDefinitionBuilder(
                Constants.SECURITY_SERVER_REQUIRES, ModelType.STRING, true)
                .setAttributeGroup(Constants.SECURITY)
                .setDefaultValue(new ModelNode("None"))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAllowExpression(true)
                .build();

        static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder(
                Constants.PROPERTIES, true)
                .setWrapXmlElement(true)
                .setWrapperElement(Constants.PROPERTIES)
                .setXmlName(Constants.PROPERTY)
                .setAllowExpression(true)
                .setRestartAllServices()
                .build();

        //ior transport config attributes
        static final AttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_AS_CONTEXT_REALM, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_AS_CONTEXT)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition REQUIRED = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_AS_CONTEXT_REQUIRED, ModelType.BOOLEAN, true)
                .setAttributeGroup(Constants.IOR_AS_CONTEXT)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(new ModelNode(false))
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition INTEGRITY = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_INTEGRITY, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition CONFIDENTIALITY = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_CONFIDENTIALITY, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition TRUST_IN_TARGET = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_TRUST_IN_TARGET, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition TRUST_IN_CLIENT = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_TRUST_IN_CLIENT, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition DETECT_REPLAY = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_DETECT_REPLAY, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        static final AttributeDefinition DETECT_MISORDERING = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_TRANSPORT_DETECT_MISORDERING, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_TRANSPORT_CONFIG)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        //ior as context attributes
        static final AttributeDefinition AUTH_METHOD = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_AS_CONTEXT_AUTH_METHOD, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_AS_CONTEXT)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(new ModelNode("username_password"))
                .setAllowExpression(true)
                .build();

        //ior sas context attributes
        static final AttributeDefinition CALLER_PROPAGATION = new SimpleAttributeDefinitionBuilder(
                Constants.IOR_SAS_CONTEXT_CALLER_PROPAGATION, ModelType.STRING, true)
                .setAttributeGroup(Constants.IOR_SAS_CONTEXT)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setDefaultValue(NONE)
                .setAllowExpression(true)
                .build();

        // list that contains ORB attribute definitions
        static final List<AttributeDefinition> ORB_ATTRIBUTES = Arrays.asList(PERSISTENT_SERVER_ID, GIOP_VERSION, SOCKET_BINDING,
                SSL_SOCKET_BINDING);

        // list that contains initializers attribute definitions
        static final List<AttributeDefinition> INITIALIZERS_ATTRIBUTES = Arrays.asList(SECURITY, TRANSACTIONS);

        // list that contains naming attributes definitions
        static final List<AttributeDefinition> NAMING_ATTRIBUTES = Arrays.asList(ROOT_CONTEXT, EXPORT_CORBALOC);

        // list that contains security attributes definitions
        static final List<AttributeDefinition> SECURITY_ATTRIBUTES = Arrays.asList(SUPPORT_SSL, SECURITY_DOMAIN,
                ADD_COMPONENT_INTERCEPTOR, CLIENT_SUPPORTS, CLIENT_REQUIRES, SERVER_SUPPORTS, SERVER_REQUIRES);

        //list that contains tcp attributes definitions
        static final List<AttributeDefinition> TCP_ATTRIBUTES = Arrays.asList(HIGH_WATER_MARK,
                NUMBER_TO_RECLAIM);

        //list that contains ior sas attributes definitions
        static final List<AttributeDefinition> IOR_SAS_ATTRIBUTES = Arrays.asList(CALLER_PROPAGATION);

        //list that contains ior as attributes definitions
        static final List<AttributeDefinition> IOR_AS_ATTRIBUTES = Arrays.asList(AUTH_METHOD, REALM, REQUIRED);

        //list that contains ior transport config attributes definitions
        static final List<AttributeDefinition> IOR_TRANSPORT_CONFIG_ATTRIBUTES = Arrays.asList(INTEGRITY, CONFIDENTIALITY, TRUST_IN_TARGET,
                TRUST_IN_CLIENT, DETECT_REPLAY, DETECT_MISORDERING);

        static final List<AttributeDefinition> CONFIG_ATTRIBUTES = new ArrayList<>();
        static final List<AttributeDefinition> IOR_ATTRIBUTES = new ArrayList<>();
        static final List<AttributeDefinition> ALL_ATTRIBUTES = new ArrayList<>();

        static {
            CONFIG_ATTRIBUTES.addAll(ORB_ATTRIBUTES);
            CONFIG_ATTRIBUTES.addAll(TCP_ATTRIBUTES);
            CONFIG_ATTRIBUTES.addAll(INITIALIZERS_ATTRIBUTES);
            CONFIG_ATTRIBUTES.addAll(NAMING_ATTRIBUTES);
            CONFIG_ATTRIBUTES.addAll(SECURITY_ATTRIBUTES);
            CONFIG_ATTRIBUTES.add(PROPERTIES);

            IOR_ATTRIBUTES.addAll(IOR_TRANSPORT_CONFIG_ATTRIBUTES);
            IOR_ATTRIBUTES.addAll(IOR_AS_ATTRIBUTES);
            IOR_ATTRIBUTES.addAll(IOR_SAS_ATTRIBUTES);

            ALL_ATTRIBUTES.addAll(CONFIG_ATTRIBUTES);
            ALL_ATTRIBUTES.addAll(IOR_ATTRIBUTES);
        }

        static final IIOPRootDefinition INSTANCE = new IIOPRootDefinition();

        private IIOPRootDefinition() {
            super(PathElement.pathElement("subsystem", "orb"), new NonResolvingResourceDescriptionResolver());
        }

        @Override
        public Collection<AttributeDefinition> getAttributes() {
            return ALL_ATTRIBUTES;
        }

    }

    static class Constants {

        static final String ORB = "orb";
        static final String ORB_GIOP_VERSION = "giop-version";
        static final String ORB_TCP = "tcp";
        static final String TCP_HIGH_WATER_MARK = "high-water-mark";
        static final String TCP_NUMBER_TO_RECLAIM = "number-to-reclaim";
        static final String ORB_SOCKET_BINDING = "socket-binding";
        static final String ORB_SSL_SOCKET_BINDING = "ssl-socket-binding";
        static final String ORB_PERSISTENT_SERVER_ID = "persistent-server-id";
        static final String ORB_INIT = "initializers";
        static final String ORB_INIT_SECURITY = "security";
        static final String ORB_INIT_TRANSACTIONS = "transactions";
        static final String NAMING = "naming";
        static final String NAMING_EXPORT_CORBALOC = "export-corbaloc";
        static final String NAMING_ROOT_CONTEXT = "root-context";
        public static final String NONE = "none";
        static final String SECURITY = "security";
        static final String SECURITY_SUPPORT_SSL = "support-ssl";
        static final String SECURITY_SECURITY_DOMAIN = "security-domain";
        static final String SECURITY_ADD_COMP_VIA_INTERCEPTOR = "add-component-via-interceptor";
        static final String SECURITY_CLIENT_SUPPORTS = "client-supports";
        static final String SECURITY_CLIENT_REQUIRES = "client-requires";
        static final String SECURITY_SERVER_SUPPORTS = "server-supports";
        static final String SECURITY_SERVER_REQUIRES = "server-requires";
        static final String IOR_TRANSPORT_CONFIG = "transport-config";
        static final String IOR_TRANSPORT_INTEGRITY = "integrity";
        static final String IOR_TRANSPORT_CONFIDENTIALITY = "confidentiality";
        static final String IOR_TRANSPORT_TRUST_IN_TARGET = "trust-in-target";
        static final String IOR_TRANSPORT_TRUST_IN_CLIENT = "trust-in-client";
        static final String IOR_TRANSPORT_DETECT_REPLAY = "detect-replay";
        static final String IOR_TRANSPORT_DETECT_MISORDERING = "detect-misordering";
        static final String IOR_AS_CONTEXT = "as-context";
        static final String IOR_AS_CONTEXT_AUTH_METHOD = "auth-method";
        static final String IOR_AS_CONTEXT_REALM = "realm";
        static final String IOR_AS_CONTEXT_REQUIRED = "required";
        static final String IOR_SAS_CONTEXT = "sas-context";
        static final String IOR_SAS_CONTEXT_CALLER_PROPAGATION = "caller-propagation";
        static final String PROPERTIES = "properties";
        static final String PROPERTY = "property";
        static final String ROOT_CONTEXT_INIT_REF = "JBoss/Naming/root";

    }

    private static final String CLASS = "class";
    private static final String MODULE = "module";
    public static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder("properties", true)
            .setAllowExpression(true)
            .build();

    public static final ObjectTypeAttributeDefinition PROCESS_STATE_LISTENER = ObjectTypeAttributeDefinition.Builder.of("process-state-listener",
            SimpleAttributeDefinitionBuilder.create(CLASS, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build(),
            SimpleAttributeDefinitionBuilder.create(MODULE, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build(),
            PROPERTIES)
            .setRestartAllServices()
            .setRequired(false)
            .build();

    public static final ObjectTypeAttributeDefinition STATE_LISTENER = ObjectTypeAttributeDefinition.Builder.of("state-listener",
            SimpleAttributeDefinitionBuilder.create(CLASS, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build(),
            SimpleAttributeDefinitionBuilder.create(MODULE, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build(),
            PROPERTIES)
            .setRestartAllServices()
            .setRequired(false)
            .build();

    public static final AttributeDefinition PROCESS_STATE_LISTENERS = ObjectListAttributeDefinition.Builder.of("listeners", PROCESS_STATE_LISTENER)
            .setRequired(true)
            .setRuntimeServiceNotRequired()
            .build();

    public static final AttributeDefinition UNWRAPPED_LISTENER = ObjectListAttributeDefinition.Builder.of("unwrapped-listener", STATE_LISTENER)
              .setRequired(true)
              .setRuntimeServiceNotRequired()
              .build();

    static class AttributeMappingObjectDefinition {
            static final SimpleAttributeDefinition FROM = new SimpleAttributeDefinitionBuilder("from", ModelType.STRING, false)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition TO = new SimpleAttributeDefinitionBuilder("to", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition FILTER = new SimpleAttributeDefinitionBuilder("filter", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition FILTER_BASE_DN = new SimpleAttributeDefinitionBuilder("filter-base-dn", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition AS_RDN = new SimpleAttributeDefinitionBuilder("as-rdn", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition[] ATTRIBUTES = new SimpleAttributeDefinition[] {FROM, TO, FILTER, FILTER_BASE_DN, AS_RDN};

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder("attribute", ATTRIBUTES)
                    .build();
        }

    static class NewIdentityAttributeObjectDefinition {
            static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final StringListAttributeDefinition VALUE = new StringListAttributeDefinition.Builder("value")
                    .setAllowExpression(true)
                    .setMinSize(1)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .setAttributeMarshaller(AttributeMarshaller.COMMA_STRING_LIST)
                    .setAttributeParser(AttributeParser.COMMA_DELIMITED_STRING_LIST)
                    .build();

            static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {NAME, VALUE};

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder("attribute", ATTRIBUTES)
                    .build();
        }

    static class IdentityMappingObjectDefinition {

            static final SimpleAttributeDefinition RDN_IDENTIFIER = new SimpleAttributeDefinitionBuilder("rdn-identifier", ModelType.STRING, false)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition USE_RECURSIVE_SEARCH = new SimpleAttributeDefinitionBuilder("use-recursive-search", ModelType.BOOLEAN)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(false))
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition SEARCH_BASE_DN = new SimpleAttributeDefinitionBuilder("search-base-dn", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final ObjectListAttributeDefinition ATTRIBUTE_MAPPINGS = new ObjectListAttributeDefinition.Builder("attribute-mapping", AttributeMappingObjectDefinition.OBJECT_DEFINITION)
                    .setRequired(false)
                    .setAttributeGroup("attribute")
                    .setAllowDuplicates(true)
                    .build();

            static final ObjectListAttributeDefinition NEW_IDENTITY_ATTRIBUTES = new ObjectListAttributeDefinition.Builder("new-identity-attributes", NewIdentityAttributeObjectDefinition.OBJECT_DEFINITION)
                    .setRequired(false)
                    .setAllowDuplicates(true)
                    .build();

            static final SimpleAttributeDefinition FILTER_NAME = new SimpleAttributeDefinitionBuilder("filter-name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition ITERATOR_FILTER = new SimpleAttributeDefinitionBuilder("iterator-filter", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final SimpleAttributeDefinition NEW_IDENTITY_PARENT_DN = new SimpleAttributeDefinitionBuilder("new-identity-parent-dn", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

            static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
                    RDN_IDENTIFIER, USE_RECURSIVE_SEARCH, SEARCH_BASE_DN,
                    ATTRIBUTE_MAPPINGS,
                    FILTER_NAME, ITERATOR_FILTER, NEW_IDENTITY_PARENT_DN, NEW_IDENTITY_ATTRIBUTES
            };

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder("identity-mapping",
                        RDN_IDENTIFIER, USE_RECURSIVE_SEARCH, SEARCH_BASE_DN,
                        ATTRIBUTE_MAPPINGS,
                    FILTER_NAME, ITERATOR_FILTER, NEW_IDENTITY_PARENT_DN, NEW_IDENTITY_ATTRIBUTES/*,
                        UserPasswordCredentialMappingObjectDefinition.OBJECT_DEFINITION,
                        OtpCredentialMappingObjectDefinition.OBJECT_DEFINITION*/
                    )
                    .setRequired(true)
                    .build();
        }

    static final PersistentResourceDefinition SERVICE_PROCESS_RESOURCE = new PersistentResourceDefinition(PathElement.pathElement("service"), new NonResolvingResourceDescriptionResolver()) {
        @Override
        public Collection<AttributeDefinition> getAttributes() {
            Collection<AttributeDefinition> attributes = new ArrayList<>();
            attributes.add(PROCESS_STATE_LISTENERS);
            attributes.add(UNWRAPPED_LISTENER);
            attributes.add(OBJECT_DEFINITION);
            return attributes;
        }
    };


    protected static final PathElement PROCESS_SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "process");

    PersistentResourceDefinition PROCESS_SUBSYSTEM_ROOT_INSTANCE = new PersistentResourceDefinition(PROCESS_SUBSYSTEM_PATH, new NonResolvingResourceDescriptionResolver()) {

              @Override
              public Collection<AttributeDefinition> getAttributes() {
                  return Collections.emptyList();
              }

              @Override
              protected List<? extends PersistentResourceDefinition> getChildren() {
                  List<PersistentResourceDefinition> children = new ArrayList<>();
                  children.add(SERVICE_PROCESS_RESOURCE);
                  return children;
              }
          };

    private static class CoreParser extends PersistentResourceXMLParser {

        protected static final String NAMESPACE = "urn:jboss:domain:core:1.0";

        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return PersistentResourceXMLDescription.builder(PROCESS_SUBSYSTEM_PATH, NAMESPACE)
                    .addChild(builder(SERVICE_PROCESS_RESOURCE.getPathElement())
                            .addAttribute(PROCESS_STATE_LISTENERS)
                            .addAttribute(UNWRAPPED_LISTENER, AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
                            .addAttribute(OBJECT_DEFINITION))
                    .build();
        }
    }
}
