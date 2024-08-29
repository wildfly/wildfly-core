/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_REMOTING_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.access.AccessIdentityResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Bits of parsing and marshaling logic that are related to {@code <management>} elements in domain.xml, host.xml and
 * standalone.xml.
 *
 * This parser implementation is specifically for the fifth major version of the schema.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
final class ManagementXml_18 implements ManagementXml {

    private final String namespace;
    private final ManagementXmlDelegate delegate;
    private final boolean domainConfiguration;


    ManagementXml_18(final String namespace, final ManagementXmlDelegate delegate, final boolean domainConfiguration) {
        this.namespace = namespace;
        this.delegate = delegate;
        this.domainConfiguration = domainConfiguration;
    }

    @Override
    public void parseManagement(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list, boolean requireNativeInterface) throws XMLStreamException {
        int managementInterfacesCount = 0;

        final ModelNode managementAddress = address.clone().add(CORE_SERVICE, MANAGEMENT);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());

            // https://issues.jboss.org/browse/WFCORE-3123
            if (domainConfiguration) {
                if (element == Element.ACCESS_CONTROL) {
                    if (delegate.parseAccessControl(reader, managementAddress, list) == false) {
                        throw unexpectedElement(reader);
                    }
                } else {
                    throw unexpectedElement(reader);
                }
            } else {
                switch (element) {
                    case MANAGEMENT_INTERFACES: {
                        if (++managementInterfacesCount > 1) {
                            throw unexpectedElement(reader);
                        }

                        if (delegate.parseManagementInterfaces(reader, managementAddress, list) == false) {
                            throw unexpectedElement(reader);
                        }

                        break;
                    }
                    case AUDIT_LOG: {
                        if (delegate.parseAuditLog(reader, managementAddress, list) == false) {
                            throw unexpectedElement(reader);
                        }
                        break;
                    }
                    case ACCESS_CONTROL: {
                        if (delegate.parseAccessControl(reader, managementAddress, list) == false) {
                            throw unexpectedElement(reader);
                        }
                        break;
                    }
                    case IDENTITY: {
                        parseIdentity(reader, managementAddress, list);
                        break;
                    }
                    case CONFIGURATION_CHANGES: {
                        parseConfigurationChanges(reader, managementAddress, list);
                        break;
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }
        }

        if (requireNativeInterface && managementInterfacesCount < 1) {
            throw missingRequiredElement(reader, EnumSet.of(Element.MANAGEMENT_INTERFACES));
        }
    }

    private void parseConfigurationChanges(final XMLExtendedStreamReader reader, final ModelNode address,
                                          final List<ModelNode> list) throws XMLStreamException {
        PathAddress operationAddress = PathAddress.pathAddress(address);
        operationAddress = operationAddress.append(LegacyConfigurationChangeResourceDefinition.PATH);
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(operationAddress));
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case MAX_HISTORY: {
                        LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        list.add(add);
        if(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
             throw unexpectedElement(reader);
        }
    }

    private void parseIdentity(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        PathAddress operationAddress = PathAddress.pathAddress(address);
        operationAddress = operationAddress.append(AccessIdentityResourceDefinition.PATH_ELEMENT);
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(operationAddress));
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SECURITY_DOMAIN: {
                        AccessIdentityResourceDefinition.SECURITY_DOMAIN.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        list.add(add);
        if(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            throw unexpectedElement(reader);
       }
    }

    @Override
    public void writeManagement(final XMLExtendedStreamWriter writer, final ModelNode management, boolean allowInterfaces)
            throws XMLStreamException {
        boolean hasInterface = allowInterfaces && management.hasDefined(MANAGEMENT_INTERFACE);

        // TODO - These checks are going to become a source of bugs in certain cases - what we really need is a way to allow writing to continue and
        // if an element is empty by the time it is closed then undo the write of that element.

        ModelNode accessAuthorization = management.hasDefined(ACCESS) ? management.get(ACCESS, AUTHORIZATION) : null;
        boolean accessAuthorizationDefined = accessAuthorization != null && accessAuthorization.isDefined();
        boolean hasServerGroupRoles = accessAuthorizationDefined && accessAuthorization.hasDefined(SERVER_GROUP_SCOPED_ROLE);
        boolean hasConfigurationChanges = management.hasDefined(ModelDescriptionConstants.SERVICE, ModelDescriptionConstants.CONFIGURATION_CHANGES);
        boolean hasHostRoles = accessAuthorizationDefined && (accessAuthorization.hasDefined(HOST_SCOPED_ROLE) || accessAuthorization.hasDefined(HOST_SCOPED_ROLES));
        boolean hasRoleMapping = accessAuthorizationDefined && accessAuthorization.hasDefined(ROLE_MAPPING);
        Map<String, Map<String, Set<String>>> configuredAccessConstraints = AccessControlXml.getConfiguredAccessConstraints(accessAuthorization);
        boolean hasProvider = accessAuthorizationDefined && accessAuthorization.hasDefined(AccessAuthorizationResourceDefinition.PROVIDER.getName());
        boolean hasCombinationPolicy = accessAuthorizationDefined && accessAuthorization.hasDefined(AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getName());
        ModelNode auditLog = management.hasDefined(ACCESS) ? management.get(ACCESS, AUDIT) : new ModelNode();
        ModelNode identity = management.hasDefined(ACCESS) ? management.get(ACCESS, IDENTITY) : new ModelNode();

        if (!hasInterface && !hasServerGroupRoles
              && !hasHostRoles && !hasRoleMapping && configuredAccessConstraints.size() == 0
                && !hasProvider && !hasCombinationPolicy && !auditLog.isDefined() && !identity.isDefined()) {
            return;
        }

        writer.writeStartElement(Element.MANAGEMENT.getLocalName());



        if(hasConfigurationChanges) {
            writeConfigurationChanges(writer, management.get(ModelDescriptionConstants.SERVICE, ModelDescriptionConstants.CONFIGURATION_CHANGES));
        }

        if (identity.isDefined()) {
            writeIdentity(writer, identity);
        }

        if (auditLog.isDefined()) {
            if (delegate.writeAuditLog(writer, auditLog) == false) {
                throw ROOT_LOGGER.unsupportedResource(AUDIT);
            }
        }

        if (allowInterfaces && hasInterface) {
            writeManagementInterfaces(writer, management);
        }

        if (accessAuthorizationDefined) {
            if (delegate.writeAccessControl(writer, accessAuthorization) == false) {
                throw ROOT_LOGGER.unsupportedResource(AUTHORIZATION);
            }
        }

        writer.writeEndElement();
    }

    private void writeIdentity(XMLExtendedStreamWriter writer, ModelNode identity) throws XMLStreamException {
        writer.writeStartElement(Element.IDENTITY.getLocalName());
        AccessIdentityResourceDefinition.SECURITY_DOMAIN.marshallAsAttribute(identity, writer);
        writer.writeEndElement();
    }

    private void writeManagementInterfaces(XMLExtendedStreamWriter writer, ModelNode management) throws XMLStreamException {
        writer.writeStartElement(Element.MANAGEMENT_INTERFACES.getLocalName());
        ModelNode managementInterfaces = management.get(MANAGEMENT_INTERFACE);

        if (managementInterfaces.hasDefined(NATIVE_REMOTING_INTERFACE)) {
            writer.writeEmptyElement(Element.NATIVE_REMOTING_INTERFACE.getLocalName());
        }

        if (managementInterfaces.hasDefined(NATIVE_INTERFACE)) {
            if (delegate.writeNativeManagementProtocol(writer, managementInterfaces.get(NATIVE_INTERFACE)) == false) {
                throw ROOT_LOGGER.unsupportedResource(NATIVE_INTERFACE);
            }
        }

        if (managementInterfaces.hasDefined(HTTP_INTERFACE)) {
            if (delegate.writeHttpManagementProtocol(writer, managementInterfaces.get(HTTP_INTERFACE)) == false) {
                throw ROOT_LOGGER.unsupportedResource(HTTP_INTERFACE);
            }
        }

        writer.writeEndElement();
    }

    private void writeConfigurationChanges(XMLExtendedStreamWriter writer, ModelNode configurationChanges) throws XMLStreamException {
        writer.writeStartElement(Element.CONFIGURATION_CHANGES.getLocalName());
        LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.marshallAsAttribute(configurationChanges, writer);
        writer.writeEndElement();
    }

}
