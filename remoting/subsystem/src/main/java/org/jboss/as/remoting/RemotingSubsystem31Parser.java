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

package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * An extension of the parser for version 3.1 of the schema.
 *
 *  @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class RemotingSubsystem31Parser extends RemotingSubsystem30Parser {

    static final RemotingSubsystem31Parser INSTANCE = new RemotingSubsystem31Parser();

    void parseOutboundConnections(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> operations) throws XMLStreamException {
        // Handle nested elements.
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case REMOTE_OUTBOUND_CONNECTION: {
                    this.parseRemoteOutboundConnection(reader, address, operations);
                    break;
                }
                case LOCAL_OUTBOUND_CONNECTION: {
                    this.parseLocalOutboundConnection(reader, address, operations);
                    break;
                }
                case OUTBOUND_CONNECTION: {
                    this.parseOutboundConnection(reader, address, operations);
                    break;
                }
                case REMOTE_OUTBOUND_CONNECTION_GROUP:
                    this.parseConnectionGroup(reader,address, operations);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    void parseConnectionGroup(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final List<ModelNode> operations) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        String name = null;
        final ModelNode addOperation = Util.createAddOperation();
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case OUTBOUND_SOCKET_BINDING_REFS: {
                    RemoteOutboundConnectionGroupResourceDefinition.OUTBOUND_SOCKET_BINDINGS_REFS.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                case USERNAME: {
                    RemoteOutboundConnectionGroupResourceDefinition.USERNAME.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                case SECURITY_REALM: {
                    RemoteOutboundConnectionGroupResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                case PROTOCOL: {
                    RemoteOutboundConnectionGroupResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, addOperation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        final PathAddress address = PathAddress.pathAddress(PathAddress.pathAddress(parentAddress), PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION_GROUP, name));
        final List<ModelNode> propertyOps = new LinkedList<>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTIES: {
                    parseProperties(reader, address.toModelNode(), propertyOps);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        addOperation.get(OP_ADDR).set(address.toModelNode());
        operations.add(addOperation);
        operations.addAll(propertyOps);
    }
}
