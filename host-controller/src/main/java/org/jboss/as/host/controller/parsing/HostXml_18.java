/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_UPGRADE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCE_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IS_DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOOPBACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATIC_DISCOVERY;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingOneOf;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.RunningMode;
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
import org.jboss.as.domain.management.parsing.AuditLogXml;
import org.jboss.as.domain.management.parsing.ManagementXml;
import org.jboss.as.domain.management.parsing.ManagementXmlDelegate;
import org.jboss.as.host.controller.discovery.DiscoveryOptionResourceDefinition;
import org.jboss.as.host.controller.discovery.StaticDiscoveryResourceDefinition;
import org.jboss.as.host.controller.ignored.IgnoredDomainTypeResourceDefinition;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.operations.DomainControllerWriteAttributeHandler;
import org.jboss.as.host.controller.operations.HostAddHandler;
import org.jboss.as.host.controller.resources.HttpManagementResourceDefinition;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.host.controller.resources.SslLoopbackResourceDefinition;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.as.server.parsing.SocketBindingsXml;
import org.jboss.as.server.services.net.SocketBindingGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser and marshaller for host controller configuration xml documents (e.g. host.xml) that use the urn:jboss:domain:18.0 schema.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:jperkins@jboss.com">James R. Perkins</a>
 */
final class HostXml_18 extends CommonXml implements ManagementXmlDelegate {

    private final AuditLogXml auditLogDelegate;

    private final String defaultHostControllerName;
    private final RunningMode runningMode;
    private final boolean isCachedDc;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionXml extensionXml;
    private final Namespace namespace;

    HostXml_18(String defaultHostControllerName, RunningMode runningMode, boolean isCachedDC,
               final ExtensionRegistry extensionRegistry, final ExtensionXml extensionXml, final Namespace namespace) {
        super(new SocketBindingsXml.HostSocketBindingsXml());
        this.auditLogDelegate = AuditLogXml.newInstance(namespace, true);
        this.defaultHostControllerName = defaultHostControllerName;
        this.runningMode = runningMode;
        this.isCachedDc = isCachedDC;
        this.extensionRegistry = extensionRegistry;
        this.extensionXml = extensionXml;
        this.namespace = namespace;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> operationList)
            throws XMLStreamException {
        final ModelNode address = new ModelNode().setEmptyList();
        if (Element.forName(reader.getLocalName()) != Element.HOST) {
            throw unexpectedElement(reader);
        }

        // Instead of having to list the remaining versions we just check it is actually a valid version.
        for (Namespace current : Namespace.domainValues()) {
            if (namespace.equals(current)) {
                readHostElement(reader, address, operationList);
                return;
            }
        }
        throw unexpectedElement(reader);
    }

    private void readHostElement(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        String hostName = null;
        String organization = null;
        // Deffer adding the namespaces and schema locations until after the host has been created.
        List<ModelNode> namespaceOperations = new LinkedList<ModelNode>();
        parseNamespaces(reader, address, namespaceOperations);

        // attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            switch (Namespace.forUri(reader.getAttributeNamespace(i))) {
                case NONE: {
                    final String value = reader.getAttributeValue(i);
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    switch (attribute) {
                        case NAME: {
                            hostName = value;
                            break;
                        }
                        case ORGANIZATION : {
                            organization = value;
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
                            parseSchemaLocations(reader, address, namespaceOperations, i);
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

        // The following also updates the address parameter so this address can be used for future operations
        // in the context of this host.
        final ModelNode hostAddOp = addLocalHost(address, list, hostName);
        setOrganization(address, list, organization);
        // The namespace operations were created before the host name was known, the address can now be updated
        // to the local host specific address.
        for (ModelNode operation : namespaceOperations) {
            operation.get(OP_ADDR).set(address);
            list.add(operation);
        }

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
            parsePaths(reader, address, namespace, list, true);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = ManagementXml.newInstance(namespace, this, false);
            managementXml.parseManagement(reader, address, list, true);
            element = nextElement(reader, namespace);
        } else {
            throw missingRequiredElement(reader, EnumSet.of(Element.MANAGEMENT));
        }
        if (element == Element.DOMAIN_CONTROLLER) {
            parseDomainController(reader, address, list, hostAddOp);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, true);
            element = nextElement(reader, namespace);
        }
        if (element == Element.JVMS) {
            parseJvms(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVERS) {
            parseServers(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILE) {
            parseHostProfile(reader, address, list);
            element = nextElement(reader, namespace);
        }
        // Single socket binding group
        if (element == Element.SOCKET_BINDING_GROUP) {
            parseSocketBindingGroup(reader, interfaceNames, address, list);
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
                    case HTTP_UPGRADE_ENABLED: {
                        ModelNode httpUpgrade = addOp.get(HTTP_UPGRADE);
                        HttpManagementResourceDefinition.ENABLED.parseAndSetParameter(value, httpUpgrade, reader);
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

    private void parseNativeManagementInterface(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list)  throws XMLStreamException {

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
                    parseNativeManagementSocket(reader, addOp);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(addOp);
    }

    private void parseHttpManagementInterface(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list)  throws XMLStreamException {

        final ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
        final ModelNode addOp = Util.getEmptyOperation(ADD, operationAddress);

        int socketCount = 0;
        int httpUpgradeCount = 0;

        // Handle attributes
        parseHttpManagementInterfaceAttributes(reader, addOp);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET:
                    if (++socketCount > 1) {
                        throw unexpectedElement(reader);
                    }
                    parseHttpManagementSocket(reader, addOp);
                    break;
                case HTTP_UPGRADE:
                    if (++httpUpgradeCount > 1) {
                        throw unexpectedElement(reader);
                    }
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
            boolean headerFound = false;
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, namespace);
                if (Element.forName(reader.getLocalName()) != Element.HEADER) {
                    throw unexpectedElement(reader);
                }
                headerFound = true;

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
            if (headerFound == false) {
                throw missingRequiredElement(reader, Collections.singleton(Element.HEADER.getLocalName()));
            }

            headerMapping.get(ModelDescriptionConstants.HEADERS).set(headers);
            constantHeaders.add(headerMapping);
        }

        addOp.get(ModelDescriptionConstants.CONSTANT_HEADERS).set(constantHeaders);
    }

    private void parseNativeManagementSocket(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        // Handle attributes
        boolean hasInterface = false;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case INTERFACE: {
                        NativeManagementResourceDefinition.INTERFACE.parseAndSetParameter(value, addOp, reader);
                        hasInterface = true;
                        break;
                    }
                    case PORT: {
                        NativeManagementResourceDefinition.NATIVE_PORT.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);

        if (!hasInterface) {
            throw missingRequired(reader, Collections.singleton(Attribute.INTERFACE.getLocalName()));
        }
    }

    private void parseHttpManagementSocket(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        // Handle attributes
        boolean hasInterface = false;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case INTERFACE: {
                        HttpManagementResourceDefinition.INTERFACE.parseAndSetParameter(value, addOp, reader);
                        hasInterface = true;
                        break;
                    }
                    case PORT: {
                        HttpManagementResourceDefinition.HTTP_PORT.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURE_PORT: {
                        HttpManagementResourceDefinition.HTTPS_PORT.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURE_INTERFACE: {
                        HttpManagementResourceDefinition.SECURE_INTERFACE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);

        if (!hasInterface) {
            throw missingRequired(reader, Collections.singleton(Attribute.INTERFACE.getLocalName()));
        }
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

    private void setOrganization(final ModelNode address, final List<ModelNode> operationList, final String value) {
        if (value != null && !value.isEmpty()) {
            final ModelNode update = Util.getWriteAttributeOperation(address, ORGANIZATION, value);
            operationList.add(update);
        }
    }
    /**
     * Add the operation to add the local host definition.
     */
    private ModelNode addLocalHost(final ModelNode address, final List<ModelNode> operationList, final String hostName) {

        String resolvedHost =  hostName != null ? hostName : defaultHostControllerName;

        // All further operations should modify the newly added host so the address passed in is updated.
        address.add(HOST, resolvedHost);

        // Add a step to setup the ManagementResourceRegistrations for the root host resource
        final ModelNode hostAddOp = new ModelNode();
        hostAddOp.get(OP).set(HostAddHandler.OPERATION_NAME); // /host=foo:add()
        hostAddOp.get(OP_ADDR).set(address);

        operationList.add(hostAddOp);

        // Add a step to store the HC name
        ModelNode nameValue = hostName == null ? new ModelNode() : new ModelNode(hostName);
        final ModelNode writeName = Util.getWriteAttributeOperation(address, NAME, nameValue);
        operationList.add(writeName);

        return hostAddOp;
    }

    private void parseDomainController(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final ModelNode hostAddOp)
            throws XMLStreamException {

        requireNoAttributes(reader);

        boolean hasLocal = false;
        boolean hasRemote = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LOCAL: {
                    if (hasLocal) {
                        throw ControllerLogger.ROOT_LOGGER.childAlreadyDeclared(element.getLocalName(), Element.DOMAIN_CONTROLLER.getLocalName(), reader.getLocation());
                    } else if (hasRemote) {
                        throw ControllerLogger.ROOT_LOGGER.childAlreadyDeclared(Element.REMOTE.getLocalName(), Element.DOMAIN_CONTROLLER.getLocalName(), reader.getLocation());
                    }
                    hostAddOp.get(IS_DOMAIN_CONTROLLER).set(true);
                    parseLocalDomainController(reader, address, list);
                    hasLocal = true;
                    break;
                }
                case REMOTE: {
                    if (hasRemote) {
                        throw ControllerLogger.ROOT_LOGGER.childAlreadyDeclared(element.getLocalName(), Element.DOMAIN_CONTROLLER.getLocalName(), reader.getLocation());
                    } else if (hasLocal) {
                        throw ControllerLogger.ROOT_LOGGER.childAlreadyDeclared(Element.LOCAL.getLocalName(), Element.DOMAIN_CONTROLLER.getLocalName(), reader.getLocation());
                    }
                    hostAddOp.get(IS_DOMAIN_CONTROLLER).set(false);
                    parseRemoteDomainController(reader, address, list);

                    hasRemote = true;
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
        if (!hasLocal && !hasRemote) {
            throw ControllerLogger.ROOT_LOGGER.domainControllerMustBeDeclared(Element.REMOTE.getLocalName(), Element.LOCAL.getLocalName(), reader.getLocation());
        }
    }

    private void parseLocalDomainController(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);
        boolean hasDiscoveryOptions = false;
        Set<String> staticDiscoveryOptionNames = new HashSet<String>();
        Set<String> discoveryOptionNames = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case DISCOVERY_OPTIONS: {
                    if (hasDiscoveryOptions) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseDiscoveryOptions(reader, address, list, staticDiscoveryOptionNames, discoveryOptionNames);
                    hasDiscoveryOptions = true;
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseRemoteDomainController(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        boolean requireDiscoveryOptions = false;
        boolean hasDiscoveryOptions = false;

        requireDiscoveryOptions = parseRemoteDomainControllerAttributes(reader, address, list);


        Set<String> types = new HashSet<String>();
        Set<String> staticDiscoveryOptionNames = new HashSet<String>();
        Set<String> discoveryOptionNames = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case IGNORED_RESOURCE: {
                    parseIgnoredResource(reader, address, list, types);
                    break;
                }
                case DISCOVERY_OPTIONS: { // Different from parseRemoteDomainController1_1
                    if (hasDiscoveryOptions) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseDiscoveryOptions(reader, address, list, staticDiscoveryOptionNames, discoveryOptionNames);
                    hasDiscoveryOptions = true;
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }

        if (requireDiscoveryOptions && !hasDiscoveryOptions) {
            throw ControllerLogger.ROOT_LOGGER.discoveryOptionsMustBeDeclared(CommandLineConstants.ADMIN_ONLY,
                    Attribute.ADMIN_ONLY_POLICY.getLocalName(), AdminOnlyDomainConfigPolicy.LEGACY_FETCH_FROM_DOMAIN_CONTROLLER.toString(),
                    Element.DISCOVERY_OPTIONS.getLocalName(), Attribute.HOST.getLocalName(), Attribute.PORT.getLocalName(),
                    reader.getLocation());
        }
    }

    private boolean parseRemoteDomainControllerAttributes(final XMLExtendedStreamReader reader, final ModelNode address,
                                                          final List<ModelNode> list) throws XMLStreamException {

        final ModelNode remoteDc = new ModelNode();
        final ModelNode updateDc = remoteDc.get(REMOTE).setEmptyObject() ;
        // Handle attributes
        AdminOnlyDomainConfigPolicy adminOnlyPolicy = AdminOnlyDomainConfigPolicy.DEFAULT;
        boolean requireDiscoveryOptions = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case HOST: {
                        DomainControllerWriteAttributeHandler.HOST.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case PORT: {
                        DomainControllerWriteAttributeHandler.PORT.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case PROTOCOL: {
                        DomainControllerWriteAttributeHandler.PROTOCOL.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case AUTHENTICATION_CONTEXT: {
                        DomainControllerWriteAttributeHandler.AUTHENTICATION_CONTEXT.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case USERNAME: {
                        DomainControllerWriteAttributeHandler.USERNAME.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case IGNORE_UNUSED_CONFIG: {
                        DomainControllerWriteAttributeHandler.IGNORE_UNUSED_CONFIG.parseAndSetParameter(value, updateDc, reader);
                        break;
                    }
                    case ADMIN_ONLY_POLICY: {
                        DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.parseAndSetParameter(value, updateDc, reader);
                        ModelNode nodeValue = updateDc.get(DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.getName());
                        if (nodeValue.getType() != ModelType.EXPRESSION) {
                            adminOnlyPolicy = AdminOnlyDomainConfigPolicy.getPolicy(nodeValue.asString());
                        }
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!updateDc.hasDefined(DomainControllerWriteAttributeHandler.HOST.getName())) {
            requireDiscoveryOptions = isRequireDiscoveryOptions(adminOnlyPolicy);
        }
        if (!updateDc.hasDefined(DomainControllerWriteAttributeHandler.PORT.getName())) {
            requireDiscoveryOptions = requireDiscoveryOptions || isRequireDiscoveryOptions(adminOnlyPolicy);
        }

        final ModelNode update = Util.getWriteAttributeOperation(address, DOMAIN_CONTROLLER, remoteDc);
        list.add(update);
        return requireDiscoveryOptions;
    }

    private boolean isRequireDiscoveryOptions(AdminOnlyDomainConfigPolicy adminOnlyPolicy) {
        return !isCachedDc &&
                (runningMode != RunningMode.ADMIN_ONLY || adminOnlyPolicy == AdminOnlyDomainConfigPolicy.LEGACY_FETCH_FROM_DOMAIN_CONTROLLER);
    }

    private void parseIgnoredResource(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> foundTypes) throws XMLStreamException {

        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);

        String type = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case TYPE: {
                    if (!foundTypes.add(value)) {
                        throw HostControllerLogger.ROOT_LOGGER.duplicateIgnoredResourceType(Element.IGNORED_RESOURCE.getLocalName(), value, reader.getLocation());
                    }
                    type = value;
                    break;
                }
                case WILDCARD: {
                    IgnoredDomainTypeResourceDefinition.WILDCARD.parseAndSetParameter(value, op, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (type == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.TYPE.getLocalName()));
        }

        ModelNode addr = op.get(OP_ADDR).set(address);
        addr.add(CORE_SERVICE, IGNORED_RESOURCES);
        addr.add(IGNORED_RESOURCE_TYPE, type);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INSTANCE: {
                    String name = ParseUtils.readStringAttributeElement(reader, NAME);
                    IgnoredDomainTypeResourceDefinition.NAMES.parseAndAddParameterElement(name, op, reader);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(op);
    }

    protected void parseDiscoveryOptions(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> staticDiscoveryOptionNames,
                                         final Set<String> discoveryOptionNames) throws XMLStreamException {
        requireNoAttributes(reader);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case DISCOVERY_OPTION:
                    parseDiscoveryOption(reader, address, list, discoveryOptionNames);
                    break;
                case STATIC_DISCOVERY:
                    parseStaticDiscoveryOption(reader, address, list, staticDiscoveryOptionNames);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    protected void parseStaticDiscoveryOption(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> staticDiscoveryOptionNames) throws XMLStreamException {

        // OP_ADDR will be set after parsing the NAME attribute
        final ModelNode staticDiscoveryOptionAddress = address.clone();
        staticDiscoveryOptionAddress.add(CORE_SERVICE, DISCOVERY_OPTIONS);
        final ModelNode addOp = Util.getEmptyOperation(ADD, new ModelNode());
        list.add(addOp);

        // Handle attributes
        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.HOST, Attribute.PORT);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    if (!staticDiscoveryOptionNames.add(value)) {
                        throw ParseUtils.duplicateNamedElement(reader, value);
                    }
                    addOp.get(OP_ADDR).set(staticDiscoveryOptionAddress).add(STATIC_DISCOVERY, value);
                    break;
                }
                case HOST: {
                    StaticDiscoveryResourceDefinition.HOST.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case PROTOCOL: {
                    StaticDiscoveryResourceDefinition.PROTOCOL.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case PORT: {
                    StaticDiscoveryResourceDefinition.PORT.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    protected void parseDiscoveryOption(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> discoveryOptionNames) throws XMLStreamException {

        // Handle attributes
        final ModelNode addOp = parseDiscoveryOptionAttributes(reader, address, list, discoveryOptionNames);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTY: {
                    parseDiscoveryOptionProperty(reader, addOp.get(PROPERTIES));
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private ModelNode parseDiscoveryOptionAttributes(final XMLExtendedStreamReader reader, final ModelNode address,
                                                     final List<ModelNode> list, final Set<String> discoveryOptionNames) throws XMLStreamException {

        // OP_ADDR will be set after parsing the NAME attribute
        final ModelNode discoveryOptionAddress = address.clone();
        discoveryOptionAddress.add(CORE_SERVICE, DISCOVERY_OPTIONS);
        final ModelNode addOp = Util.getEmptyOperation(ADD, new ModelNode());
        list.add(addOp);

        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.CODE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    if (!discoveryOptionNames.add(value)) {
                        throw ParseUtils.duplicateNamedElement(reader, value);
                    }
                    addOp.get(OP_ADDR).set(discoveryOptionAddress).add(DISCOVERY_OPTION, value);
                    break;
                }
                case CODE: {
                    DiscoveryOptionResourceDefinition.CODE.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case MODULE: {
                    DiscoveryOptionResourceDefinition.MODULE.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        return addOp;
    }

    protected void parseDiscoveryOptionProperty(XMLExtendedStreamReader reader, ModelNode discoveryOptionProperties) throws XMLStreamException {
        String propertyName = null;
        String propertyValue = null;
        EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.VALUE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    propertyName = value;
                    break;
                }
                case VALUE: {
                    propertyValue = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        discoveryOptionProperties.add(propertyName, propertyValue);
        requireNoContent(reader);
    }

    private void parseJvms(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();
        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case JVM:
                    JvmXml.parseJvm(reader, address, namespace, list, names, false);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseServersAttributes(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> list) throws XMLStreamException {
        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case DIRECTORY_GROUPING: {
                        final ModelNode address = parentAddress.clone();
                        list.add(Util.getWriteAttributeOperation(address, DIRECTORY_GROUPING,
                                parseAttributeValue(HostResourceDefinition.DIRECTORY_GROUPING, value,reader)));
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseServers(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        parseServersAttributes(reader, address, list);
        // Handle elements
        final Set<String> names = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SERVER:
                    parseServer(reader, address, list, names);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseServer(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> list,
                             final Set<String> serverNames) throws XMLStreamException {

        // Handle attributes
        final ModelNode addUpdate = parseServerAttributes(reader, parentAddress, serverNames);
        final ModelNode address = addUpdate.require(OP_ADDR);
        list.add(addUpdate);

        // Handle elements
        parseServerContent(reader, addUpdate, address, list);
    }

    private void parseServerContent(final XMLExtendedStreamReader reader, final ModelNode serverAddOperation,
                                    final ModelNode serverAddress, final List<ModelNode> list) throws XMLStreamException {
        boolean sawJvm = false;
        boolean sawSystemProperties = false;
        boolean sawSocketBinding = false;
        boolean sawSSL = false;
        final Set<String> interfaceNames = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INTERFACES: { // THIS IS DIFFERENT FROM 1.0
                    parseInterfaces(reader, interfaceNames, serverAddress, namespace, list, true);
                    break;
                }
                case JVM: {
                    if (sawJvm) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }

                    JvmXml.parseJvm(reader, serverAddress, namespace, list, new HashSet<String>(), true);
                    sawJvm = true;
                    break;
                }
                case PATHS: {
                    parsePaths(reader, serverAddress, namespace, list, true);
                    break;
                }
                case SOCKET_BINDINGS: {
                    if (sawSocketBinding) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseServerSocketBindings(reader, serverAddOperation);
                    sawSocketBinding = true;
                    break;
                }
                case SSL: {
                    if (sawSSL) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseServerSsl(serverAddress, reader, list);
                    sawSSL = true;
                    break;
                }
                case SYSTEM_PROPERTIES: {
                    if (sawSystemProperties) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseSystemProperties(reader, serverAddress, namespace, list, false);
                    sawSystemProperties = true;
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }

    }

    private ModelNode parseServerAttributes(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
                                            final Set<String> serverNames) throws XMLStreamException {

        final ModelNode addUpdate = new ModelNode();
        addUpdate.get(OP).set(ADD);

        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.GROUP);
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
                        if (!serverNames.add(value)) {
                            throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("server", value, reader.getLocation());
                        }
                        final ModelNode address = parentAddress.clone().add(SERVER_CONFIG, value);
                        addUpdate.get(OP_ADDR).set(address);
                        break;
                    }
                    case GROUP: {
                        ServerConfigResourceDefinition.GROUP.parseAndSetParameter(value, addUpdate, reader);
                        break;
                    }
                    case AUTO_START: {
                        ServerConfigResourceDefinition.AUTO_START.parseAndSetParameter(value, addUpdate, reader);
                        break;
                    }
                    case UPDATE_AUTO_START_WITH_SERVER_STATUS: {
                        ServerConfigResourceDefinition.UPDATE_AUTO_START_WITH_SERVER_STATUS.parseAndSetParameter(value, addUpdate, reader);
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

        return addUpdate;
    }

    private void parseServerSocketBindings(final XMLExtendedStreamReader reader, final ModelNode serverAddOperation) throws XMLStreamException {
        // Handle attributes

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SOCKET_BINDING_GROUP: {
                        ServerConfigResourceDefinition.SOCKET_BINDING_GROUP.parseAndSetParameter(value, serverAddOperation, reader);
                        break;
                    }
                    case DEFAULT_INTERFACE: {
                        ServerConfigResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE.parseAndSetParameter(value, serverAddOperation, reader);
                        break;
                    }
                    case PORT_OFFSET: {
                        ServerConfigResourceDefinition.SOCKET_BINDING_PORT_OFFSET.parseAndSetParameter(value, serverAddOperation, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        // Handle elements
        requireNoContent(reader);

    }

    private void parseServerSsl(final ModelNode parentAddress, final XMLExtendedStreamReader reader, final List<ModelNode> operations) throws XMLStreamException {

        ModelNode addOp = new ModelNode();
        addOp.get(OP).set(ADD);
        final ModelNode address = parentAddress.clone();
        address.add(SSL, LOOPBACK);
        addOp.get(OP_ADDR).set(address);
        operations.add(addOp);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SSL_PROTOCOL: {
                        SslLoopbackResourceDefinition.SSL_PROTOCOCOL.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case TRUST_MANAGER_ALGORITHM: {
                        SslLoopbackResourceDefinition.TRUST_MANAGER_ALGORITHM.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case TRUSTSTORE_TYPE: {
                        SslLoopbackResourceDefinition.TRUSTSTORE_TYPE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case TRUSTSTORE_PATH: {
                        SslLoopbackResourceDefinition.TRUSTSTORE_PATH.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case TRUSTSTORE_PASSWORD: {
                        SslLoopbackResourceDefinition.TRUSTSTORE_PASSWORD.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);

    }
    private void parseHostProfile(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
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
            reader.handleAny(subsystems);

            profileOps.put(namespace, subsystems);
        }

        // Let extensions modify the profile
        Set<ProfileParsingCompletionHandler> completionHandlers = extensionRegistry.getProfileParsingCompletionHandlers();
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

    /*
     * ManagamentXmlDelegate Methods
     */

    @Override
    public boolean parseManagementInterfaces(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList)
            throws XMLStreamException {

        requireNoAttributes(reader);
        boolean interfaceDefined = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case NATIVE_INTERFACE: {
                    interfaceDefined = true;
                    parseNativeManagementInterface(reader, address, operationsList);
                    break;
                }
                case HTTP_INTERFACE: {
                    interfaceDefined = true;
                    parseHttpManagementInterface(reader, address, operationsList);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (!interfaceDefined) {
            throw missingOneOf(reader, EnumSet.of(Element.NATIVE_INTERFACE, Element.HTTP_INTERFACE));
        }

        return true;
    }

    @Override
    public boolean parseAuditLog(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list)
            throws XMLStreamException {
        auditLogDelegate.parseAuditLog(reader, address, namespace, list);

        return true;
    }
}
