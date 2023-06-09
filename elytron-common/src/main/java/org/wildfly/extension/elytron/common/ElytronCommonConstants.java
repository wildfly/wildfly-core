/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron.common;

/**
 * Shared constants used by WildFly Elytron.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */

public interface ElytronCommonConstants {

    String ACCOUNT_KEY = "account-key";
    String ACTIVE_SESSION_COUNT = "active-session-count";
    String AGGREGATE_PROVIDERS = "aggregate-providers";
    String AGREE_TO_TERMS_OF_SERVICE = "agree-to-terms-of-service";
    String ALGORITHM = "algorithm";
    String ALIAS = "alias";
    String ALIAS_ATTRIBUTE = "alias-attribute";
    String ALIAS_FILTER = "alias-filter";
    String APPLICATION_BUFFER_SIZE = "application-buffer-size";
    String ARGUMENT = "argument";
    String ATTRIBUTE = "attribute";
    String ATTRIBUTE_MAPPING = "attribute-mapping";
    String AUTHENTICATION_OPTIONAL = "authentication-optional";
    String CAA_IDENTITIES = "caa-identities";
    String CERTIFICATE = "certificate";
    String CERTIFICATE_ATTRIBUTE = "certificate-attribute";
    String CERTIFICATE_AUTHORITIES = "certificate-authorities";
    String CERTIFICATE_AUTHORITY = "certificate-authority";
    String CERTIFICATE_AUTHORITY_ACCOUNT = "certificate-authority-account";
    String CERTIFICATE_AUTHORITY_ACCOUNTS = "certificate-authority-accounts";
    String CERTIFICATE_CHAIN = "certificate-chain";
    String CERTIFICATE_CHAIN_ATTRIBUTE = "certificate-chain-attribute";
    String CERTIFICATE_CHAIN_ENCODING = "certificate-chain-encoding";
    String CERTIFICATE_REVOCATION_LIST = "certificate-revocation-list";
    String CERTIFICATE_REVOCATION_LISTS = "certificate-revocation-lists";
    String CERTIFICATE_TYPE = "certificate-type";
    String CHANGE_ACCOUNT_KEY = "change-account-key";
    String CHANGE_ALIAS = "change-alias";
    String CIPHER_SUITE = "cipher-suite";
    String CIPHER_SUITE_FILTER = "cipher-suite-filter";
    String CIPHER_SUITE_NAMES = "cipher-suite-names";
    String CLASS_LOADING = "class-loading";
    String CLASS_NAME = "class-name";
    String CLASS_NAMES = "class-names";
    String CLIENT_SSL_CONTEXT = "client-ssl-context";
    String CLIENT_SSL_CONTEXTS = "client-ssl-contexts";
    String CONFIGURATION = "configuration";
    String CONTACT_URLS = "contact-urls";
    String CREATE_ACCOUNT = "create-account";
    String CREATION_DATE = "creation-date";
    String CREATION_TIME = "creation-time";
    String CRITICAL = "critical";
    String DAYS_TO_EXPIRY = "days-to-expiry";
    String DEACTIVATE_ACCOUNT = "deactivate-account";
    String DEFAULT_SSL_CONTEXT = "default-ssl-context";
    String DIR_CONTEXT = "dir-context";
    String DISTINGUISHED_NAME = "distinguished-name";
    String DOMAIN_NAMES = "domain-names";
    String ENCODED = "encoded";
    String ENTRY_TYPE = "entry-type";
    String EXPIRATION = "expiration";
    String EXPORT_CERTIFICATE = "export-certificate";
    String EXTENSION = "extension";
    String EXTENSIONS = "extensions";
    String EXTERNAL_ACCOUNT_REQUIRED = "external-account-required";
    String FILE = "file";
    String FILTER_ALIAS = "filter-alias";
    String FILTER_CERTIFICATE = "filter-certificate";
    String FILTER_ITERATE = "filter-iterate";
    String FILTERING_KEY_STORE = "filtering-key-store";
    String FINAL_PRINCIPAL_TRANSFORMER = "final-principal-transformer";
    String FORMAT = "format";
    String GENERATE_CERTIFICATE_SIGNING_REQUEST = "generate-certificate-signing-request";
    String GENERATE_SELF_SIGNED_CERTIFICATE_HOST = "generate-self-signed-certificate-host";
    String GENERATE_KEY_PAIR = "generate-key-pair";
    String GET_METADATA = "get-metadata";

    String HOST = "host";
    String HOST_CONTEXT_MAP = "host-context-map";
    String IMPLEMENTATION = "implementation";
    String IMPORT_CERTIFICATE = "import-certificate";
    String INDEX = "index";
    String INFO = "info";
    String INIT = "init";
    String INVALIDATE = "invalidate";
    String ISSUER = "issuer";
    String KEY_ATTRIBUTE = "key-attribute";
    String KEY_MANAGER = "key-manager";
    String KEY_MANAGERS = "key-managers";
    String KEY_SIZE = "key-size";
    String KEY_STORE = "key-store";
    String KEY_STORES = "key-stores";
    String KEY_TYPE = "key-type";
    String LAST_ACCESSED_TIME = "last-accessed-time";
    String LDAP_KEY_STORE = "ldap-key-store";
    String LOAD = "load";
    String LOAD_SERVICES = "load-services";
    String LOADED_PROVIDER = "loaded-provider";
    String LOADED_PROVIDERS = "loaded-providers";
    String LOCAL_CERTIFICATES = "local-certificates";
    String LOCAL_PRINCIPAL = "local-principal";
    String MAXIMUM_CERT_PATH = "maximum-cert-path";
    String MAXIMUM_SESSION_CACHE_SIZE = "maximum-session-cache-size";
    String MODIFIABLE_KEY_STORE = "modifiable-key-store";
    String MODIFIED = "modified";
    String MODULE = "module";
    String NAME = "name";
    String NEED_CLIENT_AUTH = "need-client-auth";
    String NEW_ALIAS = "new-alias";
    String NEW_ITEM_ATTRIBUTES = "new-item-attributes";
    String NEW_ITEM_PATH = "new-item-path";
    String NEW_ITEM_RDN = "new-item-rdn";
    String NEW_ITEM_TEMPLATE = "new-item-template";
    String NOT_AFTER = "not-after";
    String NOT_BEFORE = "not-before";
    String OBTAIN_CERTIFICATE = "obtain-certificate";
    String OCSP = "ocsp";
    String ONLY_LEAF_CERT = "only-leaf-cert";
    String PACKET_BUFFER_SIZE = "packet-buffer-size";
    String PATH = "path";
    String PEER_CERTIFICATES = "peer-certificates";
    String PEER_HOST = "peer-host";
    String PEER_PORT = "peer-port";
    String PEER_PRINCIPAL = "peer-principal";
    String PEM = "pem";
    String POST_REALM_PRINCIPAL_TRANSFORMER = "post-realm-principal-transformer";
    String PRE_REALM_PRINCIPAL_TRANSFORMER = "pre-realm-principal-transformer";
    String PREFER_CRLS = "prefer-crls";
    String PROPERTY = "property";
    String PROPERTY_LIST = "property-list";
    String PROTOCOL = "protocol";
    String PROTOCOLS = "protocols";
    String PROVIDER = "provider";
    String PROVIDER_LOADER = "provider-loader";
    String PROVIDER_NAME = "provider-name";
    String PROVIDER_REGISTRATION = "provider-registration";
    String PROVIDERS = "providers";
    String PUBLIC_KEY = "public-key";
    String READ_ALIAS = "read-alias";
    String READ_ALIASES = "read-aliases";
    String REALM_MAPPER = "realm-mapper";
    String REASON = "reason";
    String RECURSIVE = "recursive";
    String RELATIVE_TO = "relative-to";
    String RELOAD_CERTIFICATE_REVOCATION_LIST = "reload-certificate-revocation-list";
    String REMOVE_ALIAS = "remove-alias";
    String REQUIRED = "required";
    String RESPONDER = "responder";
    String RESPONDER_CERTIFICATE = "responder-certificate";
    String RESPONDER_KEYSTORE = "responder-keystore";
    String REVOKE_CERTIFICATE = "revoke-certificate";
    String SEARCH = "search";
    String SEARCH_PATH = "search-path";
    String SEARCH_RECURSIVE = "search-recursive";
    String SEARCH_TIME_LIMIT = "search-time-limit";
    String SECURITY_DOMAIN = "security-domain";
    String SECURITY_PROPERTIES = "security-properties";
    String SERIAL_NUMBER = "serial-number";
    String SERVER_SSL_CONTEXT = "server-ssl-context";
    String SERVER_SSL_CONTEXTS = "server-ssl-contexts";
    String SERVER_SSL_SNI_CONTEXT = "server-ssl-sni-context";
    String SERVER_SSL_SNI_CONTEXTS = "server-ssl-sni-contexts";
    String SERVICE = "service";
    String SERVICES = "services";
    String SESSION_TIMEOUT = "session-timeout";
    String SHA_1_DIGEST = "sha-1-digest";
    String SHA_256_DIGEST = "sha-256-digest";
    String SHOULD_RENEW_CERTIFICATE = "should-renew-certificate";
    String SIGNATURE = "signature";
    String SIGNATURE_ALGORITHM = "signature-algorithm";
    String SIZE = "size";
    String SNI_MAPPING = "sni-mapping";
    String SOFT_FAIL = "soft-fail";
    String SSL_CONTEXT = "ssl-context";
    String SSL_SESSION = "ssl-session";
    String STAGING = "staging";
    String STAGING_URL = "staging-url";
    String STATE = "state";
    String STORE = "store";
    String SUBJECT = "subject";
    String SYNCHRONIZED = "synchronized";
    String TERMS_OF_SERVICE = "terms-of-service";
    String TLS = "tls";
    String TRUST_CACERTS = "trust-cacerts";
    String TRUST_MANAGER = "trust-manager";
    String TRUST_MANAGERS = "trust-managers";
    String TYPE = "type";
    String UPDATE_ACCOUNT = "update-account";
    String URL = "url";
    String USE_CIPHER_SUITES_ORDER = "use-cipher-suites-order";
    String VALID = "valid";
    String VALIDATE = "validate";
    String VALIDITY = "validity";
    String VALUE = "value";
    String VERBOSE = "verbose";
    String VERSION = "version";
    String WANT_CLIENT_AUTH = "want-client-auth";
    String WRAP = "wrap";
    String WEBSITE = "website";
}
