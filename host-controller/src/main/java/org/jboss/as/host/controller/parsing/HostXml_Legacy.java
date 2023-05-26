/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTIONS;
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATIC_DISCOVERY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME;
import static org.jboss.as.controller.parsing.Namespace.DOMAIN_1_0;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingOneOf;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.parsePossibleExpression;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

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
import org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler;
import org.jboss.as.host.controller.resources.HttpManagementResourceDefinition;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.host.controller.resources.SslLoopbackResourceDefinition;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;


/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code host.xml}
 *
 * @author Brian Stansberry
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:jperkins@jboss.com">James R. Perkins</a>
 */
final class HostXml_Legacy extends CommonXml implements ManagementXmlDelegate {

    private final AuditLogXml auditLogDelegate;

    private final String defaultHostControllerName;
    private final RunningMode runningMode;
    private final boolean isCachedDc;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionXml extensionXml;
    private final Namespace namespace;

    HostXml_Legacy(String defaultHostControllerName, RunningMode runningMode, boolean isCachedDC,
            final ExtensionRegistry extensionRegistry, final ExtensionXml extensionXml, final Namespace namespace) {
        super(null);
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
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        if (readerNS == DOMAIN_1_0) {
            readHostElement_1_0(reader, address, operationList);

        } else if (readerNS.getMajorVersion() == 1 || readerNS.getMajorVersion() == 2) {
            readHostElement_1_1(readerNS, reader, address, operationList);

        } else {
            // Instead of having to list the remaining versions we just check it is actually a valid version.
            for (Namespace current : Namespace.domainValues()) {
                if (readerNS.equals(current)) {
                    readHostElement_3_0(readerNS, reader, address, operationList);
                    return;
                }
            }
            throw unexpectedElement(reader);
        }
    }

    private void readHostElement_1_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        String hostName = null;

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
        // The namespace operations were created before the host name was known, the address can now be updated
        // to the local host specific address.
        for (ModelNode operation : namespaceOperations) {
            operation.get(OP_ADDR).set(address);
            list.add(operation);
        }

        // Content
        // Handle elements: sequence

        Element element = nextElement(reader, DOMAIN_1_0);

        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, DOMAIN_1_0, list, false);
            element = nextElement(reader, DOMAIN_1_0);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, DOMAIN_1_0, list, true);
            element = nextElement(reader, DOMAIN_1_0);
        }

        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = ManagementXml.newInstance(DOMAIN_1_0, this, false);
            managementXml.parseManagement(reader, address, list, false);
            element = nextElement(reader, DOMAIN_1_0);
        }
        if (element == Element.DOMAIN_CONTROLLER) {
            parseDomainController(reader, address, list, hostAddOp);
            element = nextElement(reader, DOMAIN_1_0);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, DOMAIN_1_0, list, true);
            element = nextElement(reader, DOMAIN_1_0);
        }
        if (element == Element.JVMS) {
            parseJvms(reader, address, list);
            element = nextElement(reader, DOMAIN_1_0);
        }
        if (element == Element.SERVERS) {
            parseServers_1_0(reader, address, list);
            element = nextElement(reader, DOMAIN_1_0);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }

    }

    private void readHostElement_1_1(final Namespace namespace, final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        String hostName = null;

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
        // The namespace operations were created before the host name was known, the address can now be updated
        // to the local host specific address.
        for (ModelNode operation : namespaceOperations) {
            operation.get(OP_ADDR).set(address);
            list.add(operation);
        }

        // Content
        // Handle elements: sequence

        Element element = nextElement(reader, namespace);
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
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
            switch (namespace) {
                case DOMAIN_1_1:
                    parseServers_1_0(reader, address, list);
                    break;
                default:
                    parseServers_1_2(reader, address, list);
                    break;
            }
            element = nextElement(reader, namespace);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }

    }


    private void readHostElement_3_0(final Namespace namespace, final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        String hostName = null;

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
        if (element == Element.VAULT) {
            parseVault(reader, address, namespace, list);
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
            switch (namespace) {
                case DOMAIN_1_1:
                    parseServers_1_0(reader, address, list);
                    break;
                default:
                    parseServers_1_2(reader, address, list);
                    break;
            }
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILE) {
            parseHostProfile(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }

    }

    private void parseHttpManagementInterface1_0(final XMLExtendedStreamReader reader, final ModelNode address,
                                                   final List<ModelNode> list) throws XMLStreamException {

        final ModelNode mgmtSocket = new ModelNode();
        mgmtSocket.get(OP).set(ADD);
        ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
        mgmtSocket.get(OP_ADDR).set(operationAddress);

        // Handle attributes
        boolean hasInterfaceName = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final String value = reader.getAttributeValue(i);
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case INTERFACE: {
                        HttpManagementResourceDefinition.INTERFACE.parseAndSetParameter(value, mgmtSocket, reader);
                        hasInterfaceName = true;
                        break;
                    }
                    case PORT: {
                        HttpManagementResourceDefinition.HTTP_PORT.parseAndSetParameter(value, mgmtSocket, reader);
                        break;
                    }
                    case SECURE_PORT: {
                        HttpManagementResourceDefinition.HTTPS_PORT.parseAndSetParameter(value, mgmtSocket, reader);
                        break;
                    }
                    case MAX_THREADS: {
                        // ignore xsd mistake
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);

        if (!hasInterfaceName) {
            throw missingRequired(reader, Collections.singleton(Attribute.INTERFACE.getLocalName()));
        }

        list.add(mgmtSocket);
    }

    private void parseNativeManagementInterface1_0(final XMLExtendedStreamReader reader, final ModelNode address,
                                                   final List<ModelNode> list) throws XMLStreamException {

        final ModelNode mgmtSocket = new ModelNode();
        mgmtSocket.get(OP).set(ADD);
        ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, NATIVE_INTERFACE);
        mgmtSocket.get(OP_ADDR).set(operationAddress);

        // Handle attributes
        boolean hasInterface = false;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final String value = reader.getAttributeValue(i);
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case INTERFACE: {
                        NativeManagementResourceDefinition.INTERFACE.parseAndSetParameter(value, mgmtSocket, reader);
                        hasInterface = true;
                        break;
                    }
                    case PORT: {
                        NativeManagementResourceDefinition.NATIVE_PORT.parseAndSetParameter(value, mgmtSocket, reader);
                        break;
                    }
                    case SECURE_PORT:
                        // ignore -- this was a bug in the xsd
                        break;
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
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

        list.add(mgmtSocket);
    }

    private void parseManagementInterface1_1(XMLExtendedStreamReader reader, ModelNode address, boolean http, List<ModelNode> list)  throws XMLStreamException {

        final ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, http ? HTTP_INTERFACE : NATIVE_INTERFACE);
        final ModelNode addOp = Util.getEmptyOperation(ADD, operationAddress);

        // Handle attributes

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case CONSOLE_ENABLED:{
                        if (http){
                            HttpManagementResourceDefinition.CONSOLE_ENABLED.parseAndSetParameter(value, addOp, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET:
                    if (http) {
                        parseHttpManagementSocket(reader, addOp);
                    } else {
                        parseNativeManagementSocket(reader, addOp);
                    }
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(addOp);
    }

    private void parseHttpManagementInterfaceAttributes_1_5(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case CONSOLE_ENABLED: {
                        HttpManagementResourceDefinition.CONSOLE_ENABLED.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseHttpManagementInterfaceAttributes_2_0(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
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
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseHttpManagementInterfaceAttributes_3_0(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SASL_PROTOCOL: {
                        HttpManagementResourceDefinition.SASL_PROTOCOL.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case SERVER_NAME: {
                        NativeManagementResourceDefinition.SERVER_NAME.parseAndSetParameter(value, addOp, reader);
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

    private void parseNativeManagementInterfaceAttributes_1_5(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseNativeManagementInterfaceAttributes_3_0(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SASL_PROTOCOL: {
                        NativeManagementResourceDefinition.SASL_PROTOCOL.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case SERVER_NAME: {
                        NativeManagementResourceDefinition.SERVER_NAME.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseManagementInterface_1_5(XMLExtendedStreamReader reader, ModelNode address, boolean http, List<ModelNode> list)  throws XMLStreamException {

        final ModelNode operationAddress = address.clone();
        operationAddress.add(MANAGEMENT_INTERFACE, http ? HTTP_INTERFACE : NATIVE_INTERFACE);
        final ModelNode addOp = Util.getEmptyOperation(ADD, operationAddress);

        // Handle attributes
        if (http) {
            switch (namespace.getMajorVersion()) {
                case 1:
                    parseHttpManagementInterfaceAttributes_1_5(reader, addOp);
                    break;
                case 2:
                    parseHttpManagementInterfaceAttributes_2_0(reader, addOp);
                    break;
                default:
                    parseHttpManagementInterfaceAttributes_3_0(reader, addOp);
            }
        } else {
            switch (namespace.getMajorVersion()) {
                case 1:
                case 2:
                    parseNativeManagementInterfaceAttributes_1_5(reader, addOp);
                    break;
                default:
                    parseNativeManagementInterfaceAttributes_3_0(reader, addOp);
            }
        }

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET:
                    if (http) {
                        switch (namespace) {
                            case DOMAIN_1_5:
                                parseHttpManagementSocket(reader, addOp);
                                break;
                            default:
                                switch (namespace.getMajorVersion()) {
                                    case 2:
                                        parseHttpManagementSocket(reader, addOp);
                                        break;
                                    default:
                                        parseHttpManagementSocket_1_6_and_3_0(reader, addOp);
                                        break;
                                }
                        }
                    } else {
                        parseNativeManagementSocket(reader, addOp);
                    }
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        list.add(addOp);
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

    private void parseHttpManagementSocket_1_6_and_3_0(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {
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

    /**
     * Add the operation to add the local host definition.
     */
    private ModelNode addLocalHost(final ModelNode address, final List<ModelNode> operationList, final String hostName) {

        String resolvedHost =  hostName != null ? hostName : defaultHostControllerName;

        // All further operations should modify the newly added host so the address passed in is updated.
        address.add(HOST, resolvedHost);

        // Add a step to setup the ManagementResourceRegistrations for the root host resource
        final ModelNode hostAddOp = new ModelNode();
        hostAddOp.get(OP).set(HostAddHandler.OPERATION_NAME);
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
                    switch (namespace.getMajorVersion()) {
                        case 1:
                            requireNoAttributes(reader);
                            requireNoContent(reader);
                            break;
                        default:
                            parseLocalDomainController2_0(reader, address, list);
                            break;
                    }
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
                    switch (namespace) {
                        case DOMAIN_1_0:
                            parseRemoteDomainController1_0(reader, address, list);
                            break;
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                            parseRemoteDomainController1_1(reader, address, list);
                            break;
                        case DOMAIN_1_5:
                            parseRemoteDomainController_1_5(reader, address, list);
                            break;
                        default:
                            switch (namespace.getMajorVersion()) {
                                case 1:
                                    parseRemoteDomainController_1_6(reader, address, list);
                                    break;
                                default:  // i.e. 2.x, 3.x
                                    parseRemoteDomainController_2_0(reader, address, list);
                            }
                            break;
                    }
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

    private void parseLocalDomainController2_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
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
    private void parseRemoteDomainController1_0(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        parseRemoteDomainControllerAttributes_1_0(reader, address, list);
        requireNoContent(reader);
    }

    private void parseRemoteDomainController1_1(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        switch (namespace) {
            case DOMAIN_1_1:
            case DOMAIN_1_2:
                parseRemoteDomainControllerAttributes_1_0(reader, address, list);
                break;
            default:
                parseRemoteDomainControllerAttributes_1_3(reader, address, list);
                break;
        }

        Set<String> types = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case IGNORED_RESOURCE: {
                    parseIgnoredResource(reader, address, list, types);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseRemoteDomainController_1_5(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseRemoteDomainControllerAttributes_1_5(reader, address, list, false);

        Set<String> types = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case IGNORED_RESOURCE: {
                    parseIgnoredResource(reader, address, list, types);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseRemoteDomainController_1_6(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        boolean requireDiscoveryOptions;
        boolean hasDiscoveryOptions = false;

        requireDiscoveryOptions = parseRemoteDomainControllerAttributes_1_5(reader, address, list, true);

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
                    Attribute.ADMIN_ONLY_POLICY.getLocalName(),
                    AdminOnlyDomainConfigPolicy.LEGACY_FETCH_FROM_DOMAIN_CONTROLLER.toString(),
                    Element.DISCOVERY_OPTIONS.getLocalName(), Attribute.HOST.getLocalName(),
                    Attribute.PORT.getLocalName(), reader.getLocation());
        }
    }

    private void parseRemoteDomainController_2_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        boolean requireDiscoveryOptions;
        boolean hasDiscoveryOptions = false;
        if (namespace.getMajorVersion()< 3) {
            requireDiscoveryOptions = parseRemoteDomainControllerAttributes_2_0(reader, address, list);
        } else {
            requireDiscoveryOptions = parseRemoteDomainControllerAttributes_3_0(reader, address, list);
        }

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

    private void parseRemoteDomainControllerAttributes_1_0(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        // Handle attributes
        String host = null;
        ModelNode port = null;
        String securityRealm = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case HOST: {
                        host = value;
                        break;
                    }
                    case PORT: {
                        port = parsePossibleExpression(value);
                        if(port.getType() != ModelType.EXPRESSION) {
                            try {
                                int portNo = Integer.parseInt(value);
                                if (portNo < 1) {
                                    throw ControllerLogger.ROOT_LOGGER.invalidPort(attribute.getLocalName(), value, reader.getLocation());
                                }
                            }catch(NumberFormatException e) {
                                throw ControllerLogger.ROOT_LOGGER.invalidPort(attribute.getLocalName(), value, reader.getLocation());
                            }
                        }
                        break;
                    }
                    case SECURITY_REALM: {
                        securityRealm = value;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
        if (host == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.HOST.getLocalName()));
        }
        if (port == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.PORT.getLocalName()));
        }

        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address);
        update.get(OP).set(RemoteDomainControllerAddHandler.OPERATION_NAME);
        update.get(HOST).set(parsePossibleExpression(host));
        update.get(PORT).set(port);
        if (securityRealm != null) {
            update.get(SECURITY_REALM).set(securityRealm);
        }
        list.add(update);
    }

    private void parseRemoteDomainControllerAttributes_1_3(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        // Handle attributes
        String host = null;
        ModelNode port = null;
        String securityRealm = null;
        String username = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case HOST: {
                        host = value;
                        break;
                    }
                    case PORT: {
                        port = parsePossibleExpression(value);
                        if(port.getType() != ModelType.EXPRESSION) {
                            try {
                                int portNo = Integer.parseInt(value);
                                if (portNo < 1) {
                                    throw ControllerLogger.ROOT_LOGGER.invalidPort(attribute.getLocalName(), value, reader.getLocation());
                                }
                            }catch(NumberFormatException e) {
                                throw ControllerLogger.ROOT_LOGGER.invalidPort(attribute.getLocalName(), value, reader.getLocation());
                            }
                        }
                        break;
                    }
                    case SECURITY_REALM: {
                        securityRealm = value;
                        break;
                    }
                    case USERNAME: {
                        username = value;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
        if (host == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.HOST.getLocalName()));
        }
        if (port == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.PORT.getLocalName()));
        }

        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address);
        update.get(OP).set(RemoteDomainControllerAddHandler.OPERATION_NAME);
        update.get(HOST).set(parsePossibleExpression(host));
        update.get(PORT).set(port);
        if (securityRealm != null) {
            update.get(SECURITY_REALM).set(securityRealm);
        }
        if (username != null) {
            update.get(USERNAME).set(username);
        }
        list.add(update);
    }

    /**
     * The only difference between version 1.5 and 1.6 of the schema were to make is possible to define discovery options, this
     * resulted in the host and port attributes becoming optional -this method also indicates if discovery options are required
     * where the host and port were not supplied.
     *
     * @param allowDiscoveryOptions i.e. are host and port potentially optional?
     * @return true if discovery options are required, i.e. no host and port set and the admin policy requires a config.
     */
    private boolean parseRemoteDomainControllerAttributes_1_5(final XMLExtendedStreamReader reader, final ModelNode address,
                                                              final List<ModelNode> list, boolean allowDiscoveryOptions) throws XMLStreamException {

        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address);
        update.get(OP).set(RemoteDomainControllerAddHandler.OPERATION_NAME);

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
                        DomainControllerWriteAttributeHandler.HOST.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case PORT: {
                        DomainControllerWriteAttributeHandler.PORT.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case USERNAME: {
                        DomainControllerWriteAttributeHandler.USERNAME.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case ADMIN_ONLY_POLICY: {
                        DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.parseAndSetParameter(value, update, reader);
                        ModelNode nodeValue = update.get(DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.getName());
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

        if (!update.hasDefined(DomainControllerWriteAttributeHandler.HOST.getName())) {
            if (allowDiscoveryOptions) {
                requireDiscoveryOptions = isRequireDiscoveryOptions(adminOnlyPolicy);
            } else {
                throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.HOST.getLocalName()));
            }
        }
        if (!update.hasDefined(DomainControllerWriteAttributeHandler.PORT.getName())) {
            if (allowDiscoveryOptions) {
                requireDiscoveryOptions = requireDiscoveryOptions || isRequireDiscoveryOptions(adminOnlyPolicy);
            } else {
                throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.PORT.getLocalName()));
            }
        }

        list.add(update);
        return requireDiscoveryOptions;
    }

    private boolean parseRemoteDomainControllerAttributes_3_0(final XMLExtendedStreamReader reader, final ModelNode address,
                                                              final List<ModelNode> list) throws XMLStreamException {

        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address);
        update.get(OP).set(RemoteDomainControllerAddHandler.OPERATION_NAME);

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
                        DomainControllerWriteAttributeHandler.HOST.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case PORT: {
                        DomainControllerWriteAttributeHandler.PORT.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case PROTOCOL: {
                        DomainControllerWriteAttributeHandler.PROTOCOL.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case USERNAME: {
                        DomainControllerWriteAttributeHandler.USERNAME.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case IGNORE_UNUSED_CONFIG: {
                        DomainControllerWriteAttributeHandler.IGNORE_UNUSED_CONFIG.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case ADMIN_ONLY_POLICY: {
                        DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.parseAndSetParameter(value, update, reader);
                        ModelNode nodeValue = update.get(DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.getName());
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

        if (!update.hasDefined(DomainControllerWriteAttributeHandler.HOST.getName())) {
            requireDiscoveryOptions = isRequireDiscoveryOptions(adminOnlyPolicy);
        }
        if (!update.hasDefined(DomainControllerWriteAttributeHandler.PORT.getName())) {
            requireDiscoveryOptions = requireDiscoveryOptions || isRequireDiscoveryOptions(adminOnlyPolicy);
        }

        list.add(update);
        return requireDiscoveryOptions;
    }

    /**
     * @return true if discovery options are required, i.e. no host and port set and the admin policy requires a config.
     */
    private boolean parseRemoteDomainControllerAttributes_2_0(final XMLExtendedStreamReader reader, final ModelNode address,
                                                              final List<ModelNode> list) throws XMLStreamException {

        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address);
        update.get(OP).set(RemoteDomainControllerAddHandler.OPERATION_NAME);

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
                        DomainControllerWriteAttributeHandler.HOST.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case PORT: {
                        DomainControllerWriteAttributeHandler.PORT.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        throw ROOT_LOGGER.securityRealmReferencesUnsupported();
                    }
                    case USERNAME: {
                        DomainControllerWriteAttributeHandler.USERNAME.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case IGNORE_UNUSED_CONFIG: {
                        DomainControllerWriteAttributeHandler.IGNORE_UNUSED_CONFIG.parseAndSetParameter(value, update, reader);
                        break;
                    }
                    case ADMIN_ONLY_POLICY: {
                        DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.parseAndSetParameter(value, update, reader);
                        ModelNode nodeValue = update.get(DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.getName());
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

        if (!update.hasDefined(DomainControllerWriteAttributeHandler.HOST.getName())) {
            requireDiscoveryOptions = isRequireDiscoveryOptions(adminOnlyPolicy);
        }
        if (!update.hasDefined(DomainControllerWriteAttributeHandler.PORT.getName())) {
            requireDiscoveryOptions = requireDiscoveryOptions || isRequireDiscoveryOptions(adminOnlyPolicy);
        }

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

    private void parseDiscoveryOptions(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> staticDiscoveryOptionNames,
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

    private void parseStaticDiscoveryOption(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> staticDiscoveryOptionNames) throws XMLStreamException {

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
                if(namespace.getMajorVersion() > 2) {
                    StaticDiscoveryResourceDefinition.PROTOCOL.parseAndSetParameter(value, addOp, reader);
                } else {
                    throw unexpectedAttribute(reader, i);
                }
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

    private void parseDiscoveryOption(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list, final Set<String> discoveryOptionNames) throws XMLStreamException {

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

    private void parseDiscoveryOptionProperty(XMLExtendedStreamReader reader, ModelNode discoveryOptionProperties) throws XMLStreamException {
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

    private void parseServers_1_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
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

    private void parseServers_1_2(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
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
        switch (namespace.getMajorVersion()) {
            case 1:
                switch (namespace) {
                    case DOMAIN_1_0:
                        parseServerContent1_0(reader, addUpdate, address, list);
                        break;
                    case DOMAIN_1_1:
                    case DOMAIN_1_2:
                    case DOMAIN_1_3:
                    case DOMAIN_1_4:
                    case DOMAIN_1_5:
                    case DOMAIN_1_6:
                    case DOMAIN_1_7:
                        parseServerContent1_1(reader, addUpdate, address, list);
                        break;
                    default:
                        parseServerContent1_8(reader, addUpdate, address, list);
                }
                break;
            default:
                // Versions 2 and 3 don't contain the additional element added in version 1.8
                parseServerContent1_1(reader, addUpdate, address, list);
                break;
        }

    }

    private void parseServerContent1_0(final XMLExtendedStreamReader reader, final ModelNode serverAddOperation, final ModelNode serverAddress,
                                       final List<ModelNode> list) throws XMLStreamException {
        boolean sawJvm = false;
        boolean sawSystemProperties = false;
        boolean sawSocketBinding = false;
        final Set<String> interfaceNames = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INTERFACE_SPECS: {
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
                case SOCKET_BINDING_GROUP: {
                    if (sawSocketBinding) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseSocketBindingGroupRef(reader, serverAddOperation, ServerConfigResourceDefinition.SOCKET_BINDING_GROUP,
                            ServerConfigResourceDefinition.SOCKET_BINDING_PORT_OFFSET,
                            ServerConfigResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE);
                    sawSocketBinding = true;
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

    private void parseServerContent1_1(final XMLExtendedStreamReader reader, final ModelNode serverAddOperation,
                                       final ModelNode serverAddress, final List<ModelNode> list) throws XMLStreamException {
        boolean sawJvm = false;
        boolean sawSystemProperties = false;
        boolean sawSocketBinding = false;
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

    private void parseServerContent1_8(final XMLExtendedStreamReader reader, final ModelNode serverAddOperation,
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
                    switch (namespace) {
                        case DOMAIN_1_0:
                            parseNativeManagementInterface1_0(reader, address, operationsList);
                            break;
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                            parseManagementInterface1_1(reader, address, false, operationsList);
                            break;
                        default:
                            parseManagementInterface_1_5(reader, address, false, operationsList);
                    }
                    break;
                }
                case HTTP_INTERFACE: {
                    interfaceDefined = true;
                    switch (namespace) {
                        case DOMAIN_1_0:
                            parseHttpManagementInterface1_0(reader, address, operationsList);
                            break;
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                            parseManagementInterface1_1(reader, address, true, operationsList);
                            break;
                        default:
                            parseManagementInterface_1_5(reader, address, true, operationsList);
                            break;
                    }
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
