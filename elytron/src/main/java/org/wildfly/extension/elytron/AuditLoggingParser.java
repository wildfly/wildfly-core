/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_SECURITY_EVENT_LISTENER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUDIT_LOGGING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_EVENT_LISTENER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_EVENT_LISTENERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SYSLOG_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML Handling for the audit logging resources.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuditLoggingParser {

    private final PersistentResourceXMLDescription fileAuditLogParser = builder(PathElement.pathElement(FILE_AUDIT_LOG), null)
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT)
            .build();

    private final PersistentResourceXMLDescription syslogAuditLogParser = builder(PathElement.pathElement(SYSLOG_AUDIT_LOG), null)
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.SERVER_ADDRESS, AuditResourceDefinitions.PORT, AuditResourceDefinitions.TRANSPORT, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.HOST_NAME)
            .build();

    void readAuditLogging(ModelNode parentAddressNode, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            PathAddress parentAddress = PathAddress.pathAddress(parentAddressNode);
            switch (localName) {
                case AGGREGATE_SECURITY_EVENT_LISTENER:
                    readAggregateSecurityEventListener(parentAddress.toModelNode(), reader, operations);
                    break;
                case FILE_AUDIT_LOG:
                    fileAuditLogParser.parse(reader, parentAddress, operations);
                    break;
                case SYSLOG_AUDIT_LOG:
                    syslogAuditLogParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readAggregateSecurityEventListener(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addEventListener = new ModelNode();
        addEventListener.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addEventListener.get(OP_ADDR).set(parentAddress).add(AGGREGATE_SECURITY_EVENT_LISTENER, name);

        operations.add(addEventListener);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SECURITY_EVENT_LISTENER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String listenerName = reader.getAttributeValue(0);


            AuditResourceDefinitions.REFERENCES.parseAndAddParameterElement(listenerName, addEventListener, reader);

            requireNoContent(reader);
        }
    }

    private void writeAggregateSecurityEventListener(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_SECURITY_EVENT_LISTENER)) {
            ModelNode aggregateSecurityEventListener = subsystem.require(AGGREGATE_SECURITY_EVENT_LISTENER);
            for (String name : aggregateSecurityEventListener.keys()) {
                ModelNode aggregateListener = aggregateSecurityEventListener.require(name);
                writer.writeStartElement(AGGREGATE_SECURITY_EVENT_LISTENER);
                writer.writeAttribute(NAME, name);

                List<ModelNode> listenerReferences = aggregateListener.get(SECURITY_EVENT_LISTENERS).asList();
                for (ModelNode currentReference : listenerReferences) {
                    writer.writeStartElement(SECURITY_EVENT_LISTENER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }
        }
    }

    void writeAuditLogging(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (shouldWrite(subsystem) == false) {
            return;
        }

        writer.writeStartElement(AUDIT_LOGGING);

        writeAggregateSecurityEventListener(subsystem, writer);
        fileAuditLogParser.persist(writer, subsystem);
        syslogAuditLogParser.persist(writer, subsystem);

        writer.writeEndElement();
    }

    private boolean shouldWrite(ModelNode subsystem) {
        return subsystem.hasDefined(AGGREGATE_SECURITY_EVENT_LISTENER) || subsystem.hasDefined(FILE_AUDIT_LOG) || subsystem.hasDefined(SYSLOG_AUDIT_LOG);
    }

}
