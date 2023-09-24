/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_MAPPINGS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTINATION_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.resource.AbstractSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.LocalDestinationOutboundSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.OutboundSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.RemoteDestinationOutboundSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.SocketBindingGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to socket bindings.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class SocketBindingsXml {

    protected SocketBindingsXml() {

    }
    void parseSocketBindingGroupRef(final XMLExtendedStreamReader reader, final ModelNode addOperation,
            final SimpleAttributeDefinition socketBindingGroup, final SimpleAttributeDefinition portOffset,
            final SimpleAttributeDefinition defaultInterface) throws XMLStreamException {
        // Handle attributes
        boolean gotRef = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case REF: {
                        socketBindingGroup.parseAndSetParameter(value, addOperation, reader);
                        gotRef = true;
                        break;
                    }
                    case PORT_OFFSET: {
                        portOffset.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    case DEFAULT_INTERFACE: {
                        if (defaultInterface == null) {
                            throw unexpectedAttribute(reader, i);
                        }
                        defaultInterface.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
        if (!gotRef) {
            throw missingRequired(reader, Collections.singleton(Attribute.REF));
        }

        // Handle elements
        requireNoContent(reader);
    }

    String parseSocketBinding(final XMLExtendedStreamReader reader, final Set<String> interfaces,
            final ModelNode address, final List<ModelNode> updates) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        String name = null;

        final ModelNode binding = new ModelNode();
        binding.get(OP_ADDR); // undefined until we parse name
        binding.get(OP).set(ADD);

        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case NAME: {
                        name = value;
                        binding.get(OP_ADDR).set(address).add(SOCKET_BINDING, name);
                        break;
                    }
                    case INTERFACE: {
                        AbstractSocketBindingResourceDefinition.INTERFACE.parseAndSetParameter(value, binding, reader);
                        if (binding.get(AbstractSocketBindingResourceDefinition.INTERFACE.getName()).getType() != ModelType.EXPRESSION
                                && !interfaces.contains(value)) {
                            throw ControllerLogger.ROOT_LOGGER.unknownInterface(value, attribute.getLocalName(),
                                    Element.INTERFACES.getLocalName(), reader.getLocation());
                        }
                        binding.get(INTERFACE).set(value);
                        break;
                    }
                    case PORT: {
                        AbstractSocketBindingResourceDefinition.PORT.parseAndSetParameter(value, binding, reader);
                        break;
                    }
                    case FIXED_PORT: {
                        AbstractSocketBindingResourceDefinition.FIXED_PORT.parseAndSetParameter(value, binding, reader);
                        break;
                    }
                    case MULTICAST_ADDRESS: {
                        AbstractSocketBindingResourceDefinition.MULTICAST_ADDRESS.parseAndSetParameter(value, binding, reader);
                        break;
                    }
                    case MULTICAST_PORT: {
                        AbstractSocketBindingResourceDefinition.MULTICAST_PORT.parseAndSetParameter(value, binding, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case CLIENT_MAPPING:
                    binding.get(CLIENT_MAPPINGS).add(parseClientMapping(reader));
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        updates.add(binding);
        return name;
    }

    private ModelNode parseClientMapping(XMLExtendedStreamReader reader) throws XMLStreamException {
        final ModelNode mapping = new ModelNode();

        boolean hasDestinationAddress = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case SOURCE_NETWORK:
                    AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_SOURCE_NETWORK.parseAndSetParameter(value, mapping,
                            reader);
                    break;
                case DESTINATION_ADDRESS:
                    AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_ADDRESS.parseAndSetParameter(value,
                            mapping, reader);
                    hasDestinationAddress = true;
                    break;
                case DESTINATION_PORT: {
                    AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_PORT.parseAndSetParameter(value,
                            mapping, reader);
                    break;
                }
            }
        }
        if (!hasDestinationAddress) {
            throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(DESTINATION_ADDRESS),
                    reader.getLocation());
        }

        requireNoContent(reader);

        return mapping;
    }

    String parseOutboundSocketBinding(final XMLExtendedStreamReader reader, final Set<String> interfaces,
            final ModelNode address, final List<ModelNode> updates) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        String outboundSocketBindingName = null;

        final ModelNode outboundSocketBindingAddOperation = new ModelNode();
        outboundSocketBindingAddOperation.get(OP).set(ADD); // address for this ADD operation will be set later, once the
                                                            // local-destination or remote-destination is parsed

        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case NAME: {
                        outboundSocketBindingName = value;
                        break;
                    }
                    case SOURCE_INTERFACE: {
                        OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        if (!interfaces.contains(value)
                                && outboundSocketBindingAddOperation.get(
                                        OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.getName()).getType() != ModelType.EXPRESSION) {
                            throw ControllerLogger.ROOT_LOGGER.unknownValueForElement(
                                    Attribute.SOURCE_INTERFACE.getLocalName(), value, Element.INTERFACE.getLocalName(),
                                    Element.INTERFACES.getLocalName(), reader.getLocation());
                        }
                        break;
                    }
                    case SOURCE_PORT: {
                        OutboundSocketBindingResourceDefinition.SOURCE_PORT.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        break;
                    }
                    case FIXED_SOURCE_PORT: {
                        OutboundSocketBindingResourceDefinition.FIXED_SOURCE_PORT.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        // Handle elements
        boolean mutuallyExclusiveElementAlreadyFound = false;
        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            switch (Element.forName(reader.getLocalName())) {
                case LOCAL_DESTINATION: {
                    if (mutuallyExclusiveElementAlreadyFound) {
                        throw ControllerLogger.ROOT_LOGGER.invalidOutboundSocketBinding(outboundSocketBindingName,
                                Element.LOCAL_DESTINATION.getLocalName(), Element.REMOTE_DESTINATION.getLocalName(),
                                reader.getLocation());
                    } else {
                        mutuallyExclusiveElementAlreadyFound = true;
                    }
                    // parse the local destination outbound socket binding
                    this.parseLocalDestinationOutboundSocketBinding(reader, outboundSocketBindingAddOperation);
                    // set the address of the add operation
                    // /socket-binding-group=<groupname>/local-destination-outbound-socket-binding=<outboundSocketBindingName>
                    final ModelNode addr = address.clone().add(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING,
                            outboundSocketBindingName);
                    outboundSocketBindingAddOperation.get(OP_ADDR).set(addr);
                    break;
                }
                case REMOTE_DESTINATION: {
                    if (mutuallyExclusiveElementAlreadyFound) {
                        throw ControllerLogger.ROOT_LOGGER.invalidOutboundSocketBinding(outboundSocketBindingName,
                                Element.LOCAL_DESTINATION.getLocalName(), Element.REMOTE_DESTINATION.getLocalName(),
                                reader.getLocation());
                    } else {
                        mutuallyExclusiveElementAlreadyFound = true;
                    }
                    // parse the remote destination outbound socket binding
                    this.parseRemoteDestinationOutboundSocketBinding(reader, outboundSocketBindingAddOperation);
                    // /socket-binding-group=<groupname>/remote-destination-outbound-socket-binding=<outboundSocketBindingName>
                    final ModelNode addr = address.clone().add(REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING,
                            outboundSocketBindingName);
                    outboundSocketBindingAddOperation.get(OP_ADDR).set(addr);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        // add the "add" operations to the updates
        updates.add(outboundSocketBindingAddOperation);
        return outboundSocketBindingName;
    }

    private void parseLocalDestinationOutboundSocketBinding(final XMLExtendedStreamReader reader,
            final ModelNode outboundSocketBindingAddOperation) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.SOCKET_BINDING_REF);

        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case SOCKET_BINDING_REF: {
                        LocalDestinationOutboundSocketBindingResourceDefinition.SOCKET_BINDING_REF.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        // Handle elements
        requireNoContent(reader);
    }

    private void parseRemoteDestinationOutboundSocketBinding(final XMLExtendedStreamReader reader,
            final ModelNode outboundSocketBindingAddOperation) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.HOST, Attribute.PORT);

        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case HOST: {
                        RemoteDestinationOutboundSocketBindingResourceDefinition.HOST.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        break;
                    }
                    case PORT: {
                        RemoteDestinationOutboundSocketBindingResourceDefinition.PORT.parseAndSetParameter(value,
                                outboundSocketBindingAddOperation, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        // Handle elements
        requireNoContent(reader);
    }

    void writeSocketBindingGroup(XMLExtendedStreamWriter writer, ModelNode bindingGroup, String name)
            throws XMLStreamException {

        writer.writeStartElement(Element.SOCKET_BINDING_GROUP.getLocalName());

        writeAttribute(writer, Attribute.NAME, name);
        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.marshallAsAttribute(bindingGroup, writer);

        writeExtraAttributes(writer, bindingGroup);

        if (bindingGroup.hasDefined(SOCKET_BINDING)) {
            ModelNode bindings = bindingGroup.get(SOCKET_BINDING);
            for (String bindingName : new TreeSet<>(bindings.keys())) {
                ModelNode binding = bindings.get(bindingName);
                writer.writeStartElement(Element.SOCKET_BINDING.getLocalName());
                writeAttribute(writer, Attribute.NAME, bindingName);
                AbstractSocketBindingResourceDefinition.INTERFACE.marshallAsAttribute(binding, writer);
                AbstractSocketBindingResourceDefinition.PORT.marshallAsAttribute(binding, writer);
                AbstractSocketBindingResourceDefinition.FIXED_PORT.marshallAsAttribute(binding, writer);
                AbstractSocketBindingResourceDefinition.MULTICAST_ADDRESS.marshallAsAttribute(binding, writer);
                AbstractSocketBindingResourceDefinition.MULTICAST_PORT.marshallAsAttribute(binding, writer);

                ModelNode attr = binding.get(CLIENT_MAPPINGS);
                if (attr.isDefined()) {
                    for (ModelNode mapping : attr.asList()) {
                        writer.writeEmptyElement(Element.CLIENT_MAPPING.getLocalName());

                        AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_SOURCE_NETWORK.marshallAsAttribute(mapping, writer);
                        AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_ADDRESS.marshallAsAttribute(mapping, writer);
                        AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_PORT.marshallAsAttribute(mapping, writer);
                    }
                }

                writer.writeEndElement();

            }
        }
        // outbound-socket-binding (for local destination)
        if (bindingGroup.hasDefined(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING)) {
            final ModelNode localDestinationOutboundSocketBindings = bindingGroup.get(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING);
            for (final String outboundSocketBindingName : new TreeSet<>(localDestinationOutboundSocketBindings.keys())) {
                final ModelNode outboundSocketBinding = localDestinationOutboundSocketBindings.get(outboundSocketBindingName);
                // <outbound-socket-binding>
                writer.writeStartElement(Element.OUTBOUND_SOCKET_BINDING.getLocalName());
                // name of the outbound socket binding
                writeAttribute(writer, Attribute.NAME, outboundSocketBindingName);
                // (optional) source interface
                OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.marshallAsAttribute(outboundSocketBinding, writer);
                // (optional) source port
                OutboundSocketBindingResourceDefinition.SOURCE_PORT.marshallAsAttribute(outboundSocketBinding, writer);
                // (optional) fixedSourcePort
                OutboundSocketBindingResourceDefinition.FIXED_SOURCE_PORT.marshallAsAttribute(outboundSocketBinding, writer);
                // write the <local-destination> element
                writer.writeEmptyElement(Element.LOCAL_DESTINATION.getLocalName());
                // socket-binding-ref
                LocalDestinationOutboundSocketBindingResourceDefinition.SOCKET_BINDING_REF.marshallAsAttribute(outboundSocketBinding, writer);
                // </outbound-socket-binding>
                writer.writeEndElement();
            }
        }
        // outbound-socket-binding (for remote destination)
        if (bindingGroup.hasDefined(REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING)) {
            final ModelNode remoteDestinationOutboundSocketBindings = bindingGroup.get(REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING);
            for (final String outboundSocketBindingName : new TreeSet<>(remoteDestinationOutboundSocketBindings.keys())) {
                final ModelNode outboundSocketBinding = remoteDestinationOutboundSocketBindings.get(outboundSocketBindingName);
                // <outbound-socket-binding>
                writer.writeStartElement(Element.OUTBOUND_SOCKET_BINDING.getLocalName());
                // name of the outbound socket binding
                writeAttribute(writer, Attribute.NAME, outboundSocketBindingName);
                // (optional) source interface
                OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.marshallAsAttribute(outboundSocketBinding, writer);
                // (optional) source port
                OutboundSocketBindingResourceDefinition.SOURCE_PORT.marshallAsAttribute(outboundSocketBinding, writer);
                // (optional) fixedSourcePort
                OutboundSocketBindingResourceDefinition.FIXED_SOURCE_PORT.marshallAsAttribute(outboundSocketBinding, writer);
                // write the <remote-destination> element
                writer.writeEmptyElement(Element.REMOTE_DESTINATION.getLocalName());
                // destination host
                RemoteDestinationOutboundSocketBindingResourceDefinition.HOST.marshallAsAttribute(outboundSocketBinding, writer);
                // destination port
                RemoteDestinationOutboundSocketBindingResourceDefinition.PORT.marshallAsAttribute(outboundSocketBinding, writer);
                // </outbound-socket-binding>
                writer.writeEndElement();
            }
        }
        // </socket-binding-group>
        writer.writeEndElement();
    }

    protected abstract void writeExtraAttributes(XMLExtendedStreamWriter writer, ModelNode bindingGroup) throws XMLStreamException;

    static class ServerSocketBindingsXml extends SocketBindingsXml {
        @Override
        protected void writeExtraAttributes(XMLExtendedStreamWriter writer, ModelNode bindingGroup) throws XMLStreamException {
            SocketBindingGroupResourceDefinition.PORT_OFFSET.marshallAsAttribute(bindingGroup, writer);
        }
    }

    public static class HostSocketBindingsXml extends SocketBindingsXml {
        @Override
        protected void writeExtraAttributes(XMLExtendedStreamWriter writer, ModelNode bindingGroup) throws XMLStreamException {
            SocketBindingGroupResourceDefinition.PORT_OFFSET.marshallAsAttribute(bindingGroup, writer);
        }
    }
}
