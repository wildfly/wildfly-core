/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ManagementXmlSchema;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.host.controller.parsing.DomainXmlSchemas;
import org.jboss.as.host.controller.parsing.HostXmlSchemas;
import org.jboss.as.model.test.ModelTestParser;
import org.jboss.as.server.parsing.StandaloneXmlSchemas;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestParser implements ModelTestParser {
    private final TestModelType type;
    private final XMLElementReader<List<ModelNode>> reader;
    private final XMLElementWriter<ModelMarshallingContext> writer;
    private volatile List<ModelWriteSanitizer> writeSanitizers;

    public TestParser(TestModelType type, XMLElementReader<List<ModelNode>> reader, XMLElementWriter<ModelMarshallingContext> writer) {
        this.type = type;
        this.reader = reader;
        this.writer = writer;
    }

    public static TestParser create(ExtensionRegistry registry, XMLMapper xmlMapper, TestModelType type) {
        TestParser testParser;
        String root;
        Stability stability = Stability.DEFAULT;
        if (type == TestModelType.STANDALONE) {

            StandaloneXmlSchemas xmlSchemas = new StandaloneXmlSchemas(stability, null, Executors.newCachedThreadPool(), registry);
            ManagementXmlSchema schema = xmlSchemas.getCurrent();
            testParser = new TestParser(type, schema, schema);
            root = "server";
        } else if (type == TestModelType.DOMAIN) {
            DomainXmlSchemas xmlSchemas = new DomainXmlSchemas(stability, null, Executors.newCachedThreadPool(), registry);
            ManagementXmlSchema schema = xmlSchemas.getCurrent();
            testParser = new TestParser(type, schema, schema);
            root = "domain";
        } else if (type == TestModelType.HOST) {
            HostXmlSchemas xmlSchemas = new HostXmlSchemas(stability, "primary", RunningMode.NORMAL, false, null, Executors.newCachedThreadPool(), registry);
            ManagementXmlSchema schema = xmlSchemas.getCurrent();
            testParser = new TestParser(type, schema, schema);
            root = "host";
        } else {
            throw new IllegalArgumentException("Unknown type " + type);
        }


        try {
            for (Namespace ns : Namespace.ALL_NAMESPACES) {
                xmlMapper.registerRootElement(new QName(ns.getUriString(), root), testParser);
            }
        } catch (NoSuchFieldError e) {
            //7.1.2 does not have the ALL_NAMESPACES field
            xmlMapper.registerRootElement(new QName(Namespace.DOMAIN_1_0.getUriString(), root), testParser);
            xmlMapper.registerRootElement(new QName(Namespace.DOMAIN_1_1.getUriString(), root), testParser);
            xmlMapper.registerRootElement(new QName(Namespace.DOMAIN_1_2.getUriString(), root), testParser);
        }
        return testParser;
    }

    void addModelWriteSanitizer(ModelWriteSanitizer writeSanitizer) {
        if (writeSanitizer == null) {
            return;
        }
        if (writeSanitizers == null) {
            writeSanitizers = new ArrayList<ModelWriteSanitizer>();
        }
        writeSanitizers.add(writeSanitizer);
    }



    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
        this.reader.readElement(reader, value);
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter streamWriter, ModelMarshallingContext context) throws XMLStreamException {
        this.writer.writeContent(streamWriter, sanitizeContext(wrapPossibleHost(context)));
    }

    private ModelMarshallingContext wrapPossibleHost(final ModelMarshallingContext context) {

        if (type == TestModelType.HOST) {
            return new ModelMarshallingContext() {

                @Override
                public XMLElementWriter<SubsystemMarshallingContext> getSubsystemWriter(String subsystemName) {
                    return context.getSubsystemWriter(subsystemName);
                }

                @Override
                public ModelNode getModelNode() {
                    return context.getModelNode().get(ModelDescriptionConstants.HOST, "primary");
                }
            };
        }

        return context;
    }
    private ModelMarshallingContext sanitizeContext(final ModelMarshallingContext context) {
        if (writeSanitizers == null) {
            return context;
        }

        ModelNode model = context.getModelNode();
        for (ModelWriteSanitizer sanitizer : writeSanitizers) {
            model = sanitizer.sanitize(model);
        }

        final ModelNode theModel = model;
        return new ModelMarshallingContext() {

            @Override
            public XMLElementWriter<SubsystemMarshallingContext> getSubsystemWriter(String subsystemName) {
                return context.getSubsystemWriter(subsystemName);
            }

            @Override
            public ModelNode getModelNode() {
                return theModel;
            }
        };
    }
}
