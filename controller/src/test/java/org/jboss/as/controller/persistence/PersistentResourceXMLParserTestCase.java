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
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import com.sun.corba.se.spi.activation.Server;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 * @author Tomaz Cerar
 */
public class PersistentResourceXMLParserTestCase {


    static String readResource(final String name) throws IOException {

        URL configURL = PersistentResourceXMLParserTestCase.class.getResource(name);
        Assert.assertNotNull(name + " url is null", configURL);

        StringWriter writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
        }
        return writer.toString();
    }

    public static XMLExtendedStreamWriter createXMLStreamWriter(XMLStreamWriter writer) throws Exception {
        // Use reflection to access package protected class FormattingXMLStreamWriter
        // TODO: at some point the staxmapper API could be enhanced to make this unnecessary
        Class clazz = Class.forName("org.jboss.staxmapper.FormattingXMLStreamWriter");
        Object[] args = new Object[1];
        args[0] = writer;
        Constructor ctr = clazz.getConstructor(XMLStreamWriter.class);
        ctr.setAccessible(true);
        return (XMLExtendedStreamWriter) ctr.newInstance(args);
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

        Assert.assertEquals(2, operations.size());
        ModelNode subsystem = opsToModel(operations);

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
            subsystem.get(address.getLastElement().getKeyValuePair()).set(resource);
        }

        //System.out.println("subsystem = " + subsystem);
        return subsystem;
    }

    public static String normalizeXML(String xml) throws Exception {
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
                .setAllowNull(true)
                .setElementValidator(new StringLengthValidator(1))
                .setAttributeParser(AttributeParser.COMMA_DELIMITED_STRING_LIST)
                .setAttributeMarshaller(AttributeMarshaller.COMMA_STRING_LIST)
                .build();

        static final SimpleAttributeDefinition BUFFER_SIZE = new SimpleAttributeDefinitionBuilder("buffer-size", ModelType.INT)
                .setAllowNull(true)
                .setDefaultValue(new ModelNode(1024))
                .setAllowExpression(true)
                .build();
        static final SimpleAttributeDefinition BUFFERS_PER_REGION = new SimpleAttributeDefinitionBuilder("buffers-per-region", ModelType.INT)
                .setAllowNull(true)
                .setDefaultValue(new ModelNode(1024))
                .setAllowExpression(true)
                .build();
        static final SimpleAttributeDefinition MAX_REGIONS = new SimpleAttributeDefinitionBuilder("max-regions", ModelType.INT)
                .setAllowNull(true)
                .setAllowExpression(true)
                .setDefaultValue(new ModelNode(10))
                .build();

        public static final SimpleAttributeDefinition STATISTICS_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.STATISTICS_ENABLED, ModelType.BOOLEAN)
                .setAttributeGroup("statistics")
                .setXmlName("enabled")
                .setDefaultValue(new ModelNode(false))
                .setAllowNull(true)
                .setAllowExpression(true)
                .build();
        public static final SimpleAttributeDefinition SECURITY_ENABLED = new SimpleAttributeDefinitionBuilder("security-enabled", ModelType.BOOLEAN)
                .setAttributeGroup("security")
                .setXmlName("enabled")
                .setDefaultValue(new ModelNode(true))
                .setAllowNull(true)
                .setAllowExpression(true)
                .setRestartAllServices()
                .build();



        protected static final PersistentResourceDefinition RESOURCE_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("resource"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(clusterAttr1);
                attributes.add(clusterAttr1);
                attributes.add(securityAttr1);
                attributes.add(securityAttr2);
                attributes.add(nonGroupAttr1);
                attributes.add(ALIAS);
                return attributes;
            }
        };

        protected static final PersistentResourceDefinition BUFFER_CACHE_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("buffer-cache"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(BUFFER_SIZE);
                attributes.add(BUFFERS_PER_REGION);
                attributes.add(MAX_REGIONS);
                attributes.add(ALIAS);
                return attributes;
            }
        };


        protected static final PersistentResourceDefinition SERVER_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("server"), new NonResolvingResourceDescriptionResolver()) {
            @Override
            public Collection<AttributeDefinition> getAttributes() {
                Collection<AttributeDefinition> attributes = new ArrayList<>();
                attributes.add(STATISTICS_ENABLED);
                attributes.add(SECURITY_ENABLED);
                return attributes;
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
                return children;
            }
        };


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE, NAMESPACE)
                    .addChild(
                            builder(RESOURCE_INSTANCE)
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
                                            ALIAS
                                    )
                    )
                    .addChild(
                            builder(BUFFER_CACHE_INSTANCE)
                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, MAX_REGIONS)
                                    .addAttribute(ALIAS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
                    )
                    .build();
        }
    }

    static class AttributeGroupParser extends MyParser {


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE, NAMESPACE)
                    .addChild(
                            builder(RESOURCE_INSTANCE)
                                    .addAttributes(
                                            // cluster group
                                            clusterAttr1,
                                            clusterAttr2,
                                            // security group
                                            securityAttr1,
                                            securityAttr2,
                                            //no group element
                                            nonGroupAttr1,
                                            ALIAS
                                    )
                    )
                    .addChild(
                            builder(BUFFER_CACHE_INSTANCE)
                                    .addAttributes(BUFFER_SIZE, BUFFERS_PER_REGION, MAX_REGIONS)
                                    .addAttribute(ALIAS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
                    )
                    .build();
        }
    }

    static class ServerParser extends MyParser {


        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(SUBSYSTEM_ROOT_INSTANCE, NAMESPACE)
                    .addChild(
                            builder(SERVER_INSTANCE)
                                    .addAttributes(SECURITY_ENABLED, STATISTICS_ENABLED)
                    )
                    .build();
        }
    }
}
