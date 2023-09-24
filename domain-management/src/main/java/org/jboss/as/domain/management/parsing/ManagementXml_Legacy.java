/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.util.EnumSet;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Bits of parsing and marshaling logic that are related to {@code <management>} elements in domain.xml, host.xml and
 * standalone.xml.
 *
 * For the first three major schema versions a single parser implementation was used to support all concurrently, this 'legacy'
 * class covers this tripple version support - later versions are bieng supported with one major version per parser, this is to
 * make maintenance less error prone but will also make it easier to deprecate and remove legacy version.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
final class ManagementXml_Legacy implements ManagementXml {

    private final Namespace namespace;
    private final ManagementXmlDelegate delegate;
    private final boolean domainConfiguration;


    ManagementXml_Legacy(final Namespace namespace, final ManagementXmlDelegate delegate, final boolean domainConfiguration) {
        this.namespace = namespace;
        this.delegate = delegate;
        this.domainConfiguration = domainConfiguration;
    }

    @Override
    public void parseManagement(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list, boolean requireNativeInterface) throws XMLStreamException {
        switch (namespace) {
            case DOMAIN_1_0:
            case DOMAIN_1_1:
            case DOMAIN_1_2:
            case DOMAIN_1_3:
            case DOMAIN_1_4:
                parseManagement_1_0(reader, address, list, requireNativeInterface);
                break;
            default:
                parseManagement_1_5(reader, address, list, requireNativeInterface);
        }
    }

    private void parseManagement_1_0(final XMLExtendedStreamReader reader, final ModelNode address,
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
            }else {
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

    private void parseManagement_1_5(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list, boolean requireNativeInterface) throws XMLStreamException {
        int managementInterfacesCount = 0;

        final ModelNode managementAddress = address.clone().add(CORE_SERVICE, MANAGEMENT);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
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
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (requireNativeInterface && managementInterfacesCount < 1) {
            throw missingRequiredElement(reader, EnumSet.of(Element.MANAGEMENT_INTERFACES));
        }
    }


}
