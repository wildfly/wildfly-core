/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.remoting.CommonAttributes.AUTHENTICATION_PROVIDER;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR_REF;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.SECURITY_REALM;
import static org.jboss.as.remoting.CommonAttributes.SOCKET_BINDING;

import java.util.EnumSet;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for version 4.0 of the subsystem schema.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
// N.B. This is final to discourage anyone extending this in the future
// Parsing logic for new schema versions are to be done in RemotingSubsystemSchema.
final class RemotingSubsystem40Parser extends RemotingSubsystem30Parser {

    @Override
    void parseConnector(boolean http, XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list)
            throws XMLStreamException {
        final ModelNode connector = new ModelNode();
        connector.get(OP).set(ADD);

        String name = null;
        String securityRealm = null;

        String socketBinding = null; // Only for NON-HTTP
        String connectorRef = null;  // Only for HTTP

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, http ? Attribute.CONNECTOR_REF : Attribute.SOCKET_BINDING);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case SASL_AUTHENTICATION_FACTORY: {
                    ConnectorCommon.SASL_AUTHENTICATION_FACTORY.parseAndSetParameter(value, connector, reader);
                    break;
                }
                case SASL_PROTOCOL: {
                    ConnectorCommon.SASL_PROTOCOL.parseAndSetParameter(value, connector, reader);
                    break;
                }
                case SECURITY_REALM: {
                    securityRealm = value;
                    break;
                }
                case SERVER_NAME: {
                    ConnectorCommon.SERVER_NAME.parseAndSetParameter(value, connector, reader);
                    break;
                }
                case SOCKET_BINDING: {
                    if (http) {
                        throw unexpectedAttribute(reader, i);
                    }
                    socketBinding = value;
                    break;
                }
                case SSL_CONTEXT: {
                    if (http) {
                        throw unexpectedAttribute(reader, i);
                    }
                    ConnectorResource.SSL_CONTEXT.parseAndSetParameter(value, connector, reader);
                    break;
                }
                case CONNECTOR_REF: {
                    if (http == false) {
                        throw unexpectedAttribute(reader, i);
                    }
                    connectorRef = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        assert name != null;
        if (http) {
            assert connectorRef != null;
        } else {
            assert socketBinding != null;
        }

        connector.get(OP_ADDR).set(address).add(http ? HTTP_CONNECTOR : CONNECTOR, name);
        if (http) {
            connector.get(CONNECTOR_REF).set(connectorRef);
        } else {
            connector.get(SOCKET_BINDING).set(socketBinding);
        }
        if (securityRealm != null) {
            connector.get(SECURITY_REALM).set(securityRealm);
        }
        list.add(connector);

        // Handle nested elements.
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case SASL: {
                    parseSaslElement(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case PROPERTIES: {
                    parseProperties(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case AUTHENTICATION_PROVIDER: {
                    connector.get(AUTHENTICATION_PROVIDER).set(readStringAttributeElement(reader, "name"));
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    @Override
    void parseRemoteOutboundConnection(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> operations) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.OUTBOUND_SOCKET_BINDING_REF);
        final int count = reader.getAttributeCount();
        String name = null;
        final ModelNode addOperation = Util.createAddOperation();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case OUTBOUND_SOCKET_BINDING_REF: {
                    RemoteOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                case USERNAME: {
                    // Ignore
                    break;
                }
                case SECURITY_REALM: {
                    // Ignore
                    break;
                }
                case PROTOCOL: {
                    // Ignore
                    break;
                }
                case AUTHENTICATION_CONTEXT: {
                    RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        final PathAddress address = PathAddress.pathAddress(PathAddress.pathAddress(parentAddress), PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION, name));
        addOperation.get(OP_ADDR).set(address.toModelNode());
        // create add operation add it to the list of operations
        operations.add(addOperation);
        // parse the nested elements
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case PROPERTIES: {
                    parseProperties(reader, address.toModelNode(), operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

    }


}
