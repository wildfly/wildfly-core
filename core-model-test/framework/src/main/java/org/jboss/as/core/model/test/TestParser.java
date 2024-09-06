/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ManagementSchemas;
import org.jboss.as.controller.parsing.ManagementXmlSchema;
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

    private static int getModelMajorVersion() {
        try {
            Class<?> versionClass = Class.forName("org.jboss.as.version.Version");
            Field majorVersionField = versionClass.getDeclaredField("MANAGEMENT_MAJOR_VERSION");

            return  majorVersionField.getInt(null);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to obtain the major version of the model.", e);
        }
    }

    private static TestParser createLegacy(ExtensionRegistry registry, XMLMapper xmlMapper, TestModelType type) throws Exception {

        TestParser testParser;
        String root;

        // Common Classes
        Class<?> moduleLoaderClass = Class.forName("org.jboss.modules.ModuleLoader");
        Class<?> executorServiceClass = ExecutorService.class;
        Class<?> extensionRegistryClass = Class.forName("org.jboss.as.controller.extension.ExtensionRegistry");

        Object xmlParser = null;
        if (type == TestModelType.STANDALONE) {
            Class<?> standaloneXmlClass = Class.forName("org.jboss.as.server.parsing.StandaloneXml");

            Constructor<?> standaloneXmlConstructor = standaloneXmlClass.getConstructor(moduleLoaderClass,
                executorServiceClass, extensionRegistryClass);

            xmlParser = standaloneXmlConstructor.newInstance(null, Executors.newCachedThreadPool(), registry);

            root = "server";
        } else if (type == TestModelType.DOMAIN) {
            Class<?> domainXmlClass = Class.forName("org.jboss.as.host.controller.parsing.DomainXml");

            Constructor<?> domainXmlConstructor = domainXmlClass.getConstructor(moduleLoaderClass,
                executorServiceClass, extensionRegistryClass);

            xmlParser = domainXmlConstructor.newInstance(null, Executors.newCachedThreadPool(), registry);

            root = "domain";
        } else if (type == TestModelType.HOST) {
            Class<?> hostXmlClass = Class.forName("org.jboss.as.host.controller.parsing.HostXml");
            Class<?> runningModeClass = Class.forName("org.jboss.as.controller.RunningMode");

            Constructor<?> hostXmlConstructor = hostXmlClass.getConstructor(String.class, runningModeClass, boolean.class,
                moduleLoaderClass, executorServiceClass, extensionRegistryClass);

            xmlParser = hostXmlConstructor.newInstance("primary", RunningMode.NORMAL, false, null, Executors.newCachedThreadPool(), registry);

            root = "host";
        } else {
            throw new IllegalArgumentException("Unknown type " + type);
        }

        testParser = new TestParser(type, (XMLElementReader<List<ModelNode>>) xmlParser,
                (XMLElementWriter<ModelMarshallingContext>) xmlParser);


        Class<?> namespaceClass = Class.forName("org.jboss.as.controller.parsing.Namespace");
        Field allNamespacesField = namespaceClass.getDeclaredField("ALL_NAMESPACES");
        Object[] allNamespaces = (Object[]) allNamespacesField.get(null);
        Method getUriStringMethod = namespaceClass.getMethod("getUriString");

        for (Object ns : allNamespaces) {

            xmlMapper.registerRootElement(new QName(getUriStringMethod.invoke(ns).toString(), root), testParser);
        }

        return testParser;
    }

    private static TestParser createFrom27(Stability stability, ExtensionRegistry registry, XMLMapper xmlMapper, TestModelType type) {
        ManagementSchemas schemas;
        if (type == TestModelType.STANDALONE) {
            schemas = new StandaloneXmlSchemas(stability, null, Executors.newCachedThreadPool(), registry);
        } else if (type == TestModelType.DOMAIN) {
            schemas = new DomainXmlSchemas(stability, null, Executors.newCachedThreadPool(), registry);
        } else if (type == TestModelType.HOST) {
            schemas = new HostXmlSchemas(stability, "primary", RunningMode.NORMAL, false, null, Executors.newCachedThreadPool(), registry);
        } else {
            throw new IllegalArgumentException("Unknown type " + type);
        }

        ManagementXmlSchema schema = schemas.getCurrent();
        TestParser testParser = new TestParser(type, schema, schema);

        xmlMapper.registerRootElement(schema.getQualifiedName(), testParser);

        for (ManagementXmlSchema additional : schemas.getAdditional()) {
            xmlMapper.registerRootElement(additional.getQualifiedName(), testParser);
        }

        return testParser;
    }


    public static TestParser create(ExtensionRegistry registry, XMLMapper xmlMapper, TestModelType type) {
        return create(Stability.DEFAULT, registry, xmlMapper, type);
    }

    public static TestParser create(Stability stability, ExtensionRegistry registry, XMLMapper xmlMapper, TestModelType type) {
        if (getModelMajorVersion() < 27) {
            try {
                return createLegacy(registry, xmlMapper, type);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create the parser", e);
            }
        }
        return createFrom27(stability, registry, xmlMapper, type);
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
