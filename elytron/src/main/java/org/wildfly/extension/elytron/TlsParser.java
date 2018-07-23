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

import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNTS;
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

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.security.CredentialReference;

/**
 * A parser for the TLS related definitions.
 *
 * @author Darran Lofthouse
 * @author Tomaz Cerar
 */
class TlsParser {
    private PersistentResourceXMLBuilder keyManagerParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(KEY_MANAGER))
            .setXmlWrapperElement(KEY_MANAGERS)
            .addAttribute(SSLDefinitions.ALGORITHM)
            .addAttribute(SSLDefinitions.KEYSTORE)
            .addAttribute(SSLDefinitions.ALIAS_FILTER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .addAttribute(CredentialReference.getAttributeDefinition());

    private PersistentResourceXMLBuilder keyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(KEY_STORE))
            .addAttribute(KeyStoreDefinition.TYPE)
            .addAttribute(KeyStoreDefinition.PROVIDER_NAME)
            .addAttribute(KeyStoreDefinition.PROVIDERS)
            .addAttribute(KeyStoreDefinition.CREDENTIAL_REFERENCE)
            .addAttribute(KeyStoreDefinition.ALIAS_FILTER)
            .addAttribute(KeyStoreDefinition.REQUIRED)
            .addAttribute(FileAttributeDefinitions.PATH)
            .addAttribute(FileAttributeDefinitions.RELATIVE_TO)
            .addAttribute(CredentialReference.getAttributeDefinition());

    private PersistentResourceXMLBuilder ldapKeyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(LDAP_KEY_STORE))
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
            .setMarshallDefaultValues(true);

    private PersistentResourceXMLBuilder trustManagerParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(TRUST_MANAGER))
            .setXmlWrapperElement(TRUST_MANAGERS)
            .addAttribute(SSLDefinitions.ALGORITHM)
            .addAttribute(SSLDefinitions.KEYSTORE)
            .addAttribute(SSLDefinitions.ALIAS_FILTER)
            .addAttribute(SSLDefinitions.PROVIDERS)
            .addAttribute(SSLDefinitions.PROVIDER_NAME)
            .addAttribute(SSLDefinitions.CERTIFICATE_REVOCATION_LIST);

    private PersistentResourceXMLBuilder filteringKeyStoreParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(FILTERING_KEY_STORE))
            .addAttribute(FilteringKeyStoreDefinition.KEY_STORE)
            .addAttribute(FilteringKeyStoreDefinition.ALIAS_FILTER);

    private PersistentResourceXMLBuilder serverSslContextParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SERVER_SSL_CONTEXT))
            .setXmlWrapperElement(SERVER_SSL_CONTEXTS)
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
            .addAttribute(SSLDefinitions.REALM_MAPPER);

    private PersistentResourceXMLBuilder clientSslContextParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CLIENT_SSL_CONTEXT))
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
            .addAttribute(SSLDefinitions.PROVIDER_NAME);

    private PersistentResourceXMLBuilder certificateAuthorityAccountParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CERTIFICATE_AUTHORITY_ACCOUNT))
            .setXmlWrapperElement(CERTIFICATE_AUTHORITY_ACCOUNTS)
            .addAttribute(CertificateAuthorityAccountDefinition.CERTIFICATE_AUTHORITY)
            .addAttribute(CertificateAuthorityAccountDefinition.CONTACT_URLS)
            .addAttribute(CertificateAuthorityAccountDefinition.KEY_STORE)
            .addAttribute(CertificateAuthorityAccountDefinition.ALIAS)
            .addAttribute(CertificateAuthorityAccountDefinition.CREDENTIAL_REFERENCE);

    final PersistentResourceXMLDescription tlsParser = decorator(TLS)
            .addChild(decorator(KEY_STORES)
                    .addChild(keyStoreParser)
                    .addChild(ldapKeyStoreParser)
                    .addChild(filteringKeyStoreParser)

            )
            .addChild(keyManagerParser)
            .addChild(trustManagerParser)
            .addChild(serverSslContextParser)
            .addChild(clientSslContextParser)
            .addChild(certificateAuthorityAccountParser)
            .build();

}
