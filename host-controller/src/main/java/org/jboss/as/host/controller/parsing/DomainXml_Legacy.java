/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.parsing.AccessControlXml;
import org.jboss.as.domain.management.parsing.ManagementXml;
import org.jboss.as.domain.management.parsing.ManagementXmlDelegate;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class DomainXml_Legacy extends CommonXml implements ManagementXmlDelegate {

    private final AccessControlXml accessControlXml;

    private final ExtensionXml extensionXml;
    private final ExtensionRegistry extensionRegistry;
    private final Namespace namespace;

    DomainXml_Legacy(final ExtensionXml extensionXml, final ExtensionRegistry extensionRegistry, final Namespace namespace) {
        super(new DomainSocketBindingsXml());
        accessControlXml = AccessControlXml.newInstance(namespace);
        this.extensionXml = extensionXml;
        this.extensionRegistry = extensionRegistry;
        this.namespace = namespace;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> nodes) throws XMLStreamException {
        if (Element.forName(reader.getLocalName()) != Element.DOMAIN) {
            throw unexpectedElement(reader);
        }
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        switch (readerNS) {
            case DOMAIN_1_0: {
                readDomainElement1_0(reader, new ModelNode(), nodes);
                break;
            }
            case DOMAIN_1_1:
            case DOMAIN_1_2:
                readDomainElement1_1(reader, new ModelNode(), nodes);
                break;
            case DOMAIN_1_3:
                readDomainElement1_3(reader, new ModelNode(), nodes);
                break;
            case DOMAIN_1_4:
                readDomainElement1_4(reader, new ModelNode(), nodes);
                break;
            default:
                // Instead of having to list the remaining versions we just check it is actually a valid version.
                for (Namespace current : Namespace.domainValues()) {
                    if (readerNS.equals(current)) {
                        readDomainElement2_0(reader, new ModelNode(), nodes);
                        return;
                    }
                }
                throw unexpectedElement(reader);
        }
    }

    private void readDomainElement1_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_0(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }
        // Always add op(s) to set up management-client-content resources
        initializeRolloutPlans(address, list);
    }

    private void readDomainElement1_1(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_1(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, list);
            element = nextElement(reader, namespace);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    private void readDomainElement1_3(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, list);
            element = nextElement(reader, namespace);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    private void readDomainElement1_4(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, namespace, new ModelNode(), list, true, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, list);
            element = nextElement(reader, namespace);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    private void readDomainElement2_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = ManagementXml.newInstance(namespace, this, true);
            managementXml.parseManagement(reader, address, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, namespace, new ModelNode(), list, true, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, list);
            element = nextElement(reader, namespace);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    private void readDomainElementAttributes_1_0(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            switch (Namespace.forUri(reader.getAttributeNamespace(i))) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
    }

    private void readDomainElementAttributes_1_1(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        readDomainElementAttributes_1_0(reader, address, list);
    }

    private void readDomainElementAttributes_1_3(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            Namespace ns = Namespace.forUri(reader.getAttributeNamespace(i));
            switch (ns) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                    case NAME:
                        ModelNode op = new ModelNode();
                        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
                        op.get(NAME).set(NAME);
                        op.get(VALUE).set(ParseUtils.parsePossibleExpression(reader.getAttributeValue(i)));
                        list.add(op);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseDomainSocketBindingGroups(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> interfaces) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET_BINDING_GROUP: {
                    switch (namespace) {
                        case DOMAIN_1_0:
                            // parse 1.0 socket binding group
                            parseSocketBindingGroup_1_0(reader, interfaces, address, list);
                            break;
                        default:
                            // parse 1.1 socket binding group
                            parseSocketBindingGroup_1_1(reader, interfaces, address, list);
                            break;
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSocketBindingGroup_1_0(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
                                             final List<ModelNode> updates) throws XMLStreamException {
        // unique socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader);
        if (bindingGroupUpdate.get(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                && !interfaces.contains(defaultInterface)) {
            throw ControllerLogger.ROOT_LOGGER.unknownInterface(defaultInterface, Attribute.DEFAULT_INTERFACE.getLocalName(), Element.INTERFACES.getLocalName(), reader.getLocation());
        }

        final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
        includes.setEmptyList();
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INCLUDE: {
                    ROOT_LOGGER.warnIgnoringSocketBindingGroupInclude(reader.getLocation());

                    /* This will be reintroduced for 7.2.0, leave commented out
                     final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                     if (!includedGroups.add(includedGroup)) {
                     throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                     }
                     AbstractSocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                     */
                    break;
                }
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseSocketBindingGroup_1_1(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
                                             final List<ModelNode> updates) throws XMLStreamException {
        // both outbound-socket-bindings and socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader);
        if (bindingGroupUpdate.get(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                && !interfaces.contains(defaultInterface)) {
            throw ControllerLogger.ROOT_LOGGER.unknownInterface(defaultInterface, Attribute.DEFAULT_INTERFACE.getLocalName(), Element.INTERFACES.getLocalName(), reader.getLocation());
        }

        /*This will be reintroduced for 7.2.0, leave commented out
         final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
         includes.setEmptyList();
         */
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                /* This will be reintroduced for 7.2.0, leave commented out
                 case INCLUDE: {
                 final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                 if (!includedGroups.add(includedGroup)) {
                 throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                 }
                 AbstractSocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                 break;
                 }
                 */
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(), bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                case OUTBOUND_SOCKET_BINDING: {
                    final String bindingName = parseOutboundSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(), bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseServerGroups(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            Element serverGroup = Element.forName(reader.getLocalName());
            if (Element.SERVER_GROUP != serverGroup) {
                throw unexpectedElement(reader);
            }

            final ModelNode groupAddOp = new ModelNode();
            groupAddOp.get(OP).set(ADD);
            groupAddOp.get(OP_ADDR);

            String name = null;

            // Handle attributes
            Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.PROFILE);
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {

                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    required.remove(attribute);
                    switch (attribute) {
                        case NAME: {
                            if (!names.add(value)) {
                                throw ParseUtils.duplicateNamedElement(reader, value);
                            }
                            name = value;
                            break;
                        }
                        case PROFILE: {
                            ServerGroupResourceDefinition.PROFILE.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        case MANAGEMENT_SUBSYSTEM_ENDPOINT: {
                            ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        default:
                            throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            if (!required.isEmpty()) {
                throw missingRequired(reader, required);
            }

            final ModelNode groupAddress = new ModelNode().set(address);
            groupAddress.add(ModelDescriptionConstants.SERVER_GROUP, name);
            groupAddOp.get(OP_ADDR).set(groupAddress);

            list.add(groupAddOp);

            // Handle elements
            boolean sawDeployments = false;

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, namespace);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                    case JVM: {
                        JvmXml.parseJvm(reader, groupAddress, namespace, list, new HashSet<String>(), false);
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        if(namespace.compareTo(Namespace.DOMAIN_3_0) >= 0) {
                            parseSocketBindingGroupRef(reader, groupAddOp, ServerGroupResourceDefinition.SOCKET_BINDING_GROUP,
                                ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET,
                                ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE);
                        } else {
                             parseSocketBindingGroupRef(reader, groupAddOp, ServerGroupResourceDefinition.SOCKET_BINDING_GROUP,
                                ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET, null);
                        }
                        break;
                    }
                    case DEPLOYMENTS: {
                        if (sawDeployments) {
                            throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                        }
                        sawDeployments = true;
                        List<ModelNode> deployments = new ArrayList<ModelNode>();
                        parseDeployments(reader, groupAddress, namespace, deployments,
                                EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME, Attribute.ENABLED),
                                Collections.<Element>emptySet(), true);
                        list.addAll(deployments);
                        break;
                    }
                    case DEPLOYMENT_OVERLAYS: {
                        parseDeploymentOverlays(reader, namespace, groupAddress, list, false, true);
                        break;
                    }
                    case SYSTEM_PROPERTIES: {
                        parseSystemProperties(reader, groupAddress, namespace, list, false);
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedElement(reader);
                }
            }

        }
    }

    private void parseProfiles(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            Element element = Element.forName(reader.getLocalName());
            if (Element.PROFILE != element) {
                throw unexpectedElement(reader);
            }

            // Attributes
            requireSingleAttribute(reader, Attribute.NAME.getLocalName());
            final String name = reader.getAttributeValue(0);
            if (!names.add(name)) {
                throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("profile", name, reader.getLocation());
            }

            //final Set<String> includes = new HashSet<String>();  // See commented out section below.
            //final ModelNode profileIncludes = new ModelNode();
            // Content
            // Sequence
            final Map<String, List<ModelNode>> profileOps = new LinkedHashMap<String, List<ModelNode>>();
            while (reader.nextTag() != END_ELEMENT) {
                Namespace ns = Namespace.forUri(reader.getNamespaceURI());
                switch (ns) {
                    case UNKNOWN: {
                        if (Element.forName(reader.getLocalName()) != Element.SUBSYSTEM) {
                            throw unexpectedElement(reader);
                        }
                        String namespace = reader.getNamespaceURI();
                        if (profileOps.containsKey(namespace)) {
                            throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("subsystem", name, reader.getLocation());
                        }
                        // parse content
                        final List<ModelNode> subsystems = new ArrayList<ModelNode>();
                        reader.handleAny(subsystems);

                        profileOps.put(namespace, subsystems);

                        break;
                    }
                    case DOMAIN_1_0:
                    case DOMAIN_1_1:
                    case DOMAIN_1_2:
                    case DOMAIN_1_3: {
                        requireNamespace(reader, namespace);
                        // include should come first
                        if (profileOps.size() > 0) {
                            throw unexpectedElement(reader);
                        }
                        if (Element.forName(reader.getLocalName()) != Element.INCLUDE) {
                            throw unexpectedElement(reader);
                        }
                        //Remove support for profile includes until 7.2.0
                        if (ns == Namespace.DOMAIN_1_0) {
                            ROOT_LOGGER.warnIgnoringProfileInclude(reader.getLocation());
                        }
                        throw unexpectedElement(reader);
                        /* This will be reintroduced for 7.2.0, leave commented out
                         final String includedName = readStringAttributeElement(reader, Attribute.PROFILE.getLocalName());
                         if (! names.contains(includedName)) {
                         throw MESSAGES.profileNotFound(reader.getLocation());
                         }
                         if (! includes.add(includedName)) {
                         throw MESSAGES.duplicateProfileInclude(reader.getLocation());
                         }
                         profileIncludes.add(includedName);
                         break;
                         */
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }

            // Let extensions modify the profile
            Set<ProfileParsingCompletionHandler> completionHandlers = extensionRegistry.getProfileParsingCompletionHandlers();
            for (ProfileParsingCompletionHandler completionHandler : completionHandlers) {
                completionHandler.handleProfileParsingCompletion(profileOps, list);
            }

            final ModelNode profile = new ModelNode();
            profile.get(OP).set(ADD);
            profile.get(OP_ADDR).set(address).add(ModelDescriptionConstants.PROFILE, name);
            /* This will be reintroduced for 7.2.0, leave commented out
             profile.get(INCLUDES).set(profileIncludes);
             */
            list.add(profile);

            // Process subsystems
            for (List<ModelNode> subsystems : profileOps.values()) {
                for (final ModelNode update : subsystems) {
                    // Process relative subsystem path address
                    final ModelNode subsystemAddress = address.clone().set(address).add(ModelDescriptionConstants.PROFILE, name);
                    for (final Property path : update.get(OP_ADDR).asPropertyList()) {
                        subsystemAddress.add(path.getName(), path.getValue().asString());
                    }
                    update.get(OP_ADDR).set(subsystemAddress);
                    list.add(update);
                }
            }
        }
    }

    private void parseManagementClientContent(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        boolean rolloutPlansAdded = false;
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLLOUT_PLANS: {
                    parseRolloutPlans(reader, address, list);
                    rolloutPlansAdded = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (!rolloutPlansAdded) {
            initializeRolloutPlans(address, list);
        }
    }

    private void parseRolloutPlans(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {

        String hash = readStringAttributeElement(reader, Attribute.SHA1.getLocalName());

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        try {
            addOp.get(HASH).set(HashUtil.hexStringToByteArray(hash));
        } catch (final Exception e) {
            throw ControllerLogger.ROOT_LOGGER.invalidSha1Value(e, hash, Attribute.SHA1.getLocalName(), reader.getLocation());
        }

        list.add(addOp);
    }

    private void initializeRolloutPlans(ModelNode address, List<ModelNode> list) {

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        list.add(addOp);
    }

    /*
     * ManagamentXmlDelegate Methods
     */

    @Override
    public boolean parseAccessControl(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList)
            throws XMLStreamException {

        ModelNode accAuthzAddr = address.clone().add(ACCESS, AUTHORIZATION);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {

            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (attribute == Attribute.PROVIDER) {
                ModelNode provider = parseAttributeValue(AccessAuthorizationResourceDefinition.PROVIDER, value, reader);
                ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                        AccessAuthorizationResourceDefinition.PROVIDER.getName(), provider);

                operationsList.add(op);
            } else if (attribute == Attribute.PERMISSION_COMBINATION_POLICY) {
                ModelNode provider = parseAttributeValue(AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY, value, reader);
                ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                        AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getName(), provider);

                operationsList.add(op);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLE_MAPPING:
                    accessControlXml.parseAccessControlRoleMapping(reader, accAuthzAddr, operationsList);
                    break;
                case SERVER_GROUP_SCOPED_ROLES:
                    accessControlXml.parseServerGroupScopedRoles(reader, accAuthzAddr, operationsList);
                    break;
                case HOST_SCOPED_ROLES:
                    accessControlXml.parseHostScopedRoles(reader, accAuthzAddr, operationsList);
                    break;
                case CONSTRAINTS: {
                    accessControlXml.parseAccessControlConstraints(reader, accAuthzAddr, operationsList);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        return true;
    }

}
