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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADVANCED_FILTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED_CIPHER_SUITES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED_PROTOCOLS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_SEARCH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_TO_PRINCIPAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP_CONNECTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAP_GROUPS_TO_ROLES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_REMOTING_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRINCIPAL_TO_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECRET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
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
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_ACCESS_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_SEARCH_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.CACHE;
import static org.jboss.as.domain.management.ModelDescriptionConstants.JAAS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.JKS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KERBEROS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KEYSTORE_PROVIDER;
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
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.access.AccessIdentityResourceDefinition;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionPropertyResourceDefinition;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition;
import org.jboss.as.domain.management.security.AbstractPlugInAuthResourceDefinition;
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
import org.jboss.as.domain.management.security.UserIsDnResourceDefintion;
import org.jboss.as.domain.management.security.UserResourceDefinition;
import org.jboss.as.domain.management.security.UserSearchResourceDefintion;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
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
class ManagementXml_5 extends ManagementXml {

    private final Namespace namespace;
    private final ManagementXmlDelegate delegate;


    ManagementXml_5(final Namespace namespace, final ManagementXmlDelegate delegate) {
        this.namespace = namespace;
        this.delegate = delegate;
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

    private void parseOutboundConnections(final XMLExtendedStreamReader reader, final ModelNode address,
                                          final List<ModelNode> list) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LDAP: {
                    parseLdapConnection(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseLdapConnection(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
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
                    case ALWAYS_SEND_CLIENT_CERT: {
                        LdapConnectionResourceDefinition.ALWAYS_SEND_CLIENT_CERT.parseAndSetParameter(value,  add, reader);
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
                    parseSecurityRealm(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseSecurityRealm(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list)
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
                    parseServerIdentities(reader, realmAddress, list);
                    break;
                case AUTHENTICATION: {
                    parseAuthentication(reader, realmAddress, list);
                    break;
                }
                case AUTHORIZATION:
                    parseAuthorization(reader, add, list);
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

    private void parseServerIdentities(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list)
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
                    parseSSL(reader, realmAddress, list);
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

    private void parseSSL(final XMLExtendedStreamReader reader, final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {

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
                    parseKeystore(reader, ssl, true);
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

    private void parseKeystore(final XMLExtendedStreamReader reader, final ModelNode addOperation, final boolean extended)
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
                    case GENERATE_SELF_SIGNED_CERTIFICATE_HOST:
                        if (extended) {
                            KeystoreAttributes.GENERATE_SELF_SIGNED_CERTIFICATE_HOST.parseAndSetParameter(value, addOperation, reader);
                        } else {
                            throw unexpectedAttribute(reader, i);
                        }
                        break;
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

    private void parseAuthentication(final XMLExtendedStreamReader reader,
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
                    parseJaasAuthentication(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case KERBEROS: {
                    if (kerberosFound) {
                        throw unexpectedElement(reader);
                    }
                    parseKerberosAuthentication(reader, realmAddress, list);
                    kerberosFound = true;
                    break;
                }
                case LDAP: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parseLdapAuthentication(reader, realmAddress, list);
                    usernamePasswordFound = true;
                    break;
                }
                case PROPERTIES: {
                    if (usernamePasswordFound) {
                        throw unexpectedElement(reader);
                    }
                    parsePropertiesAuthentication(reader, realmAddress, list);
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
                    parseLocalAuthentication(reader, realmAddress, list);
                    localFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseKerberosAuthentication(final XMLExtendedStreamReader reader,
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

    private void parseJaasAuthentication(final XMLExtendedStreamReader reader,
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

    private void parseLdapAuthenticationAttributes(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
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

    private void parseLdapAuthentication(final XMLExtendedStreamReader reader,
            final ModelNode realmAddress, final List<ModelNode> list) throws XMLStreamException {
        ModelNode addr = realmAddress.clone().add(AUTHENTICATION, LDAP);
        ModelNode ldapAuthentication = Util.getEmptyOperation(ADD, addr);

        list.add(ldapAuthentication);

        parseLdapAuthenticationAttributes(reader, ldapAuthentication);

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

    private void parseLocalAuthentication(final XMLExtendedStreamReader reader,
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

    private void parsePropertiesAuthentication(final XMLExtendedStreamReader reader,
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

        parseKeystore(reader, op, false);

        list.add(op);
    }

    private void parseAuthorization(final XMLExtendedStreamReader reader,
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
                    parseLdapAuthorization(reader, realmAddress, list);
                    authzFound = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }

        }
    }

    private void parseLdapAuthorization(final XMLExtendedStreamReader reader,
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
                    parseUsernameToDn(reader, addr, list);
                    break;
                }
                case GROUP_SEARCH: {
                    parseGroupSearch(reader, addr, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseUsernameToDn(final XMLExtendedStreamReader reader,
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

    private void parseGroupSearch(final XMLExtendedStreamReader reader,
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
                    parsePrincipalToGroup(reader, address, childAdd);
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

    private void parseGroupToPrincipalAttributes(final XMLExtendedStreamReader reader, final ModelNode addOp) throws XMLStreamException {
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

        parseGroupToPrincipalAttributes(reader, addOp);

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

    private void parsePrincipalToGroup(final XMLExtendedStreamReader reader, final ModelNode parentAddress,
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
                    case PARSE_ROLES_FROM_DN: {
                        PrincipalToGroupResourceDefinition.PARSE_ROLES_FROM_DN.parseAndSetParameter(value, addOp, reader);
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



    @Override
    public void writeManagement(final XMLExtendedStreamWriter writer, final ModelNode management, boolean allowInterfaces)
            throws XMLStreamException {
        boolean hasSecurityRealm = management.hasDefined(SECURITY_REALM);
        boolean hasConnection = management.hasDefined(LDAP_CONNECTION);
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

        if (!hasSecurityRealm && !hasConnection && !hasInterface && !hasServerGroupRoles
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

        if (hasSecurityRealm) {
            writeSecurityRealm(writer, management);
        }

        if (hasConnection) {
            writeOutboundConnections(writer, management);
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

    private void writeSecurityRealm(XMLExtendedStreamWriter writer, ModelNode management) throws XMLStreamException {
        ModelNode securityRealms = management.get(SECURITY_REALM);
        writer.writeStartElement(Element.SECURITY_REALMS.getLocalName());

        for (String variableName : securityRealms.keys()) {
            writer.writeStartElement(Element.SECURITY_REALM.getLocalName());
            writeAttribute(writer, Attribute.NAME, variableName);

            ModelNode realm = securityRealms.get(variableName);
            if (realm.hasDefined(PLUG_IN)) {
                writePlugIns(writer, realm.get(PLUG_IN));
            }

            if (realm.hasDefined(SERVER_IDENTITY)) {
                writeServerIdentities(writer, realm);
            }

            if (realm.hasDefined(AUTHENTICATION)) {
                writeAuthentication(writer, realm);
            }

            writeAuthorization(writer, realm);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writePlugIns(XMLExtendedStreamWriter writer, ModelNode plugIns) throws XMLStreamException {
        writer.writeStartElement(Element.PLUG_INS.getLocalName());
        for (String variable : plugIns.keys()) {
            writer.writeEmptyElement(Element.PLUG_IN.getLocalName());
            writer.writeAttribute(Attribute.MODULE.getLocalName(), variable);
        }
        writer.writeEndElement();
    }

    private void writeServerIdentities(XMLExtendedStreamWriter writer, ModelNode realm) throws XMLStreamException {
        writer.writeStartElement(Element.SERVER_IDENTITIES.getLocalName());
        ModelNode serverIdentities = realm.get(SERVER_IDENTITY);
        if (serverIdentities.hasDefined(KERBEROS)) {
            writer.writeStartElement(Element.KERBEROS.getLocalName());
            ModelNode kerberos = serverIdentities.require(KERBEROS);

            if (kerberos.hasDefined(KEYTAB)) {
                ModelNode keytabs = kerberos.get(KEYTAB);
                for (String current : keytabs.keys()) {
                    ModelNode currentNode = keytabs.get(current);
                    writer.writeEmptyElement(KEYTAB);
                    writer.writeAttribute(Attribute.PRINCIPAL.getLocalName(), current);
                    KeytabResourceDefinition.PATH.marshallAsAttribute(currentNode, writer);
                    KeytabResourceDefinition.RELATIVE_TO.marshallAsAttribute(currentNode, writer);
                    KeytabResourceDefinition.FOR_HOSTS.getAttributeMarshaller()
                            .marshallAsAttribute(KeytabResourceDefinition.FOR_HOSTS, currentNode, true, writer);
                    KeytabResourceDefinition.DEBUG.marshallAsAttribute(currentNode, writer);
                }
            }

            writer.writeEndElement();
        }

        if (serverIdentities.hasDefined(SSL)) {
            writer.writeStartElement(Element.SSL.getLocalName());
            ModelNode ssl = serverIdentities.get(SSL);
            SSLServerIdentityResourceDefinition.PROTOCOL.marshallAsAttribute(ssl, writer);
            if (ssl.hasDefined(ENABLED_CIPHER_SUITES) || ssl.hasDefined(ENABLED_PROTOCOLS)) {
                writer.writeEmptyElement(Element.ENGINE.getLocalName());
                SSLServerIdentityResourceDefinition.ENABLED_CIPHER_SUITES.marshallAsElement(ssl, writer);
                SSLServerIdentityResourceDefinition.ENABLED_PROTOCOLS.marshallAsElement(ssl, writer);
            }

            boolean hasProvider = ssl.hasDefined(KEYSTORE_PROVIDER) && !JKS.equalsIgnoreCase(ssl.require(KEYSTORE_PROVIDER).asString());
            if (hasProvider || ssl.hasDefined(KeystoreAttributes.KEYSTORE_PATH.getName())) {
                writer.writeEmptyElement(Element.KEYSTORE.getLocalName());
                KeystoreAttributes.KEYSTORE_PROVIDER.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.KEYSTORE_PATH.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.KEYSTORE_RELATIVE_TO.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.KEYSTORE_PASSWORD.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.ALIAS.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.KEY_PASSWORD.marshallAsAttribute(ssl, writer);
                KeystoreAttributes.GENERATE_SELF_SIGNED_CERTIFICATE_HOST.marshallAsAttribute(ssl, writer);
            }
            writer.writeEndElement();
        }
        if (serverIdentities.hasDefined(SECRET)) {
            ModelNode secret = serverIdentities.get(SECRET);
            writer.writeEmptyElement(Element.SECRET.getLocalName());
            SecretServerIdentityResourceDefinition.VALUE.marshallAsAttribute(secret, writer);
        }

        writer.writeEndElement();
    }

    private void writeLdapCacheIfDefined(XMLExtendedStreamWriter writer, ModelNode parent) throws XMLStreamException {
        if (parent.hasDefined(CACHE)) {
            ModelNode cacheHolder = parent.require(CACHE);
            final ModelNode cache;
            final String type;

            if (cacheHolder.hasDefined(BY_ACCESS_TIME)) {
                cache = cacheHolder.require(BY_ACCESS_TIME);
                type = BY_ACCESS_TIME;
            } else if (cacheHolder.hasDefined(BY_SEARCH_TIME)) {
                cache = cacheHolder.require(BY_SEARCH_TIME);
                type = BY_SEARCH_TIME;
            } else {
                return;
            }

            writer.writeStartElement(Element.CACHE.getLocalName());
            if (type.equals(BY_SEARCH_TIME) == false) {
                writer.writeAttribute(Attribute.TYPE.getLocalName(), type);
            }
            LdapCacheResourceDefinition.EVICTION_TIME.marshallAsAttribute(cache, writer);
            LdapCacheResourceDefinition.CACHE_FAILURES.marshallAsAttribute(cache, writer);
            LdapCacheResourceDefinition.MAX_CACHE_SIZE.marshallAsAttribute(cache, writer);
            writer.writeEndElement();
        }
    }

    private void writeAuthentication(XMLExtendedStreamWriter writer, ModelNode realm) throws XMLStreamException {
        writer.writeStartElement(Element.AUTHENTICATION.getLocalName());
        ModelNode authentication = realm.require(AUTHENTICATION);

        if (authentication.hasDefined(TRUSTSTORE)) {
            ModelNode truststore = authentication.require(TRUSTSTORE);
            writer.writeEmptyElement(Element.TRUSTSTORE.getLocalName());
            KeystoreAttributes.KEYSTORE_PROVIDER.marshallAsAttribute(truststore, writer);
            KeystoreAttributes.KEYSTORE_PATH.marshallAsAttribute(truststore, writer);
            KeystoreAttributes.KEYSTORE_RELATIVE_TO.marshallAsAttribute(truststore, writer);
            KeystoreAttributes.KEYSTORE_PASSWORD.marshallAsAttribute(truststore, writer);
        }

        if (authentication.hasDefined(LOCAL)) {
            ModelNode local = authentication.require(LOCAL);
            writer.writeStartElement(Element.LOCAL.getLocalName());
            LocalAuthenticationResourceDefinition.DEFAULT_USER.marshallAsAttribute(local, writer);
            LocalAuthenticationResourceDefinition.ALLOWED_USERS.marshallAsAttribute(local, writer);
            LocalAuthenticationResourceDefinition.SKIP_GROUP_LOADING.marshallAsAttribute(local, writer);
            writer.writeEndElement();
        }

        if (authentication.hasDefined(KERBEROS)) {
            ModelNode kerberos = authentication.require(KERBEROS);
            writer.writeEmptyElement(Element.KERBEROS.getLocalName());
            KerberosAuthenticationResourceDefinition.REMOVE_REALM.marshallAsAttribute(kerberos, writer);
        }

        if (authentication.hasDefined(JAAS)) {
            ModelNode jaas = authentication.get(JAAS);
            writer.writeStartElement(Element.JAAS.getLocalName());
            JaasAuthenticationResourceDefinition.NAME.marshallAsAttribute(jaas, writer);
            JaasAuthenticationResourceDefinition.ASSIGN_GROUPS.marshallAsAttribute(jaas, writer);
            writer.writeEndElement();
        } else if (authentication.hasDefined(LDAP)) {
            ModelNode userLdap = authentication.get(LDAP);
            writer.writeStartElement(Element.LDAP.getLocalName());
            LdapAuthenticationResourceDefinition.CONNECTION.marshallAsAttribute(userLdap, writer);
            LdapAuthenticationResourceDefinition.BASE_DN.marshallAsAttribute(userLdap, writer);
            LdapAuthenticationResourceDefinition.RECURSIVE.marshallAsAttribute(userLdap, writer);
            LdapAuthenticationResourceDefinition.USER_DN.marshallAsAttribute(userLdap, writer);
            LdapAuthenticationResourceDefinition.ALLOW_EMPTY_PASSWORDS.marshallAsAttribute(userLdap, writer);
            LdapAuthenticationResourceDefinition.USERNAME_LOAD.marshallAsAttribute(userLdap, writer);

            writeLdapCacheIfDefined(writer, userLdap);

            if (LdapAuthenticationResourceDefinition.USERNAME_FILTER.isMarshallable(userLdap)) {
                writer.writeEmptyElement(Element.USERNAME_FILTER.getLocalName());
                LdapAuthenticationResourceDefinition.USERNAME_FILTER.marshallAsAttribute(userLdap, writer);
            } else if (LdapAuthenticationResourceDefinition.ADVANCED_FILTER.isMarshallable(userLdap)) {
                writer.writeEmptyElement(Element.ADVANCED_FILTER.getLocalName());
                LdapAuthenticationResourceDefinition.ADVANCED_FILTER.marshallAsAttribute(userLdap, writer);
            }
            writer.writeEndElement();
        } else if (authentication.hasDefined(PROPERTIES)) {
            ModelNode properties = authentication.require(PROPERTIES);
            writer.writeEmptyElement(Element.PROPERTIES.getLocalName());
            PropertiesAuthenticationResourceDefinition.PATH.marshallAsAttribute(properties, writer);
            PropertiesAuthenticationResourceDefinition.RELATIVE_TO.marshallAsAttribute(properties, writer);
            PropertiesAuthenticationResourceDefinition.PLAIN_TEXT.marshallAsAttribute(properties, writer);
        } else if (authentication.has(USERS)) {
            ModelNode userDomain = authentication.get(USERS);
            ModelNode users = userDomain.hasDefined(USER) ? userDomain.require(USER) : new ModelNode().setEmptyObject();
            writer.writeStartElement(Element.USERS.getLocalName());
            for (String userName : users.keys()) {
                ModelNode currentUser = users.get(userName);
                writer.writeStartElement(Element.USER.getLocalName());
                writer.writeAttribute(Attribute.USERNAME.getLocalName(), userName);
                UserResourceDefinition.PASSWORD.marshallAsElement(currentUser, writer);
                writer.writeEndElement();
            }
            writer.writeEndElement();
        } else if (authentication.hasDefined(PLUG_IN)) {
            writePlugIn_Authentication(writer, authentication.get(PLUG_IN));
        }

        writer.writeEndElement();
    }

    private void writePlugIn_Authentication(XMLExtendedStreamWriter writer, ModelNode plugIn) throws XMLStreamException {
        writer.writeStartElement(Element.PLUG_IN.getLocalName());
        AbstractPlugInAuthResourceDefinition.NAME.marshallAsAttribute(plugIn, writer);
        PlugInAuthenticationResourceDefinition.MECHANISM.marshallAsAttribute(plugIn, writer);
        if (plugIn.hasDefined(PROPERTY)) {
            writer.writeStartElement(PROPERTIES);
            ModelNode properties = plugIn.get(PROPERTY);
            for (String current : properties.keys()) {
                writer.writeEmptyElement(PROPERTY);
                writer.writeAttribute(Attribute.NAME.getLocalName(), current);
                PropertyResourceDefinition.VALUE.marshallAsAttribute(properties.get(current), writer);
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writeAuthorization(XMLExtendedStreamWriter writer, ModelNode realm) throws XMLStreamException {
        // A String comparison in-case it is an expression.
        String defaultMapGroupsToRoles = Boolean.toString(SecurityRealmResourceDefinition.MAP_GROUPS_TO_ROLES.getDefaultValue().asBoolean());
        String mapGroupsToRoles = realm.hasDefined(MAP_GROUPS_TO_ROLES) ? realm.require(MAP_GROUPS_TO_ROLES).asString() : defaultMapGroupsToRoles;

        if (realm.hasDefined(AUTHORIZATION) || defaultMapGroupsToRoles.equals(mapGroupsToRoles) == false) {
            writer.writeStartElement(Element.AUTHORIZATION.getLocalName());
            SecurityRealmResourceDefinition.MAP_GROUPS_TO_ROLES.marshallAsAttribute(realm, writer);
            if (realm.hasDefined(AUTHORIZATION)) {
                ModelNode authorization = realm.require(AUTHORIZATION);
                if (authorization.hasDefined(PROPERTIES)) {
                    ModelNode properties = authorization.require(PROPERTIES);
                    writer.writeEmptyElement(Element.PROPERTIES.getLocalName());
                    PropertiesAuthorizationResourceDefinition.PATH.marshallAsAttribute(properties, writer);
                    PropertiesAuthorizationResourceDefinition.RELATIVE_TO.marshallAsAttribute(properties, writer);
                } else if (authorization.hasDefined(PLUG_IN)) {
                    writePlugIn_Authorization(writer, authorization.get(PLUG_IN));
                } else if (authorization.hasDefined(LDAP)) {
                    writeLdapAuthorization(writer, authorization.get(LDAP));
                }
            }
            writer.writeEndElement();
        }
    }

    private void writeLdapAuthorization(XMLExtendedStreamWriter writer, ModelNode ldapNode) throws XMLStreamException {
        writer.writeStartElement(Element.LDAP.getLocalName());
        LdapAuthorizationResourceDefinition.CONNECTION.marshallAsAttribute(ldapNode, writer);
        if (ldapNode.hasDefined(USERNAME_TO_DN)) {
            ModelNode usenameToDn = ldapNode.require(USERNAME_TO_DN);
            if (usenameToDn.hasDefined(USERNAME_IS_DN) || usenameToDn.hasDefined(USERNAME_FILTER)
                    || usenameToDn.hasDefined(ADVANCED_FILTER)) {
                writer.writeStartElement(Element.USERNAME_TO_DN.getLocalName());
                if (usenameToDn.hasDefined(USERNAME_IS_DN)) {
                    ModelNode usernameIsDn = usenameToDn.require(USERNAME_IS_DN);
                    UserIsDnResourceDefintion.FORCE.marshallAsAttribute(usernameIsDn, writer);
                    writeLdapCacheIfDefined(writer, usernameIsDn);
                    writer.writeEmptyElement(Element.USERNAME_IS_DN.getLocalName());
                } else if (usenameToDn.hasDefined(USERNAME_FILTER)) {
                    ModelNode usernameFilter = usenameToDn.require(USERNAME_FILTER);
                    UserSearchResourceDefintion.FORCE.marshallAsAttribute(usernameFilter, writer);
                    writeLdapCacheIfDefined(writer, usernameFilter);
                    writer.writeStartElement(Element.USERNAME_FILTER.getLocalName());
                    UserSearchResourceDefintion.BASE_DN.marshallAsAttribute(usernameFilter, writer);
                    UserSearchResourceDefintion.RECURSIVE.marshallAsAttribute(usernameFilter, writer);
                    UserSearchResourceDefintion.USER_DN_ATTRIBUTE.marshallAsAttribute(usernameFilter, writer);
                    UserSearchResourceDefintion.ATTRIBUTE.marshallAsAttribute(usernameFilter, writer);
                    writer.writeEndElement();
                } else {
                    ModelNode advancedFilter = usenameToDn.require(ADVANCED_FILTER);
                    AdvancedUserSearchResourceDefintion.FORCE.marshallAsAttribute(advancedFilter, writer);
                    writeLdapCacheIfDefined(writer, advancedFilter);
                    writer.writeStartElement(Element.ADVANCED_FILTER.getLocalName());
                    AdvancedUserSearchResourceDefintion.BASE_DN.marshallAsAttribute(advancedFilter, writer);
                    AdvancedUserSearchResourceDefintion.RECURSIVE.marshallAsAttribute(advancedFilter, writer);
                    AdvancedUserSearchResourceDefintion.USER_DN_ATTRIBUTE.marshallAsAttribute(advancedFilter, writer);
                    AdvancedUserSearchResourceDefintion.FILTER.marshallAsAttribute(advancedFilter, writer);
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }

        if (ldapNode.hasDefined(GROUP_SEARCH)) {
            ModelNode groupSearch = ldapNode.require(GROUP_SEARCH);

            if (groupSearch.hasDefined(GROUP_TO_PRINCIPAL) || groupSearch.hasDefined(PRINCIPAL_TO_GROUP)) {
                writer.writeStartElement(Element.GROUP_SEARCH.getLocalName());
                if (groupSearch.hasDefined(GROUP_TO_PRINCIPAL)) {
                    ModelNode groupToPrincipal = groupSearch.require(GROUP_TO_PRINCIPAL);
                    GroupToPrincipalResourceDefinition.GROUP_NAME.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.ITERATIVE.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.GROUP_DN_ATTRIBUTE.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.GROUP_NAME_ATTRIBUTE.marshallAsAttribute(groupToPrincipal, writer);
                    writeLdapCacheIfDefined(writer, groupToPrincipal);
                    writer.writeStartElement(Element.GROUP_TO_PRINCIPAL.getLocalName());
                    GroupToPrincipalResourceDefinition.SEARCH_BY.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.BASE_DN.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.RECURSIVE.marshallAsAttribute(groupToPrincipal, writer);
                    GroupToPrincipalResourceDefinition.PREFER_ORIGINAL_CONNECTION.marshallAsAttribute(groupToPrincipal, writer);
                    writer.writeStartElement(Element.MEMBERSHIP_FILTER.getLocalName());
                    GroupToPrincipalResourceDefinition.PRINCIPAL_ATTRIBUTE.marshallAsAttribute(groupToPrincipal, writer);
                    writer.writeEndElement();
                    writer.writeEndElement();
                } else {
                    ModelNode principalToGroup = groupSearch.require(PRINCIPAL_TO_GROUP);
                    PrincipalToGroupResourceDefinition.GROUP_NAME.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.ITERATIVE.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.GROUP_DN_ATTRIBUTE.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.GROUP_NAME_ATTRIBUTE.marshallAsAttribute(principalToGroup, writer);
                    writeLdapCacheIfDefined(writer, principalToGroup);
                    writer.writeStartElement(Element.PRINCIPAL_TO_GROUP.getLocalName());
                    PrincipalToGroupResourceDefinition.GROUP_ATTRIBUTE.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.PREFER_ORIGINAL_CONNECTION.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.SKIP_MISSING_GROUPS.marshallAsAttribute(principalToGroup, writer);
                    PrincipalToGroupResourceDefinition.PARSE_ROLES_FROM_DN.marshallAsAttribute(principalToGroup, writer);
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }

        writer.writeEndElement();
    }

    private void writePlugIn_Authorization(XMLExtendedStreamWriter writer, ModelNode plugIn) throws XMLStreamException {
        writer.writeStartElement(Element.PLUG_IN.getLocalName());
        AbstractPlugInAuthResourceDefinition.NAME.marshallAsAttribute(plugIn, writer);
        if (plugIn.hasDefined(PROPERTY)) {
            writer.writeStartElement(PROPERTIES);
            ModelNode properties = plugIn.get(PROPERTY);
            for (String current : properties.keys()) {
                writer.writeEmptyElement(PROPERTY);
                writer.writeAttribute(Attribute.NAME.getLocalName(), current);
                PropertyResourceDefinition.VALUE.marshallAsAttribute(properties.get(current), writer);
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writeOutboundConnections(XMLExtendedStreamWriter writer, ModelNode management) throws XMLStreamException {

        writer.writeStartElement(Element.OUTBOUND_CONNECTIONS.getLocalName());

        ModelNode ldapConns = management.get(LDAP_CONNECTION);
        for (String variable : ldapConns.keys()) {
            ModelNode connection = ldapConns.get(variable);
            writer.writeStartElement(Element.LDAP.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), variable);
            LdapConnectionResourceDefinition.URL.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.SEARCH_DN.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.SEARCH_CREDENTIAL.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.SECURITY_REALM.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.REFERRALS.marshallAsAttribute(connection, writer);
            LdapConnectionResourceDefinition.HANDLES_REFERRALS_FOR.getAttributeMarshaller()
                    .marshallAsAttribute(LdapConnectionResourceDefinition.HANDLES_REFERRALS_FOR, connection, true, writer);
            LdapConnectionResourceDefinition.ALWAYS_SEND_CLIENT_CERT.marshallAsAttribute(connection, writer);

            if (connection.hasDefined(PROPERTY)) {
                ModelNode properties = connection.get(PROPERTY);
                Set<String> propertySet = properties.keys();
                if (propertySet.size() > 0) {
                    writer.writeStartElement(PROPERTIES);
                    for (String current : propertySet) {
                        writer.writeEmptyElement(PROPERTY);
                        writer.writeAttribute(Attribute.NAME.getLocalName(), current);
                        LdapConnectionPropertyResourceDefinition.VALUE.marshallAsAttribute(properties.get(current), writer);
                    }
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();
        }
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
