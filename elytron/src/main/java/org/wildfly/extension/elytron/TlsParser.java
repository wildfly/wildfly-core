/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALGORITHM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALIAS_ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALIAS_FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_TYPE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CIPHER_SUITE_FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLIENT_SSL_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLIENT_SSL_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERING_KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER_ALIAS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER_CERTIFICATE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER_ITERATE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_MANAGER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_MANAGERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_TYPE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LDAP_KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LDAP_MAPPING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAXIMUM_CERT_PATH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAXIMUM_SESSION_CACHE_SIZE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NEED_CLIENT_AUTH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NEW_ITEM_PATH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NEW_ITEM_RDN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NEW_ITEM_TEMPLATE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PASSWORD;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PATH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROTOCOLS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.RELATIVE_TO;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REQUIRED;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SEARCH_PATH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SEARCH_RECURSIVE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SEARCH_TIME_LIMIT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVER_SSL_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVER_SSL_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SESSION_TIMEOUT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TLS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TYPE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.WANT_CLIENT_AUTH;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the TLS related definitions.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TlsParser {

    void readTls(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        boolean keyManagersFound = false;
        boolean keyStoresFound = false;
        boolean trustManagersFound = false;
        boolean serverSSLContextsFound = false;
        boolean clientSSLContextsFound = false;

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEY_MANAGERS.equals(localName) && keyManagersFound == false) {
                keyManagersFound = true;
                readKeyManagers(parentAddress, reader, operations);
            } else if (KEY_STORES.equals(localName) && keyStoresFound == false) {
                keyStoresFound = true;
                readKeyStores(parentAddress, reader, operations);
            } else if (TRUST_MANAGERS.equals(localName) && trustManagersFound == false) {
                trustManagersFound = true;
                readTrustManagers(parentAddress, reader, operations);
            } else if (SERVER_SSL_CONTEXTS.equals(localName) && serverSSLContextsFound == false) {
                serverSSLContextsFound = true;
                readServerSSLContexts(parentAddress, reader, operations);
            } else if (CLIENT_SSL_CONTEXTS.equals(localName) && clientSSLContextsFound == false) {
                clientSSLContextsFound = true;
                readClientSSLContexts(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readKeyManagers(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEY_MANAGER.equals(localName)) {
                readKeyManager(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readKeyManager(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyManager = new ModelNode();
        addKeyManager.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, ALGORITHM }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case ALGORITHM:
                        SSLDefinitions.ALGORITHM.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case KEY_STORE:
                        SSLDefinitions.KEYSTORE.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case ALIAS_FILTER:
                        SSLDefinitions.ALIAS_FILTER.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case PROVIDERS:
                        SSLDefinitions.PROVIDERS.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case PROVIDER_NAME:
                        SSLDefinitions.PROVIDER_NAME.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyManager.get(OP_ADDR).set(parentAddress).add(KEY_MANAGERS, name);
        list.add(addKeyManager);

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (CredentialReference.CREDENTIAL_REFERENCE.equals(localName)) {
                CredentialReference.getAttributeDefinition().getParser().parseElement(CredentialReference.getAttributeDefinition(), reader, addKeyManager);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readTrustManagers(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (TRUST_MANAGER.equals(localName)) {
                readTrustManager(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readTrustManager(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyManager = new ModelNode();
        addKeyManager.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, ALGORITHM }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case ALGORITHM:
                        SSLDefinitions.ALGORITHM.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case KEY_STORE:
                        SSLDefinitions.KEYSTORE.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case ALIAS_FILTER:
                        SSLDefinitions.ALIAS_FILTER.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case PROVIDERS:
                        SSLDefinitions.PROVIDERS.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    case PROVIDER_NAME:
                        SSLDefinitions.PROVIDER_NAME.parseAndSetParameter(value, addKeyManager, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyManager.get(OP_ADDR).set(parentAddress).add(TRUST_MANAGERS, name);

        readCertificateRevocationList(reader, addKeyManager, requiredAttributes);

        list.add(addKeyManager);
    }

    private void readCertificateRevocationList(XMLExtendedStreamReader reader, ModelNode addKeyManager, Set<String> requiredAttributes) throws XMLStreamException {
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (CERTIFICATE_REVOCATION_LIST.equals(localName)) {
                ModelNode crlNode = new ModelNode().setEmptyObject();
                final int count = reader.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    final String value = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw unexpectedAttribute(reader, i);
                    } else {
                        String attribute = reader.getAttributeLocalName(i);
                        requiredAttributes.remove(attribute);
                        switch (attribute) {
                            case PATH:
                                FileAttributeDefinitions.PATH.parseAndSetParameter(value, crlNode, reader);
                                break;
                            case RELATIVE_TO:
                                FileAttributeDefinitions.RELATIVE_TO.parseAndSetParameter(value, crlNode, reader);
                                break;
                            case MAXIMUM_CERT_PATH:
                                SSLDefinitions.MAXIMUM_CERT_PATH.parseAndSetParameter(value, crlNode, reader);
                                break;
                            default:
                                throw unexpectedAttribute(reader, i);
                        }
                    }
                }
                requireNoContent(reader);
                addKeyManager.get(CERTIFICATE_REVOCATION_LIST).set(crlNode);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readServerSSLContexts(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SERVER_SSL_CONTEXT.equals(localName)) {
                readServerSSLContext(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readServerSSLContext(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addServerSSLContext = new ModelNode();
        addServerSSLContext.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case SECURITY_DOMAIN:
                        SSLDefinitions.SECURITY_DOMAIN.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case CIPHER_SUITE_FILTER:
                        SSLDefinitions.CIPHER_SUITE_FILTER.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROTOCOLS:
                        for (String protocol : reader.getListAttributeValue(i)) {
                            SSLDefinitions.PROTOCOLS.parseAndAddParameterElement(protocol, addServerSSLContext, reader);
                        }
                        break;
                    case WANT_CLIENT_AUTH:
                        SSLDefinitions.WANT_CLIENT_AUTH.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case NEED_CLIENT_AUTH:
                        SSLDefinitions.NEED_CLIENT_AUTH.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case AUTHENTICATION_OPTIONAL:
                        SSLDefinitions.AUTHENTICATION_OPTIONAL.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case USE_CIPHER_SUITES_ORDER:
                        SSLDefinitions.USE_CIPHER_SUITES_ORDER.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case MAXIMUM_SESSION_CACHE_SIZE:
                        SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case SESSION_TIMEOUT:
                        SSLDefinitions.SESSION_TIMEOUT.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case KEY_MANAGERS:
                        SSLDefinitions.KEY_MANAGERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case TRUST_MANAGERS:
                        SSLDefinitions.TRUST_MANAGERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROVIDERS:
                        SSLDefinitions.PROVIDERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROVIDER_NAME:
                        SSLDefinitions.PROVIDER_NAME.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addServerSSLContext.get(OP_ADDR).set(parentAddress).add(SERVER_SSL_CONTEXT, name);
        list.add(addServerSSLContext);

        requireNoContent(reader);
    }

    private void readClientSSLContexts(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (CLIENT_SSL_CONTEXT.equals(localName)) {
                readClientSSLContext(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readClientSSLContext(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addServerSSLContext = new ModelNode();
        addServerSSLContext.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case CIPHER_SUITE_FILTER:
                        SSLDefinitions.CIPHER_SUITE_FILTER.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROTOCOLS:
                        for (String protocol : reader.getListAttributeValue(i)) {
                            SSLDefinitions.PROTOCOLS.parseAndAddParameterElement(protocol, addServerSSLContext, reader);
                        }
                        break;
                    case USE_CIPHER_SUITES_ORDER:
                        SSLDefinitions.USE_CIPHER_SUITES_ORDER.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case MAXIMUM_SESSION_CACHE_SIZE:
                        SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case SESSION_TIMEOUT:
                        SSLDefinitions.SESSION_TIMEOUT.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case KEY_MANAGERS:
                        SSLDefinitions.KEY_MANAGERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case TRUST_MANAGERS:
                        SSLDefinitions.TRUST_MANAGERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROVIDERS:
                        SSLDefinitions.PROVIDERS.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    case PROVIDER_NAME:
                        SSLDefinitions.PROVIDER_NAME.parseAndSetParameter(value, addServerSSLContext, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addServerSSLContext.get(OP_ADDR).set(parentAddress).add(CLIENT_SSL_CONTEXT, name);
        list.add(addServerSSLContext);

        requireNoContent(reader);
    }

    private void readKeyStores(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEY_STORE.equals(localName)) {
                readKeyStore(parentAddress, reader, operations);
            } else if (LDAP_KEY_STORE.equals(localName)) {
                readLdapKeyStore(parentAddress, reader, operations);
            } else if (FILTERING_KEY_STORE.equals(localName)) {
                readFilteringKeyStore(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readKeyStore(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyStore = new ModelNode();
        addKeyStore.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, TYPE }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case TYPE:
                        KeyStoreDefinition.TYPE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PROVIDER_NAME:
                        KeyStoreDefinition.PROVIDER_NAME.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PROVIDERS:
                        KeyStoreDefinition.PROVIDERS.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PASSWORD:
                        KeyStoreDefinition.CREDENTIAL_REFERENCE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case ALIAS_FILTER:
                        KeyStoreDefinition.ALIAS_FILTER.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyStore.get(OP_ADDR).set(parentAddress).add(KEY_STORE, name);
        list.add(addKeyStore);

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (FILE.equals(localName)) {
                readFile(addKeyStore, reader, list);
            } else if (CredentialReference.CREDENTIAL_REFERENCE.equals(localName)) {
                CredentialReference.getAttributeDefinition().getParser().parseElement(CredentialReference.getAttributeDefinition(), reader, addKeyStore);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readFile(ModelNode addOp, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        boolean pathFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case RELATIVE_TO:
                        FileAttributeDefinitions.RELATIVE_TO.parseAndSetParameter(value, addOp, reader);
                        break;
                    case PATH:
                        pathFound = true;
                        FileAttributeDefinitions.PATH.parseAndSetParameter(value, addOp, reader);
                        break;
                    case REQUIRED:
                        KeyStoreDefinition.REQUIRED.parseAndSetParameter(value, addOp, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (pathFound == false) {
            throw missingRequired(reader, PATH);
        }
        requireNoContent(reader);
    }

    private void readLdapKeyStore(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyStore = new ModelNode();
        addKeyStore.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, DIR_CONTEXT, SEARCH_PATH }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case DIR_CONTEXT:
                        LdapKeyStoreDefinition.DIR_CONTEXT.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case SEARCH_PATH:
                        LdapKeyStoreDefinition.SEARCH_PATH.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case SEARCH_RECURSIVE:
                        LdapKeyStoreDefinition.SEARCH_RECURSIVE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case SEARCH_TIME_LIMIT:
                        LdapKeyStoreDefinition.SEARCH_TIME_LIMIT.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case FILTER_ALIAS:
                        LdapKeyStoreDefinition.FILTER_ALIAS.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case FILTER_CERTIFICATE:
                        LdapKeyStoreDefinition.FILTER_CERTIFICATE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case FILTER_ITERATE:
                        LdapKeyStoreDefinition.FILTER_ITERATE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyStore.get(OP_ADDR).set(parentAddress).add(LDAP_KEY_STORE, name);
        list.add(addKeyStore);

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (NEW_ITEM_TEMPLATE.equals(localName)) {
                readNewItemTemplate(addKeyStore, reader, list);
            } else if (LDAP_MAPPING.equals(localName)) {
                readLdapMapping(addKeyStore, reader, list);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readNewItemTemplate(ModelNode addKeyStore, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] {NEW_ITEM_PATH, NEW_ITEM_RDN}));
        ModelNode newItemTemplate = addKeyStore.get(NEW_ITEM_TEMPLATE);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NEW_ITEM_PATH:
                        LdapKeyStoreDefinition.NewItemTemplateObjectDefinition.NEW_ITEM_PATH.parseAndSetParameter(value, newItemTemplate, reader);
                        break;
                    case NEW_ITEM_RDN:
                        LdapKeyStoreDefinition.NewItemTemplateObjectDefinition.NEW_ITEM_RDN.parseAndSetParameter(value, newItemTemplate, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            if (ATTRIBUTE.equals(reader.getLocalName())) {
                ModelNode attribute = new ModelNode();
                readLdapAttribute(attribute, reader);
                newItemTemplate.get(NEW_ITEM_ATTRIBUTES).add(attribute);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readLdapAttribute(ModelNode attribute, XMLExtendedStreamReader reader) throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[]{NAME, VALUE}));
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attributeName = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attributeName);
                switch (attributeName) {
                    case NAME:
                        String value = reader.getAttributeValue(i);
                        LdapKeyStoreDefinition.NewItemTemplateAttributeObjectDefinition.NAME.parseAndSetParameter(value, attribute, reader);
                        break;
                    case VALUE:
                        for (String val : reader.getListAttributeValue(i)) {
                            LdapKeyStoreDefinition.NewItemTemplateAttributeObjectDefinition.VALUE.parseAndAddParameterElement(val, attribute, reader);
                        }
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);
    }

    private void readLdapMapping(ModelNode addKeyStore, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case ALIAS_ATTRIBUTE:
                        LdapKeyStoreDefinition.ALIAS_ATTRIBUTE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case CERTIFICATE_ATTRIBUTE:
                        LdapKeyStoreDefinition.CERTIFICATE_ATTRIBUTE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case CERTIFICATE_TYPE:
                        LdapKeyStoreDefinition.CERTIFICATE_TYPE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case CERTIFICATE_CHAIN_ATTRIBUTE:
                        LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ATTRIBUTE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case CERTIFICATE_CHAIN_ENCODING:
                        LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ENCODING.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case KEY_ATTRIBUTE:
                        LdapKeyStoreDefinition.KEY_ATTRIBUTE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case KEY_TYPE:
                        LdapKeyStoreDefinition.KEY_TYPE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);
    }

    private void readFilteringKeyStore(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyStore = new ModelNode();
        addKeyStore.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, KEY_STORE, ALIAS_FILTER }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case KEY_STORE:
                        FilteringKeyStoreDefinition.KEY_STORE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case ALIAS_FILTER:
                        FilteringKeyStoreDefinition.ALIAS_FILTER.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyStore.get(OP_ADDR).set(parentAddress).add(FILTERING_KEY_STORE, name);
        list.add(addKeyStore);

        requireNoContent(reader);
    }

    private void startTLS(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(TLS);
        }
    }

    void writeTLS(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        boolean tlsStarted = false;

        tlsStarted = tlsStarted | writeKeyStores(tlsStarted, subsystem, writer);
        tlsStarted = tlsStarted | writeKeyManagers(tlsStarted, subsystem, writer);
        tlsStarted = tlsStarted | writeTrustManagers(tlsStarted, subsystem, writer);
        tlsStarted = tlsStarted | writeServerSSLContext(tlsStarted, subsystem, writer);
        tlsStarted = tlsStarted | writeClientSSLContext(tlsStarted, subsystem, writer);

        if (tlsStarted) {
            writer.writeEndElement();
        }
    }

    private boolean writeKeyManagers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(KEY_MANAGERS)) {
            startTLS(started, writer);
            writer.writeStartElement(KEY_MANAGERS);
            ModelNode keyManagers = subsystem.require(KEY_MANAGERS);
            for (String name : keyManagers.keys()) {
                ModelNode keyManager = keyManagers.require(name);
                writer.writeStartElement(KEY_MANAGER);
                writer.writeAttribute(NAME, name);
                SSLDefinitions.ALGORITHM.marshallAsAttribute(keyManager, writer);
                SSLDefinitions.KEYSTORE.marshallAsAttribute(keyManager, writer);
                SSLDefinitions.PROVIDERS.marshallAsAttribute(keyManager, writer);
                SSLDefinitions.PROVIDER_NAME.marshallAsAttribute(keyManager, writer);
                CredentialReference.getAttributeDefinition().marshallAsElement(keyManager, writer);

                writer.writeEndElement();
            }

            writer.writeEndElement();
            return true;
        }

        return false;
    }

    private boolean writeTrustManagers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(TRUST_MANAGERS)) {
            startTLS(started, writer);
            writer.writeStartElement(TRUST_MANAGERS);
            ModelNode trustManagers = subsystem.require(TRUST_MANAGERS);
            for (String name : trustManagers.keys()) {
                ModelNode trustManager = trustManagers.require(name);
                writer.writeStartElement(TRUST_MANAGER);
                writer.writeAttribute(NAME, name);
                SSLDefinitions.ALGORITHM.marshallAsAttribute(trustManager, writer);
                SSLDefinitions.KEYSTORE.marshallAsAttribute(trustManager, writer);
                SSLDefinitions.PROVIDERS.marshallAsAttribute(trustManager, writer);
                SSLDefinitions.PROVIDER_NAME.marshallAsAttribute(trustManager, writer);
                SSLDefinitions.CERTIFICATE_REVOCATION_LIST.marshallAsElement(trustManager, writer);

                writer.writeEndElement();
            }

            writer.writeEndElement();
            return true;
        }

        return false;
    }

    private boolean writeServerSSLContext(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SERVER_SSL_CONTEXT)) {
            startTLS(started, writer);
            writer.writeStartElement(SERVER_SSL_CONTEXTS);
            ModelNode serverSSLContexts = subsystem.require(SERVER_SSL_CONTEXT);

            for (String name : serverSSLContexts.keys()) {
                ModelNode serverSSLContext = serverSSLContexts.require(name);
                writer.writeStartElement(SERVER_SSL_CONTEXT);
                writer.writeAttribute(NAME, name);
                SSLDefinitions.SECURITY_DOMAIN.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.CIPHER_SUITE_FILTER.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROTOCOLS.getAttributeMarshaller().marshallAsAttribute(SSLDefinitions.PROTOCOLS, serverSSLContext, false, writer);
                SSLDefinitions.WANT_CLIENT_AUTH.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.NEED_CLIENT_AUTH.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.AUTHENTICATION_OPTIONAL.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.USE_CIPHER_SUITES_ORDER.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.SESSION_TIMEOUT.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.KEY_MANAGERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.TRUST_MANAGERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROVIDERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROVIDER_NAME.marshallAsAttribute(serverSSLContext, writer);

                writer.writeEndElement();
            }

            writer.writeEndElement();
            return true;
        }

        return false;
    }

    private boolean writeClientSSLContext(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CLIENT_SSL_CONTEXT)) {
            startTLS(started, writer);
            writer.writeStartElement(CLIENT_SSL_CONTEXTS);
            ModelNode serverSSLContexts = subsystem.require(CLIENT_SSL_CONTEXT);

            for (String name : serverSSLContexts.keys()) {
                ModelNode serverSSLContext = serverSSLContexts.require(name);
                writer.writeStartElement(CLIENT_SSL_CONTEXT);
                writer.writeAttribute(NAME, name);
                SSLDefinitions.CIPHER_SUITE_FILTER.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROTOCOLS.getAttributeMarshaller().marshallAsAttribute(SSLDefinitions.PROTOCOLS, serverSSLContext, false, writer);
                SSLDefinitions.USE_CIPHER_SUITES_ORDER.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.SESSION_TIMEOUT.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.KEY_MANAGERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.TRUST_MANAGERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROVIDERS.marshallAsAttribute(serverSSLContext, writer);
                SSLDefinitions.PROVIDER_NAME.marshallAsAttribute(serverSSLContext, writer);

                writer.writeEndElement();
            }

            writer.writeEndElement();
            return true;
        }

        return false;
    }

    private boolean writeKeyStores(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(KEY_STORE) || subsystem.hasDefined(LDAP_KEY_STORE) || subsystem.hasDefined(FILTERING_KEY_STORE)) {
            startTLS(started, writer);
            writer.writeStartElement(KEY_STORES);

            ModelNode keystores = subsystem.get(KEY_STORE);
            if (keystores.isDefined()) {
                for (String name : keystores.keys()) {
                    ModelNode keyStore = keystores.require(name);
                    writer.writeStartElement(KEY_STORE);
                    writer.writeAttribute(NAME, name);
                    KeyStoreDefinition.TYPE.marshallAsAttribute(keyStore, writer);
                    KeyStoreDefinition.PROVIDER_NAME.marshallAsAttribute(keyStore, writer);
                    KeyStoreDefinition.PROVIDERS.marshallAsAttribute(keyStore, writer);
                    KeyStoreDefinition.ALIAS_FILTER.marshallAsAttribute(keyStore, writer);

                    if (keyStore.hasDefined(PATH)) {
                        writer.writeStartElement(FILE);
                        FileAttributeDefinitions.RELATIVE_TO.marshallAsAttribute(keyStore, writer);
                        FileAttributeDefinitions.PATH.marshallAsAttribute(keyStore, writer);
                        KeyStoreDefinition.REQUIRED.marshallAsAttribute(keyStore, writer);

                        writer.writeEndElement();
                    }

                    KeyStoreDefinition.CREDENTIAL_REFERENCE.marshallAsElement(keyStore, writer);

                    writer.writeEndElement(); // end of KEY_STORE
                }
            }

            ModelNode ldapKeystores = subsystem.get(LDAP_KEY_STORE);
            if (ldapKeystores.isDefined()) {
                for (String name : ldapKeystores.keys()) {
                    ModelNode keyStore = ldapKeystores.require(name);
                    writer.writeStartElement(LDAP_KEY_STORE);
                    writer.writeAttribute(NAME, name);
                    LdapKeyStoreDefinition.DIR_CONTEXT.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.SEARCH_PATH.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.SEARCH_RECURSIVE.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.SEARCH_TIME_LIMIT.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.FILTER_ALIAS.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.FILTER_CERTIFICATE.marshallAsAttribute(keyStore, writer);
                    LdapKeyStoreDefinition.FILTER_ITERATE.marshallAsAttribute(keyStore, writer);

                    ModelNode newItemTemplate = keyStore.get(NEW_ITEM_TEMPLATE);
                    if (newItemTemplate.isDefined()) {
                        writer.writeStartElement(NEW_ITEM_TEMPLATE);
                        LdapKeyStoreDefinition.NewItemTemplateObjectDefinition.NEW_ITEM_PATH.marshallAsAttribute(newItemTemplate, writer);
                        LdapKeyStoreDefinition.NewItemTemplateObjectDefinition.NEW_ITEM_RDN.marshallAsAttribute(newItemTemplate, writer);

                        ModelNode newItemAttributes = newItemTemplate.get(NEW_ITEM_ATTRIBUTES);
                        if (newItemAttributes.isDefined()) {
                            for (ModelNode newItemAttribute : newItemAttributes.asList()) {
                                writer.writeStartElement(ATTRIBUTE);
                                LdapKeyStoreDefinition.NewItemTemplateAttributeObjectDefinition.NAME.marshallAsAttribute(newItemAttribute, writer);
                                LdapKeyStoreDefinition.NewItemTemplateAttributeObjectDefinition.VALUE.getAttributeMarshaller().marshallAsAttribute(LdapKeyStoreDefinition.NewItemTemplateAttributeObjectDefinition.VALUE, newItemAttribute, false, writer);
                                writer.writeEndElement();
                            }
                        }
                        writer.writeEndElement();
                    }

                    if (hasDefinedAny(keyStore, new String[]{
                            ALIAS_ATTRIBUTE,
                            CERTIFICATE_ATTRIBUTE, CERTIFICATE_TYPE,
                            CERTIFICATE_CHAIN_ATTRIBUTE, CERTIFICATE_CHAIN_ENCODING,
                            KEY_ATTRIBUTE, KEY_TYPE
                    })) {
                        writer.writeStartElement(LDAP_MAPPING);
                        LdapKeyStoreDefinition.ALIAS_ATTRIBUTE.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.CERTIFICATE_ATTRIBUTE.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.CERTIFICATE_TYPE.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ATTRIBUTE.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ENCODING.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.KEY_ATTRIBUTE.marshallAsAttribute(keyStore, writer);
                        LdapKeyStoreDefinition.KEY_TYPE.marshallAsAttribute(keyStore, writer);
                        writer.writeEndElement();
                    }
                    writer.writeEndElement(); // end of LDAP_KEY_STORE
                }
            }

            ModelNode filteringKeystores = subsystem.get(FILTERING_KEY_STORE);
            if (filteringKeystores.isDefined()) {
                for (String name : filteringKeystores.keys()) {
                    ModelNode keyStore = filteringKeystores.require(name);
                    writer.writeStartElement(FILTERING_KEY_STORE);
                    writer.writeAttribute(NAME, name);
                    FilteringKeyStoreDefinition.KEY_STORE.marshallAsAttribute(keyStore, writer);
                    FilteringKeyStoreDefinition.ALIAS_FILTER.marshallAsAttribute(keyStore, writer);
                    writer.writeEndElement(); // end of FILTERING_KEY_STORE
                }
            }

            writer.writeEndElement(); // end of KEY_STORES
            return true;
        }
        return false;
    }

    private boolean hasDefinedAny(ModelNode node, String[] keys) {
        for (String key : keys) {
            if (node.hasDefined(key)) return true;
        }
        return false;
    }

}
