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

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADVANCED_FILTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_SEARCH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_TO_PRINCIPAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP_CONNECTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLAIN_TEXT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRINCIPAL_TO_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECRET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_FILTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_IS_DN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_TO_DN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERS;
import static org.jboss.as.controller.parsing.ParseUtils.invalidAttributeValue;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingOneOf;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_ACCESS_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_SEARCH_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.CACHE;
import static org.jboss.as.domain.management.ModelDescriptionConstants.DEFAULT_DEFAULT_USER;
import static org.jboss.as.domain.management.ModelDescriptionConstants.DEFAULT_USER;
import static org.jboss.as.domain.management.ModelDescriptionConstants.JAAS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KERBEROS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KEYTAB;
import static org.jboss.as.domain.management.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.domain.management.ModelDescriptionConstants.PLUG_IN;
import static org.jboss.as.domain.management.ModelDescriptionConstants.PROPERTY;
import static org.jboss.as.domain.management.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionPropertyResourceDefinition;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition;
import org.jboss.as.domain.management.security.AdvancedUserSearchResourceDefintion;
import org.jboss.as.domain.management.security.BaseLdapGroupSearchResource;
import org.jboss.as.domain.management.security.BaseLdapUserSearchResource;
import org.jboss.as.domain.management.security.GroupToPrincipalResourceDefinition;
import org.jboss.as.domain.management.security.JaasAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.KerberosAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.KeystoreAttributes;
import org.jboss.as.domain.management.security.KeytabResourceDefinition;
import org.jboss.as.domain.management.security.LdapAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.LdapAuthorizationResourceDefinition;
import org.jboss.as.domain.management.security.LdapCacheResourceDefinition;
import org.jboss.as.domain.management.security.LocalAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.PlugInAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.PrincipalToGroupResourceDefinition;
import org.jboss.as.domain.management.security.PropertiesAuthenticationResourceDefinition;
import org.jboss.as.domain.management.security.PropertiesAuthorizationResourceDefinition;
import org.jboss.as.domain.management.security.PropertyResourceDefinition;
import org.jboss.as.domain.management.security.SSLServerIdentityResourceDefinition;
import org.jboss.as.domain.management.security.SecretServerIdentityResourceDefinition;
import org.jboss.as.domain.management.security.SecurityRealmResourceDefinition;
import org.jboss.as.domain.management.security.UserResourceDefinition;
import org.jboss.as.domain.management.security.UserSearchResourceDefintion;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
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
            }else {
                switch (element) {
                    case SECURITY_REALMS: {
                        if (++securityRealmsCount > 1) {
                            throw unexpectedElement(reader);
                        }
                        if (delegate.parseSecurityRealms(reader, managementAddress, list) == false) {
                            parseSecurityRealms(reader, managementAddress, list);
                        }

                        break;
                    }
                    case OUTBOUND_CONNECTIONS: {
                        if (++connectionsCount > 1) {
                            throw unexpectedElement(reader);
                        }
                        if (delegate.parseOutboundConnections(reader, managementAddress, list) == false) {
                            parseOutboundConnections(reader, managementAddress, list);
                        }

                        break;
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
        int securityRealmsCount = 0;
        int connectionsCount = 0;
        int managementInterfacesCount = 0;

        final ModelNode managementAddress = address.clone().add(CORE_SERVICE, MANAGEMENT);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SECURITY_REALMS: {
                    if (++securityRealmsCount > 1) {
                        throw unexpectedElement(reader);
                    }
                    if (delegate.parseSecurityRealms(reader, managementAddress, list) == false) {
                        parseSecurityRealms(reader, managementAddress, list);
                    }

                    break;
                }
                case OUTBOUND_CONNECTIONS: {
                    if (++connectionsCount > 1) {
                        throw unexpectedElement(reader);
                    }
                    if (delegate.parseOutboundConnections(reader, managementAddress, list) == false) {
                        parseOutboundConnections(reader, managementAddress, list);
                    }

                    break;
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

    private void parseOutboundConnections(final XMLExtendedStreamReader reader, final ModelNode address,
                                          final List<ModelNode> list) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LDAP: {
                    switch (namespace) {
                        case DOMAIN_1_0:
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                        case DOMAIN_1_3:
                            parseLdapConnection_1_0(reader, address, list);
                            break;
                        case DOMAIN_1_4:
                        case DOMAIN_1_5:
                            parseLdapConnection_1_4(reader, address, list);
                            break;
                        case DOMAIN_2_0:
                            parseLdapConnection_2_0(reader, address, list);
                            break;
                        default:
                            // Most recent versions for 1.x, 2.x and 3.x streams converge at this point.
                            parseLdapConnection_1_6_and_2_1(reader, address, list);
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

    private void parseLdapConnection_1_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);

        list.add(add);

        Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.URL, Attribute.SEARCH_DN, Attribute.SEARCH_CREDENTIAL);
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
                        add.get(OP_ADDR).set(address).add(LDAP_CONNECTION, value);
                        break;
                    }
                    case URL: {
                        LdapConnectionResourceDefinition.URL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SEARCH_DN: {
                        LdapConnectionResourceDefinition.SEARCH_DN.parseAndSetParameter(value,  add, reader);
                        break;
                    }
                    case SEARCH_CREDENTIAL: {
                        LdapConnectionResourceDefinition.SEARCH_CREDENTIAL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case INITIAL_CONTEXT_FACTORY: {
                        LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    private void parseLdapConnection_1_4(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);

        list.add(add);

        Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.URL);
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
                        add.get(OP_ADDR).set(address).add(LDAP_CONNECTION, value);
                        break;
                    }
                    case URL: {
                        LdapConnectionResourceDefinition.URL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SEARCH_DN: {
                        LdapConnectionResourceDefinition.SEARCH_DN.parseAndSetParameter(value,  add, reader);
                        break;
                    }
                    case SEARCH_CREDENTIAL: {
                        LdapConnectionResourceDefinition.SEARCH_CREDENTIAL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        LdapConnectionResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case INITIAL_CONTEXT_FACTORY: {
                        LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    private void parseLdapConnection_2_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);

        list.add(add);

        ModelNode connectionAddress = null;

        Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.URL);
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
                        connectionAddress = address.clone().add(LDAP_CONNECTION, value);
                        add.get(OP_ADDR).set(connectionAddress);
                        break;
                    }
                    case URL: {
                        LdapConnectionResourceDefinition.URL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SEARCH_DN: {
                        LdapConnectionResourceDefinition.SEARCH_DN.parseAndSetParameter(value,  add, reader);
                        break;
                    }
                    case SEARCH_CREDENTIAL: {
                        LdapConnectionResourceDefinition.SEARCH_CREDENTIAL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        LdapConnectionResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case INITIAL_CONTEXT_FACTORY: {
                        LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        boolean propertiesFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTIES: {
                    if (propertiesFound) {
                        throw unexpectedElement(reader);
                    }
                    propertiesFound = true;
                    parseLdapConnectionProperties(reader, connectionAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseLdapConnection_1_6_and_2_1(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);

        list.add(add);

        ModelNode connectionAddress = null;

        Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.URL);
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
                        connectionAddress = address.clone().add(LDAP_CONNECTION, value);
                        add.get(OP_ADDR).set(connectionAddress);
                        break;
                    }
                    case URL: {
                        LdapConnectionResourceDefinition.URL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SEARCH_DN: {
                        LdapConnectionResourceDefinition.SEARCH_DN.parseAndSetParameter(value,  add, reader);
                        break;
                    }
                    case SEARCH_CREDENTIAL: {
                        LdapConnectionResourceDefinition.SEARCH_CREDENTIAL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case SECURITY_REALM: {
                        LdapConnectionResourceDefinition.SECURITY_REALM.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case INITIAL_CONTEXT_FACTORY: {
                        LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case REFERRALS: {
                        LdapConnectionResourceDefinition.REFERRALS.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    case HANDLES_REFERRALS_FOR: {
                        for (String url : reader.getListAttributeValue(i)) {
                            LdapConnectionResourceDefinition.HANDLES_REFERRALS_FOR.parseAndAddParameterElement(url, add, reader);
                        }
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        boolean propertiesFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTIES: {
                    if (propertiesFound) {
                        throw unexpectedElement(reader);
                    }
                    propertiesFound = true;
                    parseLdapConnectionProperties(reader, connectionAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseLdapConnectionProperties(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTY: {
                    Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.VALUE);
                    final ModelNode add = new ModelNode();
                    add.get(OP).set(ADD);

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
                                    add.get(OP_ADDR).set(address.clone()).add(PROPERTY, value);
                                    break;
                                }
                                case VALUE: {
                                    LdapConnectionPropertyResourceDefinition.VALUE.parseAndSetParameter(value, add, reader);
                                    break;
                                }
                                default: {
                                    throw unexpectedAttribute(reader, i);
                                }
                            }
                        }
                    }

                    if (required.size() > 0) {
                        throw missingRequired(reader, required);
                    }
                    requireNoContent(reader);

                    list.add(add);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSecurityRealms(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SECURITY_REALM: {
                    switch (namespace) {
                        case DOMAIN_1_0:
                            parseSecurityRealm_1_0(reader, address, list);
                            break;
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                            parseSecurityRealm_1_1(reader, address, list);
                            break;
                        default:
                            parseSecurityRealm_1_3(reader, address, list);
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

    private void parseSecurityRealm_1_0(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        requireSingleAttribute(reader, Attribute.NAME.getLocalName());
        // After double checking the name of the only attribute we can retrieve it.
        final String realmName = reader.getAttributeValue(0);

        final ModelNode realmAddress = address.clone();
        realmAddress.add(SECURITY_REALM, realmName);
        final ModelNode add = new ModelNode();
        add.get(OP_ADDR).set(realmAddress);
        add.get(OP).set(ADD);
        list.add(add);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SERVER_IDENTITIES:
                    parseServerIdentities_1_0(reader, realmAddress, list);
                    break;
                case AUTHENTICATION: {
                    parseAuthentication_1_0(reader, realmAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSecurityRealm_1_1(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        requireSingleAttribute(reader, Attribute.NAME.getLocalName());
        // After double checking the name of the only attribute we can retrieve it.
        final String realmName = reader.getAttributeValue(0);

        final ModelNode realmAddress = address.clone();
        realmAddress.add(SECURITY_REALM, realmName);
        final ModelNode add = new ModelNode();
        add.get(OP_ADDR).set(realmAddress);
        add.get(OP).set(ADD);
        list.add(add);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SERVER_IDENTITIES:
                    parseServerIdentities_1_0(reader, realmAddress, list);
                    break;
                case AUTHENTICATION: {
                    parseAuthentication_1_1(reader, realmAddress, list);
                    break;
                }
                case AUTHORIZATION:
                    parseAuthorization_1_1(reader, add, list);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSecurityRealm_1_3(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
            throws XMLStreamException {
        requireSingleAttribute(reader, Attribute.NAME.getLocalName());
        // After double checking the name of the only attribute we can retrieve it.
        final String realmName = reader.getAttributeValue(0);

        final ModelNode realmAddress = address.clone();
        realmAddress.add(SECURITY_REALM, realmName);
        final ModelNode add = new ModelNode();
        add.get(OP_ADDR).set(realmAddress);
        add.get(OP).set(ADD);
        list.add(add);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PLUG_INS:
                    parsePlugIns(reader, realmAddress, list);
                    break;
                case SERVER_IDENTITIES:
                    switch (namespace.getMajorVersion()) {
                        case 1:
                            switch (namespace) {
                                case DOMAIN_1_3:
                                case DOMAIN_1_4:
                                case DOMAIN_1_5:
                                case DOMAIN_1_6:
                                    parseServerIdentities_1_0(reader, realmAddress, list);
                                    break;
                                default:
                                    parseServerIdentities_1_7_and_3_0(reader, realmAddress, list);
                                    break;
                            }
                            break;
                        case 2:
                            parseServerIdentities_1_0(reader, realmAddress, list);
                            break;
                        default:
                            parseServerIdentities_1_7_and_3_0(reader, realmAddress, list);
                    }
                    break;
                case AUTHENTICATION: {
                    switch (namespace.getMajorVersion()) {
                        case 1:
                            switch (namespace) {
                                case DOMAIN_1_3:
                                case DOMAIN_1_4:
                                case DOMAIN_1_5:
                                case DOMAIN_1_6:
                                    parseAuthentication_1_3(reader, realmAddress, list);
                                    break;
                                default:
                                    parseAuthentication_1_7_and_3_0(reader, realmAddress, list);
                            }
                            break;
                        case 2:
                            parseAuthentication_1_3(reader, realmAddress, list);
                            break;
                        default:
                            parseAuthentication_1_7_and_3_0(reader, realmAddress, list);
                    }
                    break;
                }
                case AUTHORIZATION:
                    switch (namespace) {
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                            parseAuthorization_1_3(reader, realmAddress, list);
                            break;
                        default:
                            parseAuthorization_1_5_and_2_0(reader, add, list);
                    }
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parsePlugIns(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PLUG_IN: {
                    ModelNode plugIn = new ModelNode();
                    plugIn.get(OP).set(ADD);
                    String moduleValue = readStringAttributeElement(reader, Attribute.MODULE.getLocalName());
                    final ModelNode newAddress = realmAddress.clone();
                    newAddress.add(PLUG_IN, moduleValue);
                    plugIn.get(OP_ADDR).set(newAddress);

                    list.add(plugIn);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseServerIdentities_1_0(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SECRET: {
                    parseSecret(reader, realmAddress, list);
                    break;
                }
                case SSL: {
                    if (namespace.getMajorVersion() == 1) {
                        // This is only needed until we port version 1.7 of the schema from EAP to here
                        // and then the 1.x versions will also be listed in the switch below.
                        parseSSL_1_0(reader, realmAddress, list);
                    } else {
                        switch (namespace) {
                            case DOMAIN_2_0:
                            case DOMAIN_2_1:
                                parseSSL_1_0(reader, realmAddress, list);
                                break;
                            default:
                                parseSSL_1_7_and_2_2(reader, realmAddress, list);
                                break;
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseServerIdentities_1_7_and_3_0(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SECRET: {
                    parseSecret(reader, realmAddress, list);
                    break;
                }
                case SSL: {
                    parseSSL_1_7_and_2_2(reader, realmAddress, list);
                    break;
                }
                case KERBEROS: {
                    parseKerberosIdentity(reader, realmAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSecret(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {

        ModelNode secret = new ModelNode();
        secret.get(OP).set(ADD);
        secret.get(OP_ADDR).set(realmAddress).add(SERVER_IDENTITY, SECRET);
        String secretValue = readStringAttributeElement(reader, Attribute.VALUE.getLocalName());
        SecretServerIdentityResourceDefinition.VALUE.parseAndSetParameter(secretValue, secret, reader);

        list.add(secret);
    }

    private void parseSSL_1_0(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        ModelNode ssl = new ModelNode();
        ssl.get(OP).set(ADD);
        ssl.get(OP_ADDR).set(realmAddress).add(SERVER_IDENTITY, SSL);
        list.add(ssl);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PROTOCOL: {
                        SSLServerIdentityResourceDefinition.PROTOCOL.parseAndSetParameter(value, ssl, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case KEYSTORE: {
                    switch (namespace) {
                        case DOMAIN_1_0:
                        case DOMAIN_1_1:
                        case DOMAIN_1_2:
                            parseKeystore_1_0(reader, ssl);
                            break;
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                        case DOMAIN_1_5:
                        case DOMAIN_2_0:
                            parseKeystore_1_3(reader, ssl, true);
                            break;
                        default:
                            // Most recent versions for 1.x, 2.x and 3.x streams converge at this point.
                            parseKeystore_1_6_and_2_1(reader, ssl, true);
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSSL_1_7_and_2_2(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        ModelNode ssl = new ModelNode();
        ssl.get(OP).set(ADD);
        ssl.get(OP_ADDR).set(realmAddress).add(SERVER_IDENTITY, SSL);
        list.add(ssl);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PROTOCOL: {
                        SSLServerIdentityResourceDefinition.PROTOCOL.parseAndSetParameter(value, ssl, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ENGINE: {
                    parseEngine(reader, ssl);
                    break;
                }
                case KEYSTORE: {
                    // Most recent versions for 1.x, 2.x and 3.x streams converge at this point.
                    parseKeystore_1_6_and_2_1(reader, ssl, true);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseEngine(final XMLExtendedStreamReader reader, final ModelNode addOperation)
            throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case ENABLED_CIPHER_SUITES:
                        for (String value : reader.getListAttributeValue(i)) {
                            SSLServerIdentityResourceDefinition.ENABLED_CIPHER_SUITES.parseAndAddParameterElement(value, addOperation, reader);
                        }
                        break;
                    case ENABLED_PROTOCOLS: {
                        for (String value : reader.getListAttributeValue(i)) {
                            SSLServerIdentityResourceDefinition.ENABLED_PROTOCOLS.parseAndAddParameterElement(value, addOperation, reader);
                        }
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);
    }

    private void parseKeystore_1_0(final XMLExtendedStreamReader reader, final ModelNode addOperation)
            throws XMLStreamException {

        Set<Attribute> required = EnumSet.of(Attribute.PATH, Attribute.PASSWORD);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case PATH:
                        KeystoreAttributes.KEYSTORE_PATH.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case PASSWORD: {
                        KeystoreAttributes.KEYSTORE_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    case RELATIVE_TO: {
                        KeystoreAttributes.KEYSTORE_RELATIVE_TO.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    private void parseKeystore_1_3(final XMLExtendedStreamReader reader, final ModelNode addOperation, final boolean extended)
            throws XMLStreamException {
        Set<Attribute> required = EnumSet.of(Attribute.PATH, Attribute.KEYSTORE_PASSWORD);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case PATH:
                        KeystoreAttributes.KEYSTORE_PATH.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case PASSWORD: {
                        // TODO - Support for this attribute can later be removed, would suggest removing at the
                        //        start of AS 8 development.
                        ROOT_LOGGER.passwordAttributeDeprecated();
                        required.remove(Attribute.KEYSTORE_PASSWORD);
                        KeystoreAttributes.KEYSTORE_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    case KEYSTORE_PASSWORD: {
                        KeystoreAttributes.KEYSTORE_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    case RELATIVE_TO: {
                        KeystoreAttributes.KEYSTORE_RELATIVE_TO.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    /*
                     * The 'extended' attributes when a true keystore and not just a keystore acting as a truststore.
                     */
                    case ALIAS: {
                        if (extended) {
                            KeystoreAttributes.ALIAS.parseAndSetParameter(value, addOperation, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
                    }
                    case KEY_PASSWORD: {
                        if (extended) {
                            KeystoreAttributes.KEY_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
                    }

                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    private void parseKeystore_1_6_and_2_1(final XMLExtendedStreamReader reader, final ModelNode addOperation, final boolean extended)
            throws XMLStreamException {
        boolean keystorePasswordSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PROVIDER:
                        KeystoreAttributes.KEYSTORE_PROVIDER.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case PATH:
                        KeystoreAttributes.KEYSTORE_PATH.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case KEYSTORE_PASSWORD: {
                        KeystoreAttributes.KEYSTORE_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        keystorePasswordSet = true;
                        break;
                    }
                    case RELATIVE_TO: {
                        KeystoreAttributes.KEYSTORE_RELATIVE_TO.parseAndSetParameter(value, addOperation, reader);
                        break;
                    }
                    /*
                     * The 'extended' attributes when a true keystore and not just a keystore acting as a truststore.
                     */
                    case ALIAS: {
                        if (extended) {
                            KeystoreAttributes.ALIAS.parseAndSetParameter(value, addOperation, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
                    }
                    case KEY_PASSWORD: {
                        if (extended) {
                            KeystoreAttributes.KEY_PASSWORD.parseAndSetParameter(value, addOperation, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
                    }

                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        /*
         * The only mandatory attribute now is the KEYSTORE_PASSWORD.
         */
        if (keystorePasswordSet == false) {
            throw missingRequired(reader, EnumSet.of(Attribute.KEYSTORE_PASSWORD));
        }

        requireNoContent(reader);
    }

    private void parseKerberosIdentity(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        ModelNode kerberos = new ModelNode();
        kerberos.get(OP).set(ADD);
        ModelNode kerberosAddress = realmAddress.clone().add(SERVER_IDENTITY, KERBEROS);
        kerberos.get(OP_ADDR).set(kerberosAddress);
        list.add(kerberos);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case KEYTAB:
                    parseKeyTab(reader, kerberosAddress, list);
                    break;
                default:
                    throw unexpectedElement(reader);
            }

        }

    }

    private void parseKeyTab(final XMLExtendedStreamReader reader,
            final ModelNode parentAddress, final List<ModelNode> list) throws XMLStreamException {

        ModelNode keytab = new ModelNode();
        keytab.get(OP).set(ADD);
        list.add(keytab);

        Set<Attribute> requiredAttributes = EnumSet.of(Attribute.PRINCIPAL, Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case PRINCIPAL:
                        keytab.get(OP_ADDR).set(parentAddress).add(KEYTAB, value);
                        break;
                    case PATH:
                        KeytabResourceDefinition.PATH.parseAndSetParameter(value, keytab, reader);
                        break;
                    case RELATIVE_TO:
                        KeytabResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, keytab, reader);
                        break;
                    case FOR_HOSTS:
                        for (String host : reader.getListAttributeValue(i)) {
                            KeytabResourceDefinition.FOR_HOSTS.parseAndAddParameterElement(host, keytab, reader);
                        }
                        break;
                    case DEBUG:
                        KeytabResourceDefinition.DEBUG.parseAndSetParameter(value, keytab, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);

                }
            }
        }

        // This would pick up if the address for the operation has not yet been set.
        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

    }

    private void parseAuthentication_1_0(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        int userCount = 0;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            // Only a single user element within the authentication element is currently supported.
            if (++userCount > 1) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case LDAP: {
                    parseLdapAuthentication_1_0(reader, realmAddress, list);
                    break;
                }
                case PROPERTIES: {
                    parsePropertiesAuthentication_1_0(reader, realmAddress, list);
                    break;
                }
                case USERS: {
                    parseUsersAuthentication(reader, realmAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        addLegacyLocalAuthentication(realmAddress, list);
    }

    private void parseAuthentication_1_1(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        // Only one truststore can be defined.
        boolean trustStoreFound = false;
        // Only one of ldap, properties or users can be defined.
        boolean usernamePasswordFound = false;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());

            switch (element) {
                case JAAS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseJaasAuthentication_1_1(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case LDAP: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseLdapAuthentication_1_1(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case PROPERTIES: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parsePropertiesAuthentication_1_1(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case TRUSTSTORE: {
                    if (trustStoreFound) {
                        throw unexpectedElement(reader);
                    }
                    parseTruststore(reader, realmAddress, list);
                    trustStoreFound = true;
                    break;
                }
                case USERS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseUsersAuthentication(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        addLegacyLocalAuthentication(realmAddress, list);
    }

    private void parseAuthentication_1_3(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        // Only one truststore can be defined.
        boolean trustStoreFound = false;
        // Only one local can be defined.
        boolean localFound = false;
        // Only one of ldap, properties or users can be defined.
        boolean usernamePasswordFound = false;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());

            switch (element) {
                case JAAS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }

                    switch (namespace) {
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                        case DOMAIN_1_5:
                            parseJaasAuthentication_1_1(reader, realmAddress, list);
                            break;
                        default:
                            switch (namespace.getMajorVersion()) {
                                case 2: // i.e. 2.0 up to but not including 3.0
                                    parseJaasAuthentication_1_1(reader, realmAddress, list);
                                    break;
                                default: // i.e. 1 and 3, most specifically 1.6 and onwards and 3.0 and onwards.
                                    parseJaasAuthentication_1_6_and_3_0(reader, realmAddress, list);
                                    break;
                            }
                    }
                    usernamePasswordFound = true;
                    break;
                }
                case LDAP: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    // This method is specific to version 1.3 of the schema and beyond - no need to
                    // consider namespaces before this point.
                    switch (namespace) {
                        case DOMAIN_1_3:
                            parseLdapAuthentication_1_1(reader, realmAddress, list);
                            break;
                        default:
                            switch (namespace.getMajorVersion()) {
                                case 1:
                                    parseLdapAuthentication_1_4(reader, realmAddress, list);
                                    break;
                                default:
                                    parseLdapAuthentication_1_7_and_2_0(reader, realmAddress, list);
                                    break;
                            }

                    }
                    usernamePasswordFound = true;
                    break;
                }
                case PROPERTIES: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parsePropertiesAuthentication_1_1(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case TRUSTSTORE: {
                    if (trustStoreFound) {
                        throw unexpectedElement(reader);
                    }
                    parseTruststore(reader, realmAddress, list);
                    trustStoreFound = true;
                    break;
                }
                case USERS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseUsersAuthentication(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case PLUG_IN: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    ModelNode parentAddress = realmAddress.clone().add(AUTHENTICATION);
                    parsePlugIn_Authentication(reader, parentAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case LOCAL: {
                    if (localFound) {
                        throw unexpectedElement(reader);
                    }

                    // We know Version 1.7 and onwards and 3.0 and onwards will not result in calls to this method.
                    switch (namespace) {
                        case DOMAIN_1_3:
                        case DOMAIN_1_4:
                        case DOMAIN_1_5:
                        case DOMAIN_2_0:
                            parseLocalAuthentication_1_3(reader, realmAddress, list);
                            break;
                        default: // Becomes Versions 1.6 then 2.1 and onwards.
                            parseLocalAuthentication_1_6_and_2_1(reader, realmAddress, list);
                            break;
                    }

                    localFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseAuthentication_1_7_and_3_0(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        // Only one truststore can be defined.
        boolean trustStoreFound = false;
        // Only one local can be defined.
        boolean localFound = false;
        // Only one kerberos can be defined.
        boolean kerberosFound = false;
        // Only one of ldap, properties or users can be defined.
        boolean usernamePasswordFound = false;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());

            switch (element) {
                case JAAS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseJaasAuthentication_1_6_and_3_0(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case KERBEROS: {
                    if (kerberosFound) {
                        throw unexpectedElement(reader);
                    }
                    parseKerberosAuthentication_3_0(reader, realmAddress, list);
                    kerberosFound = true;
                    break;
                }
                case LDAP: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseLdapAuthentication_1_7_and_2_0(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case PROPERTIES: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parsePropertiesAuthentication_1_1(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case TRUSTSTORE: {
                    if (trustStoreFound) {
                        throw unexpectedElement(reader);
                    }
                    parseTruststore(reader, realmAddress, list);
                    trustStoreFound = true;
                    break;
                }
                case USERS: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseUsersAuthentication(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case PLUG_IN: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    ModelNode parentAddress = realmAddress.clone().add(AUTHENTICATION);
                    parsePlugIn_Authentication(reader, parentAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case LOCAL: {
                    if (localFound) {
                        throw unexpectedElement(reader);
                    }
                    parseLocalAuthentication_1_6_and_2_1(reader, realmAddress, list);
                    localFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseKerberosAuthentication_3_0(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, KERBEROS);
        ModelNode kerberos = Util.getEmptyOperation(ADD, addr);
        list.add(kerberos);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case REMOVE_REALM:
                        KerberosAuthenticationResourceDefinition.REMOVE_REALM.parseAndSetParameter(value, kerberos, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);
    }

    private void parseJaasAuthentication_1_1(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, JAAS);
        ModelNode jaas = Util.getEmptyOperation(ADD, addr);
        list.add(jaas);

        boolean nameFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        if (nameFound) {
                            throw unexpectedAttribute(reader, i);
                        }
                        nameFound = true;
                        JaasAuthenticationResourceDefinition.NAME.parseAndSetParameter(value, jaas, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        if (nameFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        requireNoContent(reader);
    }

    private void parseJaasAuthentication_1_6_and_3_0(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, JAAS);
        ModelNode jaas = Util.getEmptyOperation(ADD, addr);
        list.add(jaas);

        boolean nameFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        if (nameFound) {
                            throw unexpectedAttribute(reader, i);
                        }
                        nameFound = true;
                        JaasAuthenticationResourceDefinition.NAME.parseAndSetParameter(value, jaas, reader);
                        break;
                    case ASSIGN_GROUPS:
                        JaasAuthenticationResourceDefinition.ASSIGN_GROUPS.parseAndSetParameter(value, jaas, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        if (nameFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        requireNoContent(reader);
    }

    private void parseLdapAuthentication_1_0(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {

        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LDAP);
        ModelNode ldapAuthentication = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthentication);

        Set<Attribute> required = EnumSet.of(Attribute.CONNECTION, Attribute.BASE_DN, Attribute.USERNAME_ATTRIBUTE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case CONNECTION: {
                        LdapAuthenticationResourceDefinition.CONNECTION.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case BASE_DN: {
                        LdapAuthenticationResourceDefinition.BASE_DN.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case USERNAME_ATTRIBUTE: {
                        LdapAuthenticationResourceDefinition.USERNAME_FILTER.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case RECURSIVE: {
                        LdapAuthenticationResourceDefinition.RECURSIVE.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case USER_DN: {
                        LdapAuthenticationResourceDefinition.USER_DN.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0)
            throw missingRequired(reader, required);

        requireNoContent(reader);
    }

    private void parseLdapAuthentication_1_1(final XMLExtendedStreamReader reader,
                                             final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LDAP);
        ModelNode ldapAuthentication = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthentication);

        Set<Attribute> required = EnumSet.of(Attribute.CONNECTION, Attribute.BASE_DN);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case CONNECTION: {
                        LdapAuthenticationResourceDefinition.CONNECTION.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case BASE_DN: {
                        LdapAuthenticationResourceDefinition.BASE_DN.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case RECURSIVE: {
                        LdapAuthenticationResourceDefinition.RECURSIVE.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    case USER_DN: {
                        LdapAuthenticationResourceDefinition.USER_DN.parseAndSetParameter(value, ldapAuthentication, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        boolean choiceFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            if (choiceFound) {
                throw unexpectedElement(reader);
            }
            choiceFound = true;
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADVANCED_FILTER:
                    String filter = readStringAttributeElement(reader, Attribute.FILTER.getLocalName());
                    LdapAuthenticationResourceDefinition.ADVANCED_FILTER.parseAndSetParameter(filter, ldapAuthentication, reader);
                    break;
                case USERNAME_FILTER: {
                    String usernameAttr = readStringAttributeElement(reader, Attribute.ATTRIBUTE.getLocalName());
                    LdapAuthenticationResourceDefinition.USERNAME_FILTER.parseAndSetParameter(usernameAttr, ldapAuthentication, reader);
                    break;
                }

                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!choiceFound) {
            throw missingOneOf(reader, EnumSet.of(Element.ADVANCED_FILTER, Element.USERNAME_FILTER));
        }
    }

    private void parseLdapAuthenticationAttributes_1_4(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        Set<Attribute> required = EnumSet.of(Attribute.CONNECTION, Attribute.BASE_DN);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case CONNECTION: {
                        LdapAuthenticationResourceDefinition.CONNECTION.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case BASE_DN: {
                        LdapAuthenticationResourceDefinition.BASE_DN.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case RECURSIVE: {
                        LdapAuthenticationResourceDefinition.RECURSIVE.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case USER_DN: {
                        LdapAuthenticationResourceDefinition.USER_DN.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case ALLOW_EMPTY_PASSWORDS: {
                        LdapAuthenticationResourceDefinition.ALLOW_EMPTY_PASSWORDS.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }
    }

    private void parseLdapAuthenticationAttributes_2_0(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        Set<Attribute> required = EnumSet.of(Attribute.CONNECTION, Attribute.BASE_DN);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case CONNECTION: {
                        LdapAuthenticationResourceDefinition.CONNECTION.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case BASE_DN: {
                        LdapAuthenticationResourceDefinition.BASE_DN.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case RECURSIVE: {
                        LdapAuthenticationResourceDefinition.RECURSIVE.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case USER_DN: {
                        LdapAuthenticationResourceDefinition.USER_DN.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case ALLOW_EMPTY_PASSWORDS: {
                        LdapAuthenticationResourceDefinition.ALLOW_EMPTY_PASSWORDS.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    case USERNAME_LOAD: {
                        LdapAuthenticationResourceDefinition.USERNAME_LOAD.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }
    }

    private void parseLdapAuthentication_1_4(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LDAP);
        ModelNode ldapAuthentication = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthentication);

        parseLdapAuthenticationAttributes_1_4(reader, ldapAuthentication);

        boolean choiceFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            if (choiceFound) {
                throw unexpectedElement(reader);
            }
            choiceFound = true;
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADVANCED_FILTER:
                    String filter = readStringAttributeElement(reader, Attribute.FILTER.getLocalName());
                    LdapAuthenticationResourceDefinition.ADVANCED_FILTER.parseAndSetParameter(filter, ldapAuthentication,
                            reader);
                    break;
                case USERNAME_FILTER: {
                    String usernameAttr = readStringAttributeElement(reader, Attribute.ATTRIBUTE.getLocalName());
                    LdapAuthenticationResourceDefinition.USERNAME_FILTER.parseAndSetParameter(usernameAttr, ldapAuthentication,
                            reader);
                    break;
                }

                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!choiceFound) {
            throw missingOneOf(reader, EnumSet.of(Element.ADVANCED_FILTER, Element.USERNAME_FILTER));
        }
    }



    private void parseLdapAuthentication_1_7_and_2_0(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LDAP);
        ModelNode ldapAuthentication = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthentication);

        switch (namespace.getMajorVersion()) {
            case 1:
                parseLdapAuthenticationAttributes_1_4(reader, ldapAuthentication);
                break;
            default:
                parseLdapAuthenticationAttributes_2_0(reader, ldapAuthentication);
                break;
        }

        ModelNode addLdapCache = null;
        boolean choiceFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            if (choiceFound) {
                throw unexpectedElement(reader);
            }
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case CACHE:
                    if (addLdapCache != null) {
                        throw unexpectedElement(reader);
                    }
                    addLdapCache = parseLdapCache(reader);
                    break;
                case ADVANCED_FILTER:
                    choiceFound = true;
                    String filter = readStringAttributeElement(reader, Attribute.FILTER.getLocalName());
                    LdapAuthenticationResourceDefinition.ADVANCED_FILTER.parseAndSetParameter(filter, ldapAuthentication,
                            reader);
                    break;
                case USERNAME_FILTER: {
                    choiceFound = true;
                    String usernameAttr = readStringAttributeElement(reader, Attribute.ATTRIBUTE.getLocalName());
                    LdapAuthenticationResourceDefinition.USERNAME_FILTER.parseAndSetParameter(usernameAttr, ldapAuthentication,
                            reader);
                    break;
                }

                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!choiceFound) {
            throw missingOneOf(reader, EnumSet.of(Element.ADVANCED_FILTER, Element.USERNAME_FILTER));
        }

        if (addLdapCache != null) {
            correctCacheAddress(ldapAuthentication, addLdapCache);
            list.add(addLdapCache);
        }
    }

    private ModelNode parseLdapCache(final XMLExtendedStreamReader reader) throws XMLStreamException {
        ModelNode addr = new ModelNode();
        ModelNode addCacheOp = Util.getEmptyOperation(ADD, addr);

        String type = BY_SEARCH_TIME;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case TYPE: {
                        if (BY_ACCESS_TIME.equals(value) || BY_SEARCH_TIME.equals(value)) {
                            type = value;
                        } else {
                            throw invalidAttributeValue(reader, i);
                        }
                        break;
                    }
                    case EVICTION_TIME: {
                        LdapCacheResourceDefinition.EVICTION_TIME.parseAndSetParameter(value, addCacheOp, reader);
                        break;
                    }
                    case CACHE_FAILURES: {
                        LdapCacheResourceDefinition.CACHE_FAILURES.parseAndSetParameter(value, addCacheOp, reader);
                        break;
                    }
                    case MAX_CACHE_SIZE: {
                        LdapCacheResourceDefinition.MAX_CACHE_SIZE.parseAndSetParameter(value, addCacheOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);
        addCacheOp.get(OP_ADDR).add(CACHE, type);
        return addCacheOp;
    }

    private void correctCacheAddress(ModelNode parentAdd, ModelNode cacheAdd) {
        List<Property> addressList = cacheAdd.get(OP_ADDR).asPropertyList();
        ModelNode cacheAddress = parentAdd.get(OP_ADDR).clone();
        for (Property current : addressList) {
            cacheAddress.add(current.getName(), current.getValue().asString());
        }

        cacheAdd.get(OP_ADDR).set(cacheAddress);
    }

    private void addLegacyLocalAuthentication(final ModelNode realmAddress, final List<ModelNode> list) {
        /*
         * Before version 1.3 of the domain schema there was no configuration for the local mechanism, however it was always
         * enabled - this adds an arbitrary add local op to recreate this behaviour in the older schema versions.
         */
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LOCAL);
        ModelNode local = Util.getEmptyOperation(ADD, addr);
        local.get(DEFAULT_USER).set(DEFAULT_DEFAULT_USER);
        list.add(local);
    }

    private void parseLocalAuthentication_1_3(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LOCAL);
        ModelNode local = Util.getEmptyOperation(ADD, addr);
        list.add(local);

        final int count = reader.getAttributeCount();
        Set<Attribute> attributesFound = new HashSet<Attribute>(count);

        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                if (attributesFound.contains(attribute)) {
                    throw unexpectedAttribute(reader, i);
                }
                attributesFound.add(attribute);

                switch (attribute) {
                    case DEFAULT_USER:
                        LocalAuthenticationResourceDefinition.DEFAULT_USER.parseAndSetParameter(value, local, reader);
                        break;
                    case ALLOWED_USERS:
                        LocalAuthenticationResourceDefinition.ALLOWED_USERS.parseAndSetParameter(value, local, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        // All attributes are optional.

        requireNoContent(reader);
    }

    private void parseLocalAuthentication_1_6_and_2_1(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LOCAL);
        ModelNode local = Util.getEmptyOperation(ADD, addr);
        list.add(local);

        final int count = reader.getAttributeCount();
        Set<Attribute> attributesFound = new HashSet<Attribute>(count);

        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                if (attributesFound.contains(attribute)) {
                    throw unexpectedAttribute(reader, i);
                }
                attributesFound.add(attribute);

                switch (attribute) {
                    case DEFAULT_USER:
                        LocalAuthenticationResourceDefinition.DEFAULT_USER.parseAndSetParameter(value, local, reader);
                        break;
                    case ALLOWED_USERS:
                        LocalAuthenticationResourceDefinition.ALLOWED_USERS.parseAndSetParameter(value, local, reader);
                        break;
                    case SKIP_GROUP_LOADING:
                        LocalAuthenticationResourceDefinition.SKIP_GROUP_LOADING.parseAndSetParameter(value, local, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        // All attributes are optional.

        requireNoContent(reader);
    }

    private void parsePropertiesAuthentication_1_0(final XMLExtendedStreamReader reader,
                                                   final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, PROPERTIES);
        ModelNode properties = Util.getEmptyOperation(ADD, addr);

        list.add(properties);

        String path = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PATH:
                        path = value;
                        PropertiesAuthenticationResourceDefinition.PATH.parseAndSetParameter(value, properties, reader);
                        break;
                    case RELATIVE_TO: {
                        PropertiesAuthenticationResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, properties, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (path == null)
            throw missingRequired(reader, Collections.singleton(Attribute.PATH));

        requireNoContent(reader);
        // This property was not supported in version 1.0 of the schema, however it is set to true here to ensure
        // the default behaviour if a document based on 1.0 of the schema is parsed, 1.1 now defaults this to false.
        properties.get(PLAIN_TEXT).set(true);
    }

    private void parsePropertiesAuthentication_1_1(final XMLExtendedStreamReader reader,
                                                   final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, PROPERTIES);
        ModelNode properties = Util.getEmptyOperation(ADD, addr);
        list.add(properties);

        String path = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PATH:
                        path = value;
                        PropertiesAuthenticationResourceDefinition.PATH.parseAndSetParameter(value, properties, reader);
                        break;
                    case RELATIVE_TO: {
                        PropertiesAuthenticationResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, properties, reader);
                        break;
                    }
                    case PLAIN_TEXT: {
                        PropertiesAuthenticationResourceDefinition.PLAIN_TEXT.parseAndSetParameter(value, properties, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (path == null)
            throw missingRequired(reader, Collections.singleton(Attribute.PATH));

        requireNoContent(reader);
    }

    // The users element defines users within the domain model, it is a simple authentication for some out of the box users.
    private void parseUsersAuthentication(final XMLExtendedStreamReader reader,
                                          final ModelNode realmAddress, final List<ModelNode> list)
            throws XMLStreamException {
        final ModelNode usersAddress = realmAddress.clone().add(AUTHENTICATION, USERS);
        list.add(Util.getEmptyOperation(ADD, usersAddress));

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case USER: {
                    parseUser(reader, usersAddress, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseUser(final XMLExtendedStreamReader reader,
                           final ModelNode usersAddress, final List<ModelNode> list) throws XMLStreamException {
        requireSingleAttribute(reader, Attribute.USERNAME.getLocalName());
        // After double checking the name of the only attribute we can retrieve it.
        final String userName = reader.getAttributeValue(0);
        final ModelNode userAddress = usersAddress.clone().add(USER, userName);
        ModelNode user = Util.getEmptyOperation(ADD, userAddress);

        list.add(user);

        String password = null;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PASSWORD: {
                    password = reader.getElementText();
                    UserResourceDefinition.PASSWORD.parseAndSetParameter(password, user, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (password == null) {
            throw missingRequiredElement(reader, EnumSet.of(Element.PASSWORD));
        }
    }

    private void parseTruststore(final XMLExtendedStreamReader reader, final ModelNode realmAddress,
                                 final List<ModelNode> list) throws XMLStreamException {
        final ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(realmAddress).add(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.TRUSTSTORE);

        switch (namespace) {
            case DOMAIN_1_0:
            case DOMAIN_1_1:
            case DOMAIN_1_2:
                parseKeystore_1_0(reader, op);
                break;
            case DOMAIN_1_3:
            case DOMAIN_1_4:
            case DOMAIN_1_5:
            case DOMAIN_2_0:
                parseKeystore_1_3(reader, op, false);
                break;
            default:
                // Most recent versions for 1.x, 2.x and 3.x streams converge at this point.
                parseKeystore_1_6_and_2_1(reader, op, false);
        }

        list.add(op);
    }

    private void parseAuthorization_1_1(final XMLExtendedStreamReader reader,
            final ModelNode realmAdd, final List<ModelNode> list) throws XMLStreamException {
        ModelNode realmAddress = realmAdd.get(OP_ADDR);

        boolean authzFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            // Only a single element within the authorization element is currently supported.
            if (authzFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PROPERTIES: {
                    parsePropertiesAuthorization(reader, realmAddress, list);
                    authzFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }
    }

    private void parseAuthorization_1_3(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

        boolean authzFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            // Only a single element within the authorization element is currently supported.
            if (authzFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PROPERTIES: {
                    parsePropertiesAuthorization(reader, realmAddress, list);
                    authzFound = true;
                    break;
                }
                case PLUG_IN: {
                    ModelNode parentAddress = realmAddress.clone().add(AUTHORIZATION);
                    parsePlugIn_Authorization(reader, parentAddress, list);
                    authzFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }
    }

    private void parseAuthorization_1_5_and_2_0(final XMLExtendedStreamReader reader,
            final ModelNode realmAdd, final List<ModelNode> list) throws XMLStreamException {
        ModelNode realmAddress = realmAdd.get(OP_ADDR);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case MAP_GROUPS_TO_ROLES:
                        SecurityRealmResourceDefinition.MAP_GROUPS_TO_ROLES.parseAndSetParameter(value, realmAdd, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        boolean authzFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            // Only a single element within the authorization element is currently supported.
            if (authzFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PROPERTIES: {
                    parsePropertiesAuthorization(reader, realmAddress, list);
                    authzFound = true;
                    break;
                }
                case PLUG_IN: {
                    ModelNode parentAddress = realmAddress.clone().add(AUTHORIZATION);
                    parsePlugIn_Authorization(reader, parentAddress, list);
                    authzFound = true;
                    break;
                }
                case LDAP: {
                    parseLdapAuthorization_1_5(reader, realmAddress, list);
                    authzFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }
    }

    // 1.5 and on, 2.0 and on, 3.0 and on.
    private void parseLdapAuthorization_1_5(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHORIZATION, LDAP);
        ModelNode ldapAuthorization = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthorization);

        Set<Attribute> required = EnumSet.of(Attribute.CONNECTION);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                required.remove(attribute);
                switch (attribute) {
                    case CONNECTION: {
                        LdapAuthorizationResourceDefinition.CONNECTION.parseAndSetParameter(value, ldapAuthorization, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (required.isEmpty() == false) {
            throw missingRequired(reader, required);
        }

        Set<Element> foundElements = new HashSet<Element>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            if (foundElements.add(element) == false) {
                throw unexpectedElement(reader); // Only one of each allowed.
            }
            switch (element) {
                case USERNAME_TO_DN: {
                    switch (namespace.getMajorVersion()) {
                        case 1: // 1.5 up to but not including 2.0
                            parseUsernameToDn_1_5(reader, addr, list);
                            break;
                        default: // 2.0 and onwards
                            parseUsernameToDn_2_0(reader, addr, list);
                            break;
                    }

                    break;
                }
                case GROUP_SEARCH: {
                    switch (namespace) {
                        case DOMAIN_1_5:
                        case DOMAIN_1_6:
                            parseGroupSearch_1_5(reader, addr, list);
                            break;
                        default:
                            parseGroupSearch_1_7_and_2_0(reader, addr, list);
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

    private void parseUsernameToDn_1_5(final XMLExtendedStreamReader reader,
            final ModelNode ldapAddress, final List<ModelNode> list) throws XMLStreamException {
        // Add operation to be defined by parsing a child element, however the attribute FORCE is common here.
        final ModelNode childAdd = new ModelNode();
        childAdd.get(OP).set(ADD);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case FORCE:
                        BaseLdapUserSearchResource.FORCE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        boolean filterFound = false;
        ModelNode address = ldapAddress.clone().add(USERNAME_TO_DN);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            if (filterFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case USERNAME_IS_DN:
                    filterFound = true;
                    parseUsernameIsDn(reader, address, childAdd);
                    break;
                case USERNAME_FILTER:
                    filterFound = true;
                    parseUsernameFilter(reader, address, childAdd);
                    break;
                case ADVANCED_FILTER:
                    filterFound = true;
                    parseAdvancedFilter(reader, address, childAdd);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }

        if (filterFound == false) {
            throw missingOneOf(reader, EnumSet.of(Element.USERNAME_IS_DN, Element.USERNAME_FILTER, Element.ADVANCED_FILTER));
        }

        list.add(childAdd);
    }

    private void parseUsernameToDn_2_0(final XMLExtendedStreamReader reader,
            final ModelNode ldapAddress, final List<ModelNode> list) throws XMLStreamException {
        // Add operation to be defined by parsing a child element, however the attribute FORCE is common here.
        final ModelNode childAdd = new ModelNode();
        childAdd.get(OP).set(ADD);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case FORCE:
                        BaseLdapUserSearchResource.FORCE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        boolean filterFound = false;
        ModelNode cacheAdd = null;
        ModelNode address = ldapAddress.clone().add(USERNAME_TO_DN);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            if (filterFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case CACHE:
                    if (cacheAdd != null) {
                        throw unexpectedElement(reader);
                    }
                    cacheAdd = parseLdapCache(reader);
                    break;
                case USERNAME_IS_DN:
                    filterFound = true;
                    parseUsernameIsDn(reader, address, childAdd);
                    break;
                case USERNAME_FILTER:
                    filterFound = true;
                    parseUsernameFilter(reader, address, childAdd);
                    break;
                case ADVANCED_FILTER:
                    filterFound = true;
                    parseAdvancedFilter(reader, address, childAdd);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }

        if (filterFound == false) {
            throw missingOneOf(reader, EnumSet.of(Element.USERNAME_IS_DN, Element.USERNAME_FILTER, Element.ADVANCED_FILTER));
        }

        list.add(childAdd);
        if (cacheAdd != null) {
            correctCacheAddress(childAdd, cacheAdd);
            list.add(cacheAdd);
        }
    }

    private void parseUsernameIsDn(final XMLExtendedStreamReader reader,
            final ModelNode parentAddress, final ModelNode addOp) throws XMLStreamException {
        requireNoAttributes(reader);
        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(USERNAME_IS_DN));
    }

    private void parseUsernameFilter(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        boolean baseDnFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case BASE_DN: {
                        baseDnFound = true;
                        UserSearchResourceDefintion.BASE_DN.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case RECURSIVE: {
                        UserSearchResourceDefintion.RECURSIVE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case USER_DN_ATTRIBUTE: {
                        UserSearchResourceDefintion.USER_DN_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case ATTRIBUTE: {
                        UserSearchResourceDefintion.ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (baseDnFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.BASE_DN));
        }

        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(USERNAME_FILTER));
    }

    private void parseAdvancedFilter(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        boolean baseDnFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case BASE_DN: {
                        baseDnFound = true;
                        AdvancedUserSearchResourceDefintion.BASE_DN.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case RECURSIVE: {
                        AdvancedUserSearchResourceDefintion.RECURSIVE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case USER_DN_ATTRIBUTE: {
                        UserSearchResourceDefintion.USER_DN_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case FILTER: {
                        AdvancedUserSearchResourceDefintion.FILTER.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (baseDnFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.BASE_DN));
        }

        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(ADVANCED_FILTER));
    }

    // 1.5 and 1.6 ONLY
    private void parseGroupSearch_1_5(final XMLExtendedStreamReader reader,
            final ModelNode ldapAddress, final List<ModelNode> list) throws XMLStreamException {
        // Add operation to be defined by parsing a child element, however the attribute FORCE is common here.
        final ModelNode childAdd = new ModelNode();
        childAdd.get(OP).set(ADD);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case GROUP_NAME:
                        BaseLdapGroupSearchResource.GROUP_NAME.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case ITERATIVE:
                        BaseLdapGroupSearchResource.ITERATIVE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case GROUP_DN_ATTRIBUTE:
                        BaseLdapGroupSearchResource.GROUP_DN_ATTRIBUTE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case GROUP_NAME_ATTRIBUTE:
                        BaseLdapGroupSearchResource.GROUP_NAME_ATTRIBUTE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        boolean filterFound = false;
        ModelNode address = ldapAddress.clone().add(GROUP_SEARCH);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            if (filterFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case GROUP_TO_PRINCIPAL:
                    filterFound = true;
                    parseGroupToPrincipal(reader, address, childAdd);
                    break;
                case PRINCIPAL_TO_GROUP:
                    filterFound = true;
                    switch (namespace) {
                        case DOMAIN_1_5:
                            parsePrincipalToGroup_1_5(reader, address, childAdd);
                            break;
                        default:
                            // 1.6 and 2.1 converge at this point.
                            parsePrincipalToGroup_1_6_and_2_1(reader, address, childAdd);
                            break;
                    }

                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (filterFound == false) {
            throw missingOneOf(reader, EnumSet.of(Element.GROUP_TO_PRINCIPAL, Element.PRINCIPAL_TO_GROUP));
        }

        list.add(childAdd);
    }

    private void parseGroupSearch_1_7_and_2_0(final XMLExtendedStreamReader reader,
            final ModelNode ldapAddress, final List<ModelNode> list) throws XMLStreamException {
        // Add operation to be defined by parsing a child element, however the attribute FORCE is common here.
        final ModelNode childAdd = new ModelNode();
        childAdd.get(OP).set(ADD);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case GROUP_NAME:
                        BaseLdapGroupSearchResource.GROUP_NAME.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case ITERATIVE:
                        BaseLdapGroupSearchResource.ITERATIVE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case GROUP_DN_ATTRIBUTE:
                        BaseLdapGroupSearchResource.GROUP_DN_ATTRIBUTE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    case GROUP_NAME_ATTRIBUTE:
                        BaseLdapGroupSearchResource.GROUP_NAME_ATTRIBUTE.parseAndSetParameter(value, childAdd, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        boolean filterFound = false;
        ModelNode cacheAdd = null;
        ModelNode address = ldapAddress.clone().add(GROUP_SEARCH);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            if (filterFound) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case CACHE:
                    if (cacheAdd != null) {
                        throw unexpectedElement(reader);
                    }
                    cacheAdd = parseLdapCache(reader);
                    break;
                case GROUP_TO_PRINCIPAL:
                    filterFound = true;
                    parseGroupToPrincipal(reader, address, childAdd);
                    break;
                case PRINCIPAL_TO_GROUP:
                    filterFound = true;
                    // 1.7, 2.0, 2.1, 3.0
                    switch (namespace) {
                        case DOMAIN_2_0:
                            parsePrincipalToGroup_1_5(reader, address, childAdd);
                            break;
                        default:
                            switch (namespace.getMajorVersion()) {
                                case 2:
                                    parsePrincipalToGroup_1_6_and_2_1(reader, address, childAdd);
                                    break;
                                default: // i.e. 1.7 onwards and 3.0 onwards
                                    parsePrincipalToGroup_1_7_and_3_0(reader, address, childAdd);
                                    break;
                            }
                    }

                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (filterFound == false) {
            throw missingOneOf(reader, EnumSet.of(Element.GROUP_TO_PRINCIPAL, Element.PRINCIPAL_TO_GROUP));
        }

        list.add(childAdd);
        if (cacheAdd != null) {
            correctCacheAddress(childAdd, cacheAdd);
            list.add(cacheAdd);
        }
    }

    private void parseGroupToPrincipalAttributes_1_5_and_2_0(final XMLExtendedStreamReader reader, final ModelNode addOp) throws XMLStreamException {
        boolean baseDnFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case BASE_DN: {
                        baseDnFound = true;
                        GroupToPrincipalResourceDefinition.BASE_DN.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case RECURSIVE: {
                        GroupToPrincipalResourceDefinition.RECURSIVE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SEARCH_BY:
                        GroupToPrincipalResourceDefinition.SEARCH_BY.parseAndSetParameter(value, addOp, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (baseDnFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.BASE_DN));
        }
    }

    private void parseGroupToPrincipalAttributes_1_6_and_3_0(final XMLExtendedStreamReader reader, final ModelNode addOp) throws XMLStreamException {
        boolean baseDnFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case BASE_DN: {
                        baseDnFound = true;
                        GroupToPrincipalResourceDefinition.BASE_DN.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case RECURSIVE: {
                        GroupToPrincipalResourceDefinition.RECURSIVE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SEARCH_BY:
                        GroupToPrincipalResourceDefinition.SEARCH_BY.parseAndSetParameter(value, addOp, reader);
                        break;
                    case PREFER_ORIGINAL_CONNECTION:
                        GroupToPrincipalResourceDefinition.PREFER_ORIGINAL_CONNECTION.parseAndSetParameter(value, addOp, reader);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (baseDnFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.BASE_DN));
        }
    }

    private void parseGroupToPrincipal(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        switch (namespace) {
            case DOMAIN_1_5:
                parseGroupToPrincipalAttributes_1_5_and_2_0(reader, addOp);
                break;
            default:
                switch (namespace.getMajorVersion()) {
                    case 2:
                        parseGroupToPrincipalAttributes_1_5_and_2_0(reader, addOp);
                        break;
                    default: // i.e. 1.6 onwards and 3.0 onwards.
                        parseGroupToPrincipalAttributes_1_6_and_3_0(reader, addOp);
                        break;
                }
        }

        boolean elementFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);

            final Element element = Element.forName(reader.getLocalName());
            if (elementFound) {
                throw unexpectedElement(reader);
            }
            elementFound = true;
            switch (element) {
                case MEMBERSHIP_FILTER:
                    parseMembershipFilter(reader, addOp);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        addOp.get(OP_ADDR).set(parentAddress.clone().add(GROUP_TO_PRINCIPAL));
    }

    private void parseMembershipFilter(final XMLExtendedStreamReader reader,
            final ModelNode addOp) throws XMLStreamException {
        boolean principalAttribute = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PRINCIPAL_ATTRIBUTE: {
                        principalAttribute = true;
                        GroupToPrincipalResourceDefinition.PRINCIPAL_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (principalAttribute == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.PRINCIPAL_ATTRIBUTE));
        }

        requireNoContent(reader);
    }

    private void parsePrincipalToGroup_1_5(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case GROUP_ATTRIBUTE: {
                        PrincipalToGroupResourceDefinition.GROUP_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(PRINCIPAL_TO_GROUP));
    }

    private void parsePrincipalToGroup_1_6_and_2_1(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case GROUP_ATTRIBUTE: {
                        PrincipalToGroupResourceDefinition.GROUP_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case PREFER_ORIGINAL_CONNECTION: {
                        PrincipalToGroupResourceDefinition.PREFER_ORIGINAL_CONNECTION.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(PRINCIPAL_TO_GROUP));
    }

    private void parsePrincipalToGroup_1_7_and_3_0(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
            final ModelNode addOp) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case GROUP_ATTRIBUTE: {
                        PrincipalToGroupResourceDefinition.GROUP_ATTRIBUTE.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case PREFER_ORIGINAL_CONNECTION: {
                        PrincipalToGroupResourceDefinition.PREFER_ORIGINAL_CONNECTION.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case SKIP_MISSING_GROUPS: {
                        PrincipalToGroupResourceDefinition.SKIP_MISSING_GROUPS.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        requireNoContent(reader);

        addOp.get(OP_ADDR).set(parentAddress.clone().add(PRINCIPAL_TO_GROUP));
    }

    private void parsePropertiesAuthorization(final XMLExtendedStreamReader reader, final ModelNode realmAddress,
            final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHORIZATION, PROPERTIES);
        ModelNode properties = Util.getEmptyOperation(ADD, addr);
        list.add(properties);

        String path = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PATH:
                        path = value;
                        PropertiesAuthorizationResourceDefinition.PATH.parseAndSetParameter(value, properties, reader);
                        break;
                    case RELATIVE_TO: {
                        PropertiesAuthorizationResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, properties, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (path == null)
            throw missingRequired(reader, Collections.singleton(Attribute.PATH));

        requireNoContent(reader);
    }

    private void parsePlugIn_Authentication(final XMLExtendedStreamReader reader,
            final ModelNode parentAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = parentAddress.clone().add(PLUG_IN);
        ModelNode plugIn = Util.getEmptyOperation(ADD, addr);
        list.add(plugIn);

        boolean nameFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        PlugInAuthenticationResourceDefinition.NAME.parseAndSetParameter(value, plugIn, reader);
                        nameFound = true;
                        break;
                    case MECHANISM: {
                        PlugInAuthenticationResourceDefinition.MECHANISM.parseAndSetParameter(value, plugIn, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (nameFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTIES: {
                    while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                        requireNamespace(reader, namespace);
                        final Element propertyElement = Element.forName(reader.getLocalName());
                        switch (propertyElement) {
                            case PROPERTY:
                                parseProperty(reader, addr, list);
                                break;
                            default:
                                throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parsePlugIn_Authorization(final XMLExtendedStreamReader reader,
            final ModelNode parentAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = parentAddress.clone().add(PLUG_IN);
        ModelNode plugIn = Util.getEmptyOperation(ADD, addr);
        list.add(plugIn);

        requireSingleAttribute(reader, Attribute.NAME.getLocalName());
        // After double checking the name of the only attribute we can retrieve it.
        final String plugInName = reader.getAttributeValue(0);
        plugIn.get(NAME).set(plugInName);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PROPERTIES: {
                    while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                        requireNamespace(reader, namespace);
                        final Element propertyElement = Element.forName(reader.getLocalName());
                        switch (propertyElement) {
                            case PROPERTY:
                                parseProperty(reader, addr, list);
                                break;
                            default:
                                throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseProperty(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final List<ModelNode> list)
            throws XMLStreamException {

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        list.add(add);

        boolean addressFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        add.get(OP_ADDR).set(parentAddress).add(PROPERTY, value);
                        addressFound = true;
                        break;
                    case VALUE: {
                        PropertyResourceDefinition.VALUE.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (addressFound == false) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        requireNoContent(reader);
    }

}
