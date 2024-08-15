/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.parsePossibleExpression;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedEndElement;
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.parsing.WriteUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to interfaces.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class InterfacesXml {

    public void parseInterfaces(final XMLExtendedStreamReader reader, final Set<String> names, final ModelNode address,
            final IntVersion version, final String expectedNs, final List<ModelNode> list, final boolean checkSpecified) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element element = Element.forName(reader.getLocalName());
            if (Element.INTERFACE != element) {
                throw unexpectedElement(reader);
            }

            // Attributes
            requireSingleAttribute(reader, Attribute.NAME.getLocalName());
            final String name = reader.getAttributeValue(0);
            if (!names.add(name)) {
                throw ControllerLogger.ROOT_LOGGER.duplicateInterfaceDeclaration(reader.getLocation());
            }
            final ModelNode interfaceAdd = new ModelNode();
            interfaceAdd.get(OP_ADDR).set(address).add(ModelDescriptionConstants.INTERFACE, name);
            interfaceAdd.get(OP).set(ADD);

            final ModelNode criteriaNode = interfaceAdd;
            parseInterfaceCriteria(reader, version, expectedNs, interfaceAdd);

            if (checkSpecified && criteriaNode.getType() != ModelType.STRING && criteriaNode.getType() != ModelType.EXPRESSION
                    && criteriaNode.asInt() == 0) {
                throw unexpectedEndElement(reader);
            }
            list.add(interfaceAdd);
        }
    }

    private void parseInterfaceCriteria(final XMLExtendedStreamReader reader, final IntVersion version, final String expectedNs,
            final ModelNode interfaceModel) throws XMLStreamException {
        // all subsequent elements are criteria elements
        if (reader.nextTag() == END_ELEMENT) {
            return;
        }
        requireNamespace(reader, expectedNs);
        Element element = Element.forName(reader.getLocalName());
        switch (element) {
            case ANY_IPV4_ADDRESS:
            case ANY_IPV6_ADDRESS: {
                if (version.major() >= 3) {
                    throw ParseUtils.unexpectedElement(reader);
                } else {
                    throw ParseUtils.unsupportedElement(reader, Element.ANY_ADDRESS.getLocalName());
                }
            }
            case ANY_ADDRESS: {
                interfaceModel.get(Element.ANY_ADDRESS.getLocalName()).set(true);
                requireNoContent(reader); // consume this element
                requireNoContent(reader); // consume rest of criteria (no further content allowed)
                return;
            }
        }
        do {
            requireNamespace(reader, expectedNs);
            element = Element.forName(reader.getLocalName());
            switch (element) {
                case ANY:
                    parseCompoundInterfaceCriterion(reader, expectedNs, interfaceModel.get(ANY).setEmptyObject());
                    break;
                case NOT:
                    parseCompoundInterfaceCriterion(reader, expectedNs, interfaceModel.get(NOT).setEmptyObject());
                    break;
                default: {
                    // parseSimpleInterfaceCriterion(reader, criteria.add().set(element.getLocalName(), new
                    // ModelNode()).get(element.getLocalName()));
                    parseSimpleInterfaceCriterion(reader, interfaceModel, false);
                    break;
                }
            }
        } while (reader.nextTag() != END_ELEMENT);
    }

    private void parseCompoundInterfaceCriterion(final XMLExtendedStreamReader reader, final String expectedNs,
            final ModelNode subModel) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            parseSimpleInterfaceCriterion(reader, subModel, true);
        }
    }

    /**
     * Creates the appropriate AbstractInterfaceCriteriaElement for simple criterion.
     * <p/>
     * Note! changes/additions made here will likely need to be added to the corresponding write method that handles the write
     * of the element. Failure to do so will result in a configuration that can be read, but not written out.
     *
     * @throws javax.xml.stream.XMLStreamException if an error occurs
     * @see {@link #writeInterfaceCriteria(org.jboss.staxmapper.XMLExtendedStreamWriter, org.jboss.dmr.ModelNode, boolean)}
     */
    private void parseSimpleInterfaceCriterion(final XMLExtendedStreamReader reader, final ModelNode subModel, boolean nested)
            throws XMLStreamException {
        final Element element = Element.forName(reader.getLocalName());
        final String localName = element.getLocalName();
        switch (element) {
            case INET_ADDRESS: {
                requireSingleAttribute(reader, Attribute.VALUE.getLocalName());
                final String value = reader.getAttributeValue(0);
                ModelNode valueNode = parsePossibleExpression(value);
                requireNoContent(reader);
                // todo: validate IP address
                if (nested) {
                    subModel.get(localName).add(valueNode);
                } else {
                    subModel.get(localName).set(valueNode);
                }
                break;
            }
            case LOOPBACK_ADDRESS: {
                requireSingleAttribute(reader, Attribute.VALUE.getLocalName());
                final String value = reader.getAttributeValue(0);
                ModelNode valueNode = parsePossibleExpression(value);
                requireNoContent(reader);
                // todo: validate IP address
                subModel.get(localName).set(valueNode);
                break;
            }
            case LINK_LOCAL_ADDRESS:
            case LOOPBACK:
            case MULTICAST:
            case POINT_TO_POINT:
            case PUBLIC_ADDRESS:
            case SITE_LOCAL_ADDRESS:
            case UP:
            case VIRTUAL: {
                requireNoAttributes(reader);
                requireNoContent(reader);
                subModel.get(localName).set(true);
                break;
            }
            case NIC: {
                requireSingleAttribute(reader, Attribute.NAME.getLocalName());
                final String value = reader.getAttributeValue(0);
                requireNoContent(reader);
                // todo: validate NIC name
                if (nested) {
                    subModel.get(localName).add(value);
                } else {
                    subModel.get(localName).set(value);
                }
                break;
            }
            case NIC_MATCH: {
                requireSingleAttribute(reader, Attribute.PATTERN.getLocalName());
                final String value = reader.getAttributeValue(0);
                requireNoContent(reader);
                // todo: validate pattern
                if (nested) {
                    subModel.get(localName).add(value);
                } else {
                    subModel.get(localName).set(value);
                }
                break;
            }
            case SUBNET_MATCH: {
                requireSingleAttribute(reader, Attribute.VALUE.getLocalName());
                final String value = reader.getAttributeValue(0);
                requireNoContent(reader);

                if (nested) {
                    subModel.get(localName).add(value);
                } else {
                    subModel.get(localName).set(value);
                }
                break;
            }
            default:
                throw unexpectedElement(reader);
        }
    }

    /**
     * Write the interfaces including the criteria elements.
     *
     * @param writer    the xml stream writer
     * @param modelNode the model
     * @throws XMLStreamException
     */
    void writeInterfaces(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        writer.writeStartElement(Element.INTERFACES.getLocalName());
        final Set<String> interfaces = new TreeSet<>(modelNode.keys());
        for (String ifaceName : interfaces) {
            final ModelNode iface = modelNode.get(ifaceName);
            writer.writeStartElement(Element.INTERFACE.getLocalName());
            writeAttribute(writer, Attribute.NAME, ifaceName);
            // <any-* /> is just handled at the root
            if (iface.get(Element.ANY_ADDRESS.getLocalName()).asBoolean(false)) {
                writer.writeEmptyElement(Element.ANY_ADDRESS.getLocalName());
            } else {
                // Write the other criteria elements
                writeInterfaceCriteria(writer, iface, false);
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Write the criteria elements, extracting the information of the sub-model.
     *
     * @param writer   the xml stream writer
     * @param subModel the interface model
     * @param nested   whether it the criteria elements are nested as part of <not /> or <any />
     * @throws XMLStreamException
     */
    private void writeInterfaceCriteria(final XMLExtendedStreamWriter writer, final ModelNode subModel, final boolean nested) throws XMLStreamException {
        for (final Property property : subModel.asPropertyList()) {
            if (property.getValue().isDefined()) {
                writeInterfaceCriteria(writer, property, nested);
            }
        }
    }

    private void writeInterfaceCriteria(final XMLExtendedStreamWriter writer, final Property property, final boolean nested) throws XMLStreamException {
        final Element element = Element.forName(property.getName());
        switch (element) {
            case INET_ADDRESS:
                writeInterfaceCriteria(writer, element, Attribute.VALUE, property.getValue(), nested);
                break;
            case LOOPBACK_ADDRESS:
                writeInterfaceCriteria(writer, element, Attribute.VALUE, property.getValue(), false);
                break;
            case LINK_LOCAL_ADDRESS:
            case LOOPBACK:
            case MULTICAST:
            case POINT_TO_POINT:
            case PUBLIC_ADDRESS:
            case SITE_LOCAL_ADDRESS:
            case UP:
            case VIRTUAL: {
                if (property.getValue().asBoolean(false)) {
                    writer.writeEmptyElement(element.getLocalName());
                }
                break;
            }
            case NIC:
                writeInterfaceCriteria(writer, element, Attribute.NAME, property.getValue(), nested);
                break;
            case NIC_MATCH:
                writeInterfaceCriteria(writer, element, Attribute.PATTERN, property.getValue(), nested);
                break;
            case SUBNET_MATCH:
                writeInterfaceCriteria(writer, element, Attribute.VALUE, property.getValue(), nested);
                break;
            case ANY:
            case NOT:
                if (nested) {
                    break;
                }
                writer.writeStartElement(element.getLocalName());
                writeInterfaceCriteria(writer, property.getValue(), true);
                writer.writeEndElement();
                break;
            case NAME:
                // not a criteria element; ignore
                break;
            case ANY_ADDRESS:
                assert property.getValue().asBoolean(false) == false;
                // not a criteria element; ignore
                break;
            default: {
                // TODO we perhaps should just log a warning.
                throw ControllerLogger.ROOT_LOGGER.unknownCriteriaInterfaceProperty(property.getName());
            }
        }
    }

    private static void writeInterfaceCriteria(final XMLExtendedStreamWriter writer, final Element element, final Attribute attribute, final ModelNode subModel, boolean asList) throws XMLStreamException {
        if (asList) {
            // Nested criteria elements are represented as list in the model
            WriteUtils.writeListAsMultipleElements(writer, element, attribute, subModel);
        } else {
            WriteUtils.writeSingleElement(writer, element, attribute, subModel);
        }
    }
}
