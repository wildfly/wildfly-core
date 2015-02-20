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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
public class PersistentResourceXMLParserTestCase {

    @Test
    public void testParse() throws XMLStreamException {

        MyParser parser = new MyParser();

        String xml = "<subsystem xmlns=\"" + parser.NAMESPACE + "\">\n" +
                "  <resource name=\"foo\">\n" +
                "    <cluster attr1='bar'\n" +
                "             attr2='baz' />\n" +
                "    <security my-attr1='alice'\n" +
                "             my-attr2='bob' />\n" +
                "   </resource>\n" +
                "  <resource name=\"foo2\">\n" +
                "    <cluster attr1='bar2'\n" +
                "             attr2='baz2' />\n" +
                "   </resource>\n" +
                "</subsystem>";
        System.out.println(xml);
        StringReader strReader = new StringReader(xml);

        XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(parser.NAMESPACE, "subsystem"), parser);

        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StreamSource(strReader));
        List<ModelNode> operations = new ArrayList<ModelNode>();
        mapper.parseDocument(operations, reader);

        System.out.println(operations);

        ModelNode subsystem = createSubystemModelNode(operations);

        assertEquals("bar", subsystem.get("resource", "foo", "cluster-attr1").asString());
        assertEquals("baz", subsystem.get("resource", "foo", "cluster-attr2").asString());
        assertEquals("alice", subsystem.get("resource", "foo", "security-my-attr1").asString());
        assertEquals("bob", subsystem.get("resource", "foo", "security-my-attr2").asString());
        assertEquals("bar2", subsystem.get("resource", "foo2", "cluster-attr1").asString());
        assertEquals("baz2", subsystem.get("resource", "foo2", "cluster-attr2").asString());

        StringWriter stringWriter = new StringWriter();
        XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter);
        mapper.deparseDocument(parser, subsystem, xmlStreamWriter);
        String out = stringWriter.toString();

        System.out.println("out = " + out);
    }

    private ModelNode createSubystemModelNode(List<ModelNode> operations) {
        ModelNode subsystem = new ModelNode();
        subsystem.set(operations.get(0));
        subsystem.remove("address");
        subsystem.remove("operation");

        ModelNode addResourceOp = operations.get(1);
        ModelNode resource = addResourceOp.clone();
        resource.remove("operation");
        resource.remove("address");

        subsystem.get("resource", "foo").set(resource);

        ModelNode addResourceOp1 = operations.get(2);
        ModelNode resource2 = addResourceOp1.clone();
        resource2.remove("operation");
        resource2.remove("address");

        subsystem.get("resource", "foo2").set(resource2);

        System.out.println("subsystem = " + subsystem);
        return subsystem;
    }

    private static class MyParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<ModelNode> {

        protected static final String NAMESPACE = "urn:jboss:domain:my-namespace:1.0";
        protected static final MyParser INSTANCE = new MyParser();

        private static final PersistentResourceXMLDescription xmlDescription;

        protected  static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "foo");

        protected  static final PersistentResourceDefinition RESOURCE_INSTANCE;

        static {
            final AttributeDefinition clusterAttr1 = create("cluster-attr1", ModelType.STRING)
                    .setAttributeGroup("cluster")
                    .setXmlName("attr1")
                    .build();
            final AttributeDefinition clusterAttr2 = create("cluster-attr2", ModelType.STRING)
                    .setAttributeGroup("cluster")
                    .setXmlName("attr2")
                    .build();

            final AttributeDefinition securityAttr1 = create("security-my-attr1", ModelType.STRING)
                    .setAttributeGroup("security")
                    .setXmlName("my-attr1")
                    .build();
            final AttributeDefinition securityAttr2 = create("security-my-attr2", ModelType.STRING)
                    .setAttributeGroup("security")
                    .setXmlName("my-attr2")
                    .build();


            RESOURCE_INSTANCE = new PersistentResourceDefinition(PathElement.pathElement("resource"), new NonResolvingResourceDescriptionResolver()) {
                @Override
                public Collection<AttributeDefinition> getAttributes() {
                    Collection<AttributeDefinition> attributes = new ArrayList<>();
                    attributes.add(clusterAttr1);
                    attributes.add(clusterAttr1);
                    attributes.add(securityAttr1);
                    attributes.add(securityAttr2);
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
                    return children;
                }
            };

            xmlDescription = builder(SUBSYSTEM_ROOT_INSTANCE)
                    .addChild(
                            builder(RESOURCE_INSTANCE)
                                    .addAttributes(
                                            // cluster group
                                            clusterAttr1,
                                            clusterAttr2,
                                            // security group
                                            securityAttr1,
                                            securityAttr2
                                            )
                    )
                    .build();
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            xmlDescription.parse(reader, PathAddress.EMPTY_ADDRESS, list);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, ModelNode modelNode) throws XMLStreamException {
            ModelNode model = new ModelNode();
            model.get(SUBSYSTEM_PATH.getKeyValuePair()).set(modelNode);
            xmlDescription.persist(writer, model, NAMESPACE);
        }
    }
}
