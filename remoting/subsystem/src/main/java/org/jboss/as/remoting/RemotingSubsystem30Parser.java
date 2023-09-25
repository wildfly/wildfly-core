/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * An extension of the parser for version 3.0 of the schema.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RemotingSubsystem30Parser extends RemotingSubsystem20Parser {

    @Override
    void parseConnector(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        parseConnector(false, reader, address, list);
    }

    @Override
    void parseHttpConnector(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        parseConnector(true, reader, address, list);
    }

    void parseConnector(boolean http, XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
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

}
