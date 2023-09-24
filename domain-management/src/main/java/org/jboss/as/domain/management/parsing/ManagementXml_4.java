/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.util.EnumSet;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;


/**
 * Bits of parsing and marshaling logic that are related to {@code <management>} elements in domain.xml, host.xml and
 * standalone.xml.
 *
 * This parser implementation is specifically for the fourth major version of the schema.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
final class ManagementXml_4 implements ManagementXml {

    private final Namespace namespace;
    private final ManagementXmlDelegate delegate;
    private final boolean domainConfiguration;


    ManagementXml_4(final Namespace namespace, final ManagementXmlDelegate delegate, boolean domainConfiguration) {
        this.namespace = namespace;
        this.delegate = delegate;
        this.domainConfiguration = domainConfiguration;
    }

    @Override
    public void parseManagement(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list, boolean requireNativeInterface) throws XMLStreamException {
        int securityRealmsCount = 0;
        int connectionsCount = 0;
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
                    case SECURITY_REALMS: {
                        throw ROOT_LOGGER.securityRealmsUnsupported();
                    }
                    case OUTBOUND_CONNECTIONS: {
                        throw ROOT_LOGGER.outboundConnectionsUnsupported();
                    }
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

}
