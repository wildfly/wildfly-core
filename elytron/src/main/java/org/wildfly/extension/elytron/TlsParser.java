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
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLIENT_SSL_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLIENT_SSL_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERING_KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_MANAGER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_MANAGERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LDAP_KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVER_SSL_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVER_SSL_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TLS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGERS;

import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the TLS related definitions.
 *
 * @author Darran Lofthouse
 * @author Tomaz Cerar
 */
class TlsParser {
    private final ElytronSubsystemParser elytronSubsystemParser;

    private PersistentResourceXMLDescription keyManagerParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(KEY_MANAGER))
            .setXmlWrapperElement("key-managers")
            .addAttribute(SSLDefinitions.ALGORITHM)
            .addAttribute(SSLDefinitions.KEYSTORE)
            .addAttribute(SSLDefinitions.ALIAS_FILTER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .addAttribute(CredentialReference.getAttributeDefinition())
            .build();

    private PersistentResourceXMLDescription keyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(KEY_STORE))
            //.setXmlWrapperElement("key-stores")
            .addAttribute(KeyStoreDefinition.TYPE)
            .addAttribute(KeyStoreDefinition.PROVIDER_NAME)
            .addAttribute(KeyStoreDefinition.PROVIDERS)
            .addAttribute(KeyStoreDefinition.CREDENTIAL_REFERENCE)
            .addAttribute(KeyStoreDefinition.ALIAS_FILTER)
            .addAttribute(KeyStoreDefinition.REQUIRED)
            .addAttribute(FileAttributeDefinitions.PATH)
            .addAttribute(FileAttributeDefinitions.RELATIVE_TO)
            .addAttribute(CredentialReference.getAttributeDefinition())
            .build();

    private PersistentResourceXMLDescription ldapKeyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(LDAP_KEY_STORE))
            .addAttribute(LdapKeyStoreDefinition.DIR_CONTEXT)
            .addAttribute(LdapKeyStoreDefinition.SEARCH_PATH)
            .addAttribute(LdapKeyStoreDefinition.SEARCH_RECURSIVE)
            .addAttribute(LdapKeyStoreDefinition.SEARCH_TIME_LIMIT)
            .addAttribute(LdapKeyStoreDefinition.FILTER_ALIAS)
            .addAttribute(LdapKeyStoreDefinition.FILTER_CERTIFICATE)
            .addAttribute(LdapKeyStoreDefinition.FILTER_ITERATE)
            .addAttribute(LdapKeyStoreDefinition.NewItemTemplateObjectDefinition.OBJECT_DEFINITION)
            //attribute mapping, attribute group==attribute-mapping
            .addAttribute(LdapKeyStoreDefinition.ALIAS_ATTRIBUTE)
            .addAttribute(LdapKeyStoreDefinition.CERTIFICATE_ATTRIBUTE)
            .addAttribute(LdapKeyStoreDefinition.CERTIFICATE_TYPE)
            .addAttribute(LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ATTRIBUTE)
            .addAttribute(LdapKeyStoreDefinition.CERTIFICATE_CHAIN_ENCODING)
            .addAttribute(LdapKeyStoreDefinition.KEY_ATTRIBUTE)
            .addAttribute(LdapKeyStoreDefinition.KEY_TYPE)
            .setMarshallDefaultValues(true)
            .build();

    private PersistentResourceXMLDescription trustManagerParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(TRUST_MANAGER))
            .setXmlWrapperElement("trust-managers")
            .addAttribute(SSLDefinitions.ALGORITHM)
            .addAttribute(SSLDefinitions.KEYSTORE)
            .addAttribute(SSLDefinitions.ALIAS_FILTER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .addAttribute(SSLDefinitions.CERTIFICATE_REVOCATION_LIST)
            .build();

    private PersistentResourceXMLDescription filteringKeyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(FILTERING_KEY_STORE))
            .addAttribute(FilteringKeyStoreDefinition.KEY_STORE)
            .addAttribute(FilteringKeyStoreDefinition.ALIAS_FILTER)
            .build();

    private PersistentResourceXMLDescription serverSslContextParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SERVER_SSL_CONTEXT))
            .setXmlWrapperElement("server-ssl-contexts")
            .setMarshallDefaultValues(true)
            .addAttribute(SSLDefinitions.SECURITY_DOMAIN)
            .addAttribute(SSLDefinitions.CIPHER_SUITE_FILTER)
            .addAttribute(SSLDefinitions.PROTOCOLS)
            .addAttribute(SSLDefinitions.WANT_CLIENT_AUTH)
            .addAttribute(SSLDefinitions.NEED_CLIENT_AUTH)
            .addAttribute(SSLDefinitions.AUTHENTICATION_OPTIONAL)
            .addAttribute(SSLDefinitions.USE_CIPHER_SUITES_ORDER)
            .addAttribute(SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE)
            .addAttribute(SSLDefinitions.SESSION_TIMEOUT)
            .addAttribute(SSLDefinitions.WRAP)
            .addAttribute(SSLDefinitions.KEY_MANAGER)
            .addAttribute(SSLDefinitions.TRUST_MANAGER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .addAttribute(SSLDefinitions.PRE_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(SSLDefinitions.POST_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(SSLDefinitions.FINAL_PRINCIPAL_TRANSFORMER)
            .addAttribute(SSLDefinitions.REALM_MAPPER)
            .build();

    private PersistentResourceXMLDescription clientSslContextParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CLIENT_SSL_CONTEXT))
            .setXmlWrapperElement(CLIENT_SSL_CONTEXTS)
            .addAttribute(SSLDefinitions.SECURITY_DOMAIN)
            .addAttribute(SSLDefinitions.CIPHER_SUITE_FILTER)
            .addAttribute(SSLDefinitions.PROTOCOLS)
            .addAttribute(SSLDefinitions.WANT_CLIENT_AUTH)
            .addAttribute(SSLDefinitions.NEED_CLIENT_AUTH)
            .addAttribute(SSLDefinitions.AUTHENTICATION_OPTIONAL)
            .addAttribute(SSLDefinitions.USE_CIPHER_SUITES_ORDER)
            .addAttribute(SSLDefinitions.MAXIMUM_SESSION_CACHE_SIZE)
            .addAttribute(SSLDefinitions.SESSION_TIMEOUT)
            .addAttribute(SSLDefinitions.WRAP)
            .addAttribute(SSLDefinitions.KEY_MANAGER)
            .addAttribute(SSLDefinitions.TRUST_MANAGER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .build();

    TlsParser(ElytronSubsystemParser elytronSubsystemParser) {
        this.elytronSubsystemParser = elytronSubsystemParser;
    }

    private void verifyNamespace(XMLExtendedStreamReader reader) throws XMLStreamException {
        elytronSubsystemParser.verifyNamespace(reader);
    }

    void readTls(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        boolean keyManagersFound = false;
        boolean keyStoresFound = false;
        boolean trustManagersFound = false;
        boolean serverSSLContextsFound = false;
        boolean clientSSLContextsFound = false;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEY_MANAGERS.equals(localName) && keyManagersFound == false) {
                keyManagersFound = true;
                readWithWrapper(parentAddress, reader, operations, keyManagerParser);
            } else if (KEY_STORES.equals(localName) && keyStoresFound == false) {
                keyStoresFound = true;
                readKeyStores(parentAddress, reader, operations);
            } else if (TRUST_MANAGERS.equals(localName) && trustManagersFound == false) {
                trustManagersFound = true;
                readWithWrapper(parentAddress, reader, operations, trustManagerParser);
            } else if (SERVER_SSL_CONTEXTS.equals(localName) && serverSSLContextsFound == false) {
                serverSSLContextsFound = true;
                readWithWrapper(parentAddress, reader, operations, serverSslContextParser);
            } else if (CLIENT_SSL_CONTEXTS.equals(localName) && clientSSLContextsFound == false) {
                clientSSLContextsFound = true;
                readWithWrapper(parentAddress, reader, operations, clientSslContextParser);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readWithWrapper(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations, PersistentResourceXMLDescription parser) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (parser.getPathElement().getKey().equals(localName)) {
                parser.parse(reader, parentAddress, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }


    private void readKeyStores(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEY_STORE.equals(localName)) {
                keyStoreParser.parse(reader, parentAddress, operations);
            } else if (LDAP_KEY_STORE.equals(localName)) {
                ldapKeyStoreParser.parse(reader, parentAddress, operations);
            } else if (FILTERING_KEY_STORE.equals(localName)) {
                filteringKeyStoreParser.parse(reader, parentAddress, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void startTLS(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(TLS);
        }
    }

    void writeTLS(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        boolean tlsStarted = false;

        tlsStarted = tlsStarted | writeKeyStores(tlsStarted, subsystem, writer);
        tlsStarted = tlsStarted | writeElement(tlsStarted, subsystem, writer, keyManagerParser);
        tlsStarted = tlsStarted | writeElement(tlsStarted, subsystem, writer, trustManagerParser);
        tlsStarted = tlsStarted | writeElement(tlsStarted, subsystem, writer, serverSslContextParser);
        tlsStarted = tlsStarted | writeElement(tlsStarted, subsystem, writer, clientSslContextParser);

        if (tlsStarted) {
            writer.writeEndElement();
        }
    }

    private boolean writeElement(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer, PersistentResourceXMLDescription parser) throws XMLStreamException {
        if (subsystem.hasDefined(parser.getPathElement().getKey())) {
            startTLS(started, writer);
            parser.persist(writer, subsystem);
            return true;
        }
        return false;
    }

    private boolean writeKeyStores(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(KEY_STORE) || subsystem.hasDefined(LDAP_KEY_STORE) || subsystem.hasDefined(FILTERING_KEY_STORE)) {
            startTLS(started, writer);
            writer.writeStartElement(KEY_STORES);
            keyStoreParser.persist(writer, subsystem);
            ldapKeyStoreParser.persist(writer, subsystem);
            filteringKeyStoreParser.persist(writer, subsystem);
            writer.writeEndElement(); // end of KEY_STORES
            return true;
        }
        return false;
    }
}
