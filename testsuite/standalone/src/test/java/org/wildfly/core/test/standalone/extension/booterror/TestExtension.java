/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.extension.booterror;

import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Brian Stansberry
 */
public class TestExtension implements Extension {

    static final String SUBSYSTEM_NAME = "boot-error-test-extension";

    private static final String NAMESPACE = "urn:jboss:domain:boot-error-test-extension:1.0";
    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser(NAMESPACE);

    @Override
    public void initialize(ExtensionContext context) {
        System.out.println("Initializing TestExtension");
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        registration.registerSubsystemModel(new RootResourceDefinition());
        registration.registerXMLElementWriter(PARSER);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, PARSER);
    }

    private static class EmptySubsystemParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

        private final String namespace;

        public EmptySubsystemParser(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            ParseUtils.requireNoAttributes(reader);
            ParseUtils.requireNoContent(reader);
            ModelNode op = new ModelNode();
            op.get("operation").set("add");
            op.get("address").add("subsystem", SUBSYSTEM_NAME);
            value.add(op);
            op = new ModelNode();
            op.get("operation").set("add");
            op.get("address").add("subsystem", SUBSYSTEM_NAME).add("key", "value");
            value.add(op);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext context) throws XMLStreamException {
            context.startSubsystemElement(namespace, false);
            streamWriter.writeEndElement();
        }
    }

}
