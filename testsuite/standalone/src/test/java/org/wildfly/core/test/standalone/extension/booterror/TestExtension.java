/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
