/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_UPGRADE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_REMOTING_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT;
import static org.jboss.as.controller.parsing.Namespace.CURRENT;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.ORGANIZATION_IDENTIFIER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.management.BaseHttpInterfaceResourceDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.DeferredExtensionContext;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.parsing.WriteUtils;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.parsing.AccessControlXml;
import org.jboss.as.domain.management.parsing.AuditLogXml;
import org.jboss.as.domain.management.parsing.ManagementXml;
import org.jboss.as.domain.management.parsing.ManagementXmlDelegate;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.HttpManagementResourceDefinition;
import org.jboss.as.server.mgmt.NativeManagementResourceDefinition;
import org.jboss.as.server.services.net.SocketBindingGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parser and marshaller for standalone server configuration xml documents (e.g. standalone.xml) that use the urn:jboss:domain:11.0 schema.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class StandaloneXml_11 extends CommonXml implements ManagementXmlDelegate {

    private final AccessControlXml accessControlXml;
    private final StandaloneXml.ParsingOption[] parsingOptions;
    private AuditLogXml auditLogDelegate;
    private final Namespace namespace;
    private ExtensionHandler extensionHandler;
    private final DeferredExtensionContext deferredExtensionContext;

    StandaloneXml_11(ExtensionHandler extensionHandler, Namespace namespace, DeferredExtensionContext deferredExtensionContext, StandaloneXml.ParsingOption... options) {
        super(new SocketBindingsXml.ServerSocketBindingsXml());
        this.namespace = namespace;
        this.extensionHandler = extensionHandler;
        this.accessControlXml = AccessControlXml.newInstance(namespace);
        this.auditLogDelegate = AuditLogXml.newInstance(namespace, false);
        this.deferredExtensionContext = deferredExtensionContext;
        this.parsingOptions = options;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> operationList)
            throws XMLStreamException {

        long start = System.currentTimeMillis();
        final ModelNode address = new ModelNode().setEmptyList();

        if (Element.forName(reader.getLocalName()) != Element.SERVER) {
            throw unexpectedElement(reader);
        }

        boolean validNamespace = false;
        for (Namespace current : Namespace.domainValues()) {
            if (namespace.equals(current)) {
                validNamespace = true;
                readServerElement(reader, address, operationList);
                break;
            }
        }
        if (validNamespace == false) {
            throw unexpectedElement(reader);
        }

        if (ServerLogger.ROOT_LOGGER.isDebugEnabled()) {
            long elapsed = System.currentTimeMillis() - start;
            ServerLogger.ROOT_LOGGER.debugf("Parsed standalone configuration in [%d] ms", elapsed);
        }
    }

    /**
     * Read the <server/> element.
     *
     * @param reader  the xml stream reader
     * @param address address of the parent resource of any resources this method will add
     * @param list    the list of boot operations to which any new operations should be added
     * @throws XMLStreamException if a parsing error occurs
     */
    private void readServerElement(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        parseNamespaces(reader, address, list);

        ModelNode serverName = null;

        // attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            switch (Namespace.forUri(reader.getAttributeNamespace(i))) {
                case NONE: {
                    final String value = reader.getAttributeValue(i);
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    switch (attribute) {
                        case NAME: {
                            serverName = ServerRootResourceDefinition.NAME.parse(value, reader);
                            break;
                        }
                        case ORGANIZATION: {
                            setOrganization(address, list, ServerRootResourceDefinition.ORGANIZATION_IDENTIFIER.parse(value, reader));
                            break;
                        }
                        default:
                            throw unexpectedAttribute(reader, i);
                    }
                    break;
                }
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

        setServerName(address, list, serverName);

        // elements - sequence

        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionHandler.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        // System properties
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, true);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, true);
            element = nextElement(reader, namespace);
        }

        if (element == Element.VAULT) {
            parseVault(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = ManagementXml.newInstance(namespace, this, false);
            managementXml.parseManagement(reader, address, list, false);
            element = nextElement(reader, namespace);
        }
        //load all extensions and initialize the parsers
        deferredExtensionContext.load();
        // Single profile
        if (element == Element.PROFILE) {
            parseServerProfile(reader, address, list);
            element = nextElement(reader, namespace);
        }

        // Interfaces
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, true);
            element = nextElement(reader, namespace);
        }
        // Single socket binding group
        if (element == Element.SOCKET_BINDING_GROUP) {
            parseSocketBindingGroup(reader, interfaceNames, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME, Attribute.ENABLED),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), true);
            element = nextElement(reader, namespace);
        }

        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, namespace, new ModelNode(), list, true, true);
            element = nextElement(reader, namespace);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }
    }

    private void parseHttpManagementInterfaceAttributes(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case HTTP_AUTHENTICATION_FACTORY: {
                        HttpManagementResourceDefinition.HTTP_AUTHENTICATION_FACTORY.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SASL_PROTOCOL: {
                        HttpManagementResourceDefinition.SASL_PROTOCOL.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        HttpManagementResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SERVER_NAME: {
                        HttpManagementResourceDefinition.SERVER_NAME.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SSL_CONTEXT: {
                        HttpManagementResourceDefinition.SSL_CONTEXT.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case CONSOLE_ENABLED: {
                        HttpManagementResourceDefinition.CONSOLE_ENABLED.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case ALLOWED_ORIGINS: {
                        HttpManagementResourceDefinition.ALLOWED_ORIGINS.getParser().parseAndSetParameter(HttpManagementResourceDefinition.ALLOWED_ORIGINS, value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseNativeManagementInterfaceAttributes(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SASL_AUTHENTICATION_FACTORY: {
                        NativeManagementResourceDefinition.SASL_AUTHENTICATION_FACTORY.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SASL_PROTOCOL: {
                        NativeManagementResourceDefinition.SASL_PROTOCOL.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        NativeManagementResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SERVER_NAME: {
                        NativeManagementResourceDefinition.SERVER_NAME.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SSL_CONTEXT: {
                        NativeManagementResourceDefinition.SSL_CONTEXT.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseNativeManagementInterface(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, NATIVE_INTERFACE);
        final ModelNode addOp = Util.getEmptyOperation(ADD, operationAddress);

        // Handle attributes
        parseNativeManagementInterfaceAttributes(reader, addOp);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET:
                    throw ControllerLogger.ROOT_LOGGER.unsupportedElement(reader.getName(),reader.getLocation(), SOCKET_BINDING);
                case SOCKET_BINDING:
                    parseNativeManagementSocketBinding(reader, addOp);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(addOp);
    }

    private void parseHttpManagementInterface(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
        final ModelNode addOp = Util.getEmptyOperation(ADD, operationAddress);

        // Handle attributes
        parseHttpManagementInterfaceAttributes(reader, addOp);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET:
                    throw ControllerLogger.ROOT_LOGGER.unsupportedElement(reader.getName(),reader.getLocation(), SOCKET_BINDING);
                case SOCKET_BINDING:
                    parseHttpManagementSocketBinding(reader, addOp);
                    break;
                case HTTP_UPGRADE:
                    parseHttpUpgrade(reader, addOp);
                    break;
                case CONSTANT_HEADERS:
                    parseConstantHeaders(reader, addOp);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(addOp);
    }

    private void parseConstantHeaders(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        // Attributes
        requireNoAttributes(reader);

        ModelNode constantHeaders= new ModelNode();
        // Content
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            if (Element.forName(reader.getLocalName()) != Element.HEADER_MAPPING) {
                throw unexpectedElement(reader);
            }

            ModelNode headerMapping = new ModelNode();
            requireSingleAttribute(reader, Attribute.PATH.getLocalName());
            // After double checking the name of the only attribute we can retrieve it.
            HttpManagementResourceDefinition.PATH.parseAndSetParameter(reader.getAttributeValue(0), headerMapping, reader);
            ModelNode headers = new ModelNode();
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, namespace);
                if (Element.forName(reader.getLocalName()) != Element.HEADER) {
                    throw unexpectedElement(reader);
                }

                ModelNode header= new ModelNode();
                final int count = reader.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    final String value = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw unexpectedAttribute(reader, i);
                    } else {
                        final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                        switch (attribute) {
                            case NAME: {
                                HttpManagementResourceDefinition.HEADER_NAME.parseAndSetParameter(value, header, reader);
                                break;
                            }
                            case VALUE: {
                                HttpManagementResourceDefinition.HEADER_VALUE.parseAndSetParameter(value, header, reader);
                                break;
                            }
                            default:
                                throw unexpectedAttribute(reader, i);
                        }
                    }
                }
                headers.add(header);

                requireNoContent(reader);
            }

            headerMapping.get(ModelDescriptionConstants.HEADERS).set(headers);
            constantHeaders.add(headerMapping);
        }

        addOp.get(ModelDescriptionConstants.CONSTANT_HEADERS).set(constantHeaders);
    }

    private void parseHttpManagementSocketBinding(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {

        // Handle attributes

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case HTTP: {
                        HttpManagementResourceDefinition.SOCKET_BINDING.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case HTTPS: {
                        HttpManagementResourceDefinition.SECURE_SOCKET_BINDING.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);
    }

    private void parseHttpUpgrade(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        // Handle attributes

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case ENABLED: {
                        ModelNode httpUpgrade = addOp.get(HTTP_UPGRADE);
                        HttpManagementResourceDefinition.ENABLED.parseAndSetParameter(value, httpUpgrade, reader);
                        break;
                    }
                    case SASL_AUTHENTICATION_FACTORY: {
                        ModelNode httpUpgrade = addOp.get(HTTP_UPGRADE);
                        HttpManagementResourceDefinition.SASL_AUTHENTICATION_FACTORY.parseAndSetParameter(value, httpUpgrade, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);
    }

    private void parseNativeManagementSocketBinding(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {

        // Handle attributes
        boolean hasRef = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NATIVE: {
                        NativeManagementResourceDefinition.SOCKET_BINDING.parseAndSetParameter(value, addOp, reader);
                        hasRef = true;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!hasRef) {
            throw missingRequired(reader, Collections.singleton(Attribute.REF.getLocalName()));
        }

        requireNoContent(reader);
    }

    private void parseNativeRemotingManagementInterface(final XMLExtendedStreamReader reader, final ModelNode address,
                                                        final List<ModelNode> list) throws XMLStreamException {

        requireNoAttributes(reader);
        //requireNoContent(reader);

        final ModelNode connector = new ModelNode();
        connector.get(OP).set(ADD);
        ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, NATIVE_REMOTING_INTERFACE);
        connector.get(OP_ADDR).set(operationAddress);
        list.add(connector);

        reader.discardRemainder();
    }

    private void parseSocketBindingGroup(final XMLExtendedStreamReader reader, final Set<String> interfaces,
                                         final ModelNode address, final List<ModelNode> updates) throws XMLStreamException {

        // unique names for both socket-binding and outbound-socket-binding(s)
        final Set<String> uniqueBindingNames = new HashSet<String>();

        ModelNode op = Util.getEmptyOperation(ADD, null);
        // Handle attributes
        String socketBindingGroupName = null;

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.DEFAULT_INTERFACE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    socketBindingGroupName = value;
                    required.remove(attribute);
                    break;
                }
                case DEFAULT_INTERFACE: {
                    SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(value, op, reader);
                    required.remove(attribute);
                    if (op.get(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                            && !interfaces.contains(value)) {
                        throw ControllerLogger.ROOT_LOGGER.unknownInterface(value, Attribute.DEFAULT_INTERFACE.getLocalName(), Element.INTERFACES.getLocalName(), reader.getLocation());
                    }
                    break;
                }
                case PORT_OFFSET: {
                    SocketBindingGroupResourceDefinition.PORT_OFFSET.parseAndSetParameter(value, op, reader);
                    break;
                }
                default:
                    throw ParseUtils.unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }


        ModelNode groupAddress = address.clone().add(SOCKET_BINDING_GROUP, socketBindingGroupName);
        op.get(OP_ADDR).set(groupAddress);

        updates.add(op);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
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

    private void parseServerProfile(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        // Attributes
        requireNoAttributes(reader);

        // Content
        final Map<String, List<ModelNode>> profileOps = new LinkedHashMap<String, List<ModelNode>>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            if (Element.forName(reader.getLocalName()) != Element.SUBSYSTEM) {
                throw unexpectedElement(reader);
            }
            String namespace = reader.getNamespaceURI();
            if (profileOps.containsKey(namespace)) {
                throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("subsystem", reader.getLocation());
            }
            // parse subsystem
            final List<ModelNode> subsystems = new ArrayList<ModelNode>();
            try {
                reader.handleAny(subsystems);
            } catch (XMLStreamException e) {
                if(StandaloneXml.ParsingOption.IGNORE_SUBSYSTEM_FAILURES.isSet(this.parsingOptions)) {
                    QName element = new QName(reader.getNamespaceURI(), reader.getLocalName());
                    ControllerLogger.ROOT_LOGGER.failedToParseElementLenient(e, element.toString());
                    reader.discardRemainder();
                }
                else {
                    throw e;
                }
            }

            profileOps.put(namespace, subsystems);
        }

        // Let extensions modify the profile
        Set<ProfileParsingCompletionHandler> completionHandlers = extensionHandler.getProfileParsingCompletionHandlers();
        for (ProfileParsingCompletionHandler completionHandler : completionHandlers) {
            completionHandler.handleProfileParsingCompletion(profileOps, list);
        }

        for (List<ModelNode> subsystems : profileOps.values()) {
            for (final ModelNode update : subsystems) {
                // Process relative subsystem path address
                final ModelNode subsystemAddress = address.clone();
                for (final Property path : update.get(OP_ADDR).asPropertyList()) {
                    subsystemAddress.add(path.getName(), path.getValue().asString());
                }
                update.get(OP_ADDR).set(subsystemAddress);
                list.add(update);
            }
        }
    }

    private void setOrganization(final ModelNode address, final List<ModelNode> operationList, final ModelNode value) {
        if (value != null && value.isDefined() && value.asString().length() > 0) {
            final ModelNode update = Util.getWriteAttributeOperation(address, ORGANIZATION, value);
            operationList.add(update);
        }
    }
    private void setServerName(final ModelNode address, final List<ModelNode> operationList, final ModelNode value) {
        if (value != null && value.isDefined() && value.asString().length() > 0) {
            final ModelNode update = Util.getWriteAttributeOperation(address, NAME, value);
            operationList.add(update);
        }
    }

    void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context)
            throws XMLStreamException {

        ModelNode modelNode = context.getModelNode();
        writer.writeStartDocument();
        writer.writeStartElement(Element.SERVER.getLocalName());

        if (modelNode.hasDefined(NAME)) {
            ServerRootResourceDefinition.NAME.marshallAsAttribute(modelNode, false, writer);
        }

        if (modelNode.hasDefined(ORGANIZATION_IDENTIFIER.getName())) {
            ServerRootResourceDefinition.ORGANIZATION_IDENTIFIER.marshallAsAttribute(modelNode, false, writer);
        }

        writer.writeDefaultNamespace(CURRENT.getUriString());
        writeNamespaces(writer, modelNode);
        writeSchemaLocation(writer, modelNode);

        if (modelNode.hasDefined(EXTENSION)) {
            extensionHandler.writeExtensions(writer, modelNode.get(EXTENSION));
        }

        if (modelNode.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, modelNode.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, true);
        }

        if (modelNode.hasDefined(PATH)) {
            writePaths(writer, modelNode.get(PATH), false);
        }

        if (modelNode.hasDefined(CORE_SERVICE) && modelNode.get(CORE_SERVICE).hasDefined(VAULT)) {
            writeVault(writer, modelNode.get(CORE_SERVICE, VAULT));
        }

        if (modelNode.hasDefined(CORE_SERVICE)) {
            ManagementXml managementXml = ManagementXml.newInstance(CURRENT, this, false);
            managementXml.writeManagement(writer, modelNode.get(CORE_SERVICE, MANAGEMENT), true);
        }

        writeServerProfile(writer, context);

        if (modelNode.hasDefined(INTERFACE)) {
            writeInterfaces(writer, modelNode.get(INTERFACE));
        }

        if (modelNode.hasDefined(SOCKET_BINDING_GROUP)) {
            Set<String> groups = modelNode.get(SOCKET_BINDING_GROUP).keys();
            if (groups.size() > 1) {
                throw ControllerLogger.ROOT_LOGGER.multipleModelNodes(SOCKET_BINDING_GROUP);
            }
            for (String group : groups) {
                writeSocketBindingGroup(writer, modelNode.get(SOCKET_BINDING_GROUP, group), group);
            }
        }

        if (modelNode.hasDefined(DEPLOYMENT)) {
            writeServerDeployments(writer, modelNode.get(DEPLOYMENT));
            WriteUtils.writeNewLine(writer);
        }

        if (modelNode.hasDefined(DEPLOYMENT_OVERLAY)) {
            writeDeploymentOverlays(writer, modelNode.get(DEPLOYMENT_OVERLAY));
            WriteUtils.writeNewLine(writer);
        }
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    private void writeServerDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode)
            throws XMLStreamException {

        boolean deploymentWritten = false;
        for (String deploymentName : modelNode.keys()) {

            final ModelNode deployment = modelNode.get(deploymentName);
            if (!deployment.isDefined()) {
                continue;
            }

            if (!deploymentWritten) {
                writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
                deploymentWritten = true;
            }

            writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
            WriteUtils.writeAttribute(writer, Attribute.NAME, deploymentName);
            DeploymentAttributes.RUNTIME_NAME.marshallAsAttribute(deployment, writer);
            DeploymentAttributes.ENABLED.marshallAsAttribute(deployment, writer);
            final List<ModelNode> contentItems = deployment.require(CONTENT).asList();
            for (ModelNode contentItem : contentItems) {
                writeContentItem(writer, contentItem);
            }
            writer.writeEndElement();
        }
        if (deploymentWritten) {
            writer.writeEndElement();
        }
    }

    private void writeServerProfile(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context)
            throws XMLStreamException {

        final ModelNode profileNode = context.getModelNode();
        // In case there are no subsystems defined
        if (!profileNode.hasDefined(SUBSYSTEM)) {
            return;
        }

        writer.writeStartElement(Element.PROFILE.getLocalName());
        writeSubsystems(profileNode, writer, context);
        writer.writeEndElement();
    }

    /*
     * ManagamentXmlDelegate Methods
     */

    @Override
    public boolean parseManagementInterfaces(final XMLExtendedStreamReader reader, final ModelNode address,
                                             final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case NATIVE_INTERFACE: {
                    parseNativeManagementInterface(reader, address, list);
                    break;
                }
                case HTTP_INTERFACE: {
                    parseHttpManagementInterface(reader, address, list);
                    break;
                }
                case NATIVE_REMOTING_INTERFACE: {
                    parseNativeRemotingManagementInterface(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        return true;
    }

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
            if (null == attribute) {
                throw unexpectedAttribute(reader, i);
            }
            switch (attribute) {
                case PROVIDER:
                {
                    ModelNode provider = AccessAuthorizationResourceDefinition.PROVIDER.getParser().parse(AccessAuthorizationResourceDefinition.PROVIDER, value, reader);
                    ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                            AccessAuthorizationResourceDefinition.PROVIDER.getName(), provider);
                    operationsList.add(op);
                    break;
                }
                case USE_IDENTITY_ROLES:
                {
                    ModelNode useIdentityRoles = AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES.getParser().parse(AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES, value, reader);
                    ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                            AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES.getName(), useIdentityRoles);
                    operationsList.add(op);
                    break;
                }
                case PERMISSION_COMBINATION_POLICY:
                {
                    ModelNode provider = AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getParser().parse(AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY, value, reader);
                    ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                            AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getName(), provider);
                    operationsList.add(op);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLE_MAPPING: {
                    accessControlXml.parseAccessControlRoleMapping(reader, accAuthzAddr, operationsList);
                    break;
                }
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

    @Override
    public boolean parseAuditLog(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list)
            throws XMLStreamException {
        auditLogDelegate.parseAuditLog(reader, address, namespace, list);

        return true;
    }

    @Override
    public boolean writeNativeManagementProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {

        writer.writeStartElement(Element.NATIVE_INTERFACE.getLocalName());
        NativeManagementResourceDefinition.SASL_AUTHENTICATION_FACTORY.marshallAsAttribute(protocol, writer);
        NativeManagementResourceDefinition.SSL_CONTEXT.marshallAsAttribute(protocol, writer);
        NativeManagementResourceDefinition.SECURITY_REALM.marshallAsAttribute(protocol, writer);
        NativeManagementResourceDefinition.SASL_PROTOCOL.marshallAsAttribute(protocol, writer);
        NativeManagementResourceDefinition.SERVER_NAME.marshallAsAttribute(protocol, writer);

        if (NativeManagementResourceDefinition.SOCKET_BINDING.isMarshallable(protocol)) {
            writer.writeEmptyElement(Element.SOCKET_BINDING.getLocalName());
            NativeManagementResourceDefinition.SOCKET_BINDING.marshallAsAttribute(protocol, writer);
        }

        writer.writeEndElement();

        return true;
    }

    @Override
    public boolean writeHttpManagementProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {

        writer.writeStartElement(Element.HTTP_INTERFACE.getLocalName());
        HttpManagementResourceDefinition.HTTP_AUTHENTICATION_FACTORY.marshallAsAttribute(protocol, writer);
        HttpManagementResourceDefinition.SSL_CONTEXT.marshallAsAttribute(protocol, writer);
        HttpManagementResourceDefinition.SECURITY_REALM.marshallAsAttribute(protocol, writer);
        HttpManagementResourceDefinition.SASL_PROTOCOL.marshallAsAttribute(protocol, writer);
        HttpManagementResourceDefinition.SERVER_NAME.marshallAsAttribute(protocol, writer);

        boolean consoleEnabled = protocol.get(ModelDescriptionConstants.CONSOLE_ENABLED).asBoolean(true);
        if (!consoleEnabled) {
            HttpManagementResourceDefinition.CONSOLE_ENABLED.marshallAsAttribute(protocol, writer);
        }

        HttpManagementResourceDefinition.ALLOWED_ORIGINS.getMarshaller().marshallAsAttribute(
                HttpManagementResourceDefinition.ALLOWED_ORIGINS, protocol, true, writer);

        if (HttpManagementResourceDefinition.HTTP_UPGRADE.isMarshallable(protocol)) {
            writer.writeEmptyElement(Element.HTTP_UPGRADE.getLocalName());
            HttpManagementResourceDefinition.ENABLED.marshallAsAttribute(protocol.require(HTTP_UPGRADE), writer);
            HttpManagementResourceDefinition.SASL_AUTHENTICATION_FACTORY.marshallAsAttribute(protocol.require(HTTP_UPGRADE), writer);
        }

        if (HttpManagementResourceDefinition.SOCKET_BINDING.isMarshallable(protocol)
                || HttpManagementResourceDefinition.SECURE_SOCKET_BINDING.isMarshallable(protocol)) {
            writer.writeEmptyElement(Element.SOCKET_BINDING.getLocalName());
            HttpManagementResourceDefinition.SOCKET_BINDING.marshallAsAttribute(protocol, writer);
            HttpManagementResourceDefinition.SECURE_SOCKET_BINDING.marshallAsAttribute(protocol, writer);
        }

        if (HttpManagementResourceDefinition.CONSTANT_HEADERS.isMarshallable(protocol)) {
            writer.writeStartElement(Element.CONSTANT_HEADERS.getLocalName());

            for (ModelNode headerMapping : protocol.require(ModelDescriptionConstants.CONSTANT_HEADERS).asList()) {
                writer.writeStartElement(Element.HEADER_MAPPING.getLocalName());
                BaseHttpInterfaceResourceDefinition.PATH.marshallAsAttribute(headerMapping, writer);
                for (ModelNode header : headerMapping.require(ModelDescriptionConstants.HEADERS).asList()) {
                    writer.writeEmptyElement(Element.HEADER.getLocalName());
                    BaseHttpInterfaceResourceDefinition.HEADER_NAME.marshallAsAttribute(header, writer);
                    BaseHttpInterfaceResourceDefinition.HEADER_VALUE.marshallAsAttribute(header, writer);
                }
                writer.writeEndElement();
            }

            writer.writeEndElement();
        }

        writer.writeEndElement();

        return true;
    }

    @Override
    public boolean writeAccessControl(XMLExtendedStreamWriter writer, ModelNode accessAuthorization) throws XMLStreamException {
        accessControlXml.writeAccessControl(writer, accessAuthorization);

        return true;
    }

    @Override
    public boolean writeAuditLog(XMLExtendedStreamWriter writer, ModelNode auditLog) throws XMLStreamException {
        auditLogDelegate.writeAuditLog(writer, auditLog);

        return true;
    }
}
