/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

/**
 * Constants used in the Elytron subsystem.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
interface ElytronDescriptionConstants extends ElytronCommonConstants {

    String ACTION = "action";
    String ADD_IDENTITY = "add-identity";
    String ADD_IDENTITY_ATTRIBUTE = "add-identity-attribute";
    String ADD_ALIAS = "add-alias";
    String ADD_ATTRIBUTE = "add-attribute";
    String ADD_PREFIX_ROLE_MAPPER = "add-prefix-role-mapper";
    String ADD_SUFFIX_ROLE_MAPPER = "add-suffix-role-mapper";
    String AGGREGATE_EVIDENCE_DECODER = "aggregate-evidence-decoder";
    String AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY = "aggregate-http-server-mechanism-factory";
    String AGGREGATE_PRINCIPAL_DECODER = "aggregate-principal-decoder";
    String AGGREGATE_PRINCIPAL_TRANSFORMER = "aggregate-principal-transformer";
    String AGGREGATE_REALM = "aggregate-realm";
    String AGGREGATE_ROLE_DECODER = "aggregate-role-decoder";
    String AGGREGATE_ROLE_MAPPER = "aggregate-role-mapper";
    String AGGREGATE_SASL_SERVER_FACTORY = "aggregate-sasl-server-factory";
    String AGGREGATE_SECURITY_EVENT_LISTENER = "aggregate-security-event-listener";
    String ALGORITHM_FROM = "algorithm-from";
    String ALLOW_BLANK_PASSWORD = "allow-blank-password";
    String ALT_NAME_TYPE = "alt-name-type";
    String AND = "and";
    String ANONYMOUS = "anonymous";
    String APPLICATION_CONTEXT = "application-context";
    String ATTRIBUTE_NAME = "attribute-name";
    String ATTRIBUTE_VALUES = "attribute-values";
    String ATTRIBUTES = "attributes";
    String AUDIENCE = "audience";
    String AUDIT_LOGGING = "audit-logging";
    String AUTHENTICATION = "authentication";
    String AUTHENTICATION_CLIENT = "authentication-client";
    String AUTHENTICATION_CONFIGURATION = "authentication-configuration";
    String AUTHENTICATION_CONTEXT = "authentication-context";
    String AUTHENTICATION_CONTEXT_REGISTRATION = "authentication-context-registration";
    String AUTHENTICATION_LEVEL = "authentication-level";
    String AUTHENTICATION_NAME = "authentication-name";
    String AUTHENTICATION_REALM = "authentication-realm";
    String AUTHORIZATION = "authorization";
    String AUTHORIZATION_NAME = "authorization-name";
    String AUTHORIZATION_REALM = "authorization-realm";
    String AUTHORIZATION_REALMS = "authorization-realms";
    String AUTH_METHOD = "auth-method";
    String AUTOFLUSH = "autoflush";
    String AVAILABLE_MECHANISMS = "available-mechanisms";

    String BASE64 = "base64";
    String BCRYPT = "bcrypt";
    String BCRYPT_MAPPER = "bcrypt-mapper";

    String CACHING_REALM = "caching-realm";
    String CASE_PRINCIPAL_TRANSFORMER = "case-principal-transformer";
    String CALLBACK_HANDLER = "callback-handler";
    String CERTIFICATE_FROM = "certificate-from";
    String CHAINED_PRINCIPAL_TRANSFORMER = "chained-principal-transformer";
    String UPDATE_KEY_PAIR = "update-key-pair";
    String CLEAR = "clear";
    String CLEAR_CACHE = "clear-cache";
    String CLEAR_PASSWORD_MAPPER = "clear-password-mapper";
    String CLEAR_TEXT = "clear-text";
    String CLIENT_ID = "client-id";
    String CLIENT_SECRET = "client-secret";
    String CONCATENATING_PRINCIPAL_DECODER = "concatenating-principal-decoder";
    String CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY = "configurable-http-server-mechanism-factory";
    String CONFIGURABLE_SASL_SERVER_FACTORY = "configurable-sasl-server-factory";
    String CONFIGURATION_FACTORY = "configuration-factory";
    String CONFIGURATION_FILE = "configuration-file";
    String CONFIGURATION_PROPERTIES = "configuration-properties";
    String CONNECTION_TIMEOUT = "connection-timeout";
    String CONSTANT = "constant";
    String CONSTANT_PERMISSION_MAPPER = "constant-permission-mapper";
    String CONSTANT_PRINCIPAL_DECODER = "constant-principal-decoder";
    String CONSTANT_PRINCIPAL_TRANSFORMER = "constant-principal-transformer";
    String CONSTANT_REALM_MAPPER = "constant-realm-mapper";
    String CONSTANT_ROLE_MAPPER = "constant-role-mapper";
    String CONVERT = "convert";
    String CREATE = "create";
    String CREATE_EXPRESSION = "create-expression";
    String CREDENTIAL = "credential";
    String CREDENTIAL_SECURITY_FACTORIES = "credential-security-factories";
    String CREDENTIAL_SECURITY_FACTORY = "credential-security-factory";
    String CREDENTIAL_STORE = "credential-store";
    String CREDENTIAL_STORES = "credential-stores";
    String CREDENTIALS = "credentials";

    String CUSTOM_CREDENTIAL_SECURITY_FACTORY = "custom-credential-security-factory";
    String CUSTOM_EVIDENCE_DECODER = "custom-evidence-decoder";
    String CUSTOM_PERMISSION_MAPPER = "custom-permission-mapper";
    String CUSTOM_POLICY = "custom-policy";
    String CUSTOM_PRINCIPAL_DECODER = "custom-principal-decoder";
    String CUSTOM_PRINCIPAL_TRANSFORMER = "custom-principal-transformer";
    String CUSTOM_REALM = "custom-realm";
    String CUSTOM_MODIFIABLE_REALM = "custom-modifiable-realm";
    String CUSTOM_REALM_MAPPER = "custom-realm-mapper";
    String CUSTOM_ROLE_DECODER = "custom-role-decoder";
    String CUSTOM_ROLE_MAPPER = "custom-role-mapper";
    String CUSTOM_SECURITY_EVENT_LISTENER = "custom-security-event-listener";

    String DATA_SOURCE = "data-source";
    String DEBUG = "debug";
    String DEFAULT_ALIAS = "default-alias";
    String DEFAULT_AUTHENTICATION_CONTEXT = "default-authentication-context";
    String DEFAULT_POLICY = "default-policy";
    String DEFAULT_REALM = "default-realm";
    String DEFAULT_RESOLVER = "default-resolver";
    String DELEGATE_REALM = "delegate-realm";
    String DELEGATE_REALM_MAPPER = "delegate-realm-mapper";
    String DESCRIPTION = "description";
    String DIGEST = "digest";
    String DIGEST_ALGORITHM = "digest-algorithm";
    String DIGEST_FROM = "digest-from";
    String DIGEST_REALM_NAME = "digest-realm-name";
    String DIR_CONTEXTS = "dir-contexts";
    String DIRECT_VERIFICATION = "direct-verification";
    String DISALLOWED_PROVIDERS = "disallowed-providers";
    String DISTRIBUTED_REALM = "distributed-realm";

    String ELYTRON_SECURITY = "elytron-security";
    String ENABLE_CONNECTION_POOLING = "enable-connection-pooling";
    String ENABLING = "enabling";
    String ENCRYPTION = "encryption";
    String ENTRY = "entry";
    String ENCODING = "encoding";
    String EVIDENCE_DECODER = "evidence-decoder";
    String EVIDENCE_DECODERS = "evidence-decoders";
    String EXPORT_SECRET_KEY = "export-secret-key";
    String EXPRESSION = "expression";
    String EXPRESSION_RESOLVER = "expression-resolver";
    String EXTRACT_RDN = "extract-rdn";
    String EXTENDS = "extends";

    String FAIL_CACHE = "fail-cache";
    String FAILOVER_REALM = "failover-realm";
    String FILE_AUDIT_LOG = "file-audit-log";
    String FILESYSTEM_REALM = "filesystem-realm";
    String FILTER = "filter";
    String FILTER_BASE_DN = "filter-base-dn";
    String FILTER_NAME = "filter-name";
    String FILTERS = "filters";
    String FINAL_PROVIDERS = "final-providers";
    String FIRST = "first";
    String FLAG = "flag";
    String FORWARDING_MODE = "forwarding-mode";
    String FROM = "from";

    String GENERATE_SECRET_KEY = "generate-secret-key";
    String GREATER_THAN = "greater-than";
    String GROUPS = "groups";
    String GROUPS_ATTRIBUTE = "groups-attribute";
    String GROUPS_PROPERTIES = "groups-properties";
    String HOST_NAME = "host-name";
    String HOST_NAME_VERIFICATION_POLICY = "host-name-verification-policy";
    String HASH_CHARSET = "hash-charset";
    String HASH_ENCODING = "hash-encoding";
    String HASH_FROM = "hash-from";
    String HEX = "hex";
    String HTTP = "http";
    String HTTP_AUTHENTICATION_FACTORY = "http-authentication-factory";
    String HTTP_MECHANISM = "http-mechanism";
    String HTTP_SERVER_MECHANISM_FACTORY = "http-server-mechanism-factory";
    String HTTP_SERVER_MECHANISM_FACTORIES = "http-server-mechanism-factories";

    String IDENTITY = "identity";
    String IDENTITY_MAPPING = "identity-mapping";
    String IDENTITY_REALM = "identity-realm";
    String IGNORE_UNAVAILABLE_REALMS = "ignore-unavailable-realms";
    String IMPLEMENTATION_PROPERTIES = "implementation-properties";
    String IMPORT_SECRET_KEY = "import-secret-key";
    String INITIAL = "initial";
    String INITIAL_PROVIDERS = "initial-providers";
    String INTROSPECTION_URL = "introspection-url";
    String ITERATION_COUNT = "iteration-count";
    String ITERATION_COUNT_INDEX = "iteration-count-index";
    String ITERATOR_FILTER = "iterator-filter";

    String JAAS_REALM = "jaas-realm";
    String JACC_POLICY = "jacc-policy";
    String JASPI = "jaspi";
    String JASPI_CONFIGURATION = "jaspi-configuration";
    String JDBC_REALM = "jdbc-realm";
    String JOINER = "joiner";
    String JWT = "jwt";

    String KEEP_MAPPED = "keep-mapped";
    String KEEP_NON_MAPPED = "keep-non-mapped";
    String KERBEROS_SECURITY_FACTORY = "kerberos-security-factory";
    String KEY = "key";
    String KEY_MAP = "key-map";
    String KEY_STORE_ALIAS = "key-store-alias";
    String KEY_STORE_REALM = "key-store-realm";
    String KID = "kid";

    String LAYER = "layer";
    String LDAP_MAPPING = "ldap-mapping";
    String LDAP_REALM = "ldap-realm";
    String LEFT = "left";
    String LESS_THAN = "less-than";
    String LEVELS = "levels";
    String LOCATION = "location";
    String EMIT_EVENTS = "emit-events";
    String LOGICAL_OPERATION = "logical-operation";
    String LOGICAL_PERMISSION_MAPPER = "logical-permission-mapper";
    String LOGICAL_ROLE_MAPPER = "logical-role-mapper";

    String MAPPED_REGEX_REALM_MAPPER = "mapped-regex-realm-mapper";
    String MAPPED_ROLE_MAPPER = "mapped-role-mapper";
    String MAPPERS = "mappers";
    String MAPPING_MODE = "mapping-mode";
    String MATCH = "match";
    String MATCH_ABSTRACT_TYPE = "match-abstract-type";
    String MATCH_ABSTRACT_TYPE_AUTHORITY = "match-abstract-type-authority";
    String MATCH_ALL = "match-all";
    String MATCH_HOST = "match-host";
    String MATCH_LOCAL_SECURITY_DOMAIN = "match-local-security-domain";
    String MATCH_NO_USER = "match-no-user";
    String MATCH_PATH = "match-path";
    String MATCH_PORT = "match-port";
    String MATCH_PROTOCOL = "match-protocol";
    String MATCH_RULE = "match-rule";
    String MATCH_RULES = "match-rules";
    String MATCH_URN = "match-urn";
    String MATCH_USER = "match-user";
    String MAXIMUM_AGE = "maximum-age";
    String MAXIMUM_ENTRIES = "maximum-entries";
    String MAXIMUM_SEGMENTS = "maximum-segments";
    String MAX_BACKUP_INDEX = "max-backup-index";
    String MECHANISM = "mechanism";
    String MECHANISM_CONFIGURATION = "mechanism-configuration";
    String MECHANISM_CONFIGURATIONS = "mechanism-configurations";
    String MECHANISM_NAME = "mechanism-name";
    String MECHANISM_NAMES = "mechanism-names";
    String MECHANISM_OIDS = "mechanism-oids";
    String MECHANISM_PROPERTIES = "mechanism-properties";
    String MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY = "mechanism-provider-filtering-sasl-server-factory";
    String MECHANISM_REALM = "mechanism-realm";
    String MECHANISM_REALM_CONFIGURATION = "mechanism-realm-configuration";
    String MECHANISM_REALM_CONFIGURATIONS = "mechanism-realm-configurations";
    String MINIMUM_REMAINING_LIFETIME = "minimum-remaining-lifetime";
    String MINUS = "minus";
    String MODIFIABLE = "modifiable";
    String MODIFIABLE_SECURITY_REALM = "modifiable-security-realm";
    String MODULAR_CRYPT_MAPPER = "modular-crypt-mapper";

    String NEW_IDENTITY_ATTRIBUTES = "new-identity-attributes";
    String NEW_IDENTITY_PARENT_DN = "new-identity-parent-dn";

    String OAUTH2_INTROSPECTION = "oauth2-introspection";
    String OBTAIN_KERBEROS_TICKET = "obtain-kerberos-ticket";
    String OID = "oid";
    String OPERATIONS = "operations";
    String OTHER_PROVIDERS = "other-providers";
    String OTP = "otp";
    String OTP_CREDENTIAL_MAPPER = "otp-credential-mapper";
    String OPTION = "option";
    String OPTIONAL = "optional";
    String OPTIONS = "options";
    String OR = "or";
    String OUTFLOW_ANONYMOUS = "outflow-anonymous";
    String OUTFLOW_SECURITY_DOMAINS = "outflow-security-domains";

    String PASSWORD = "password";
    String PASSWORD_INDEX = "password-index";
    String PATTERN = "pattern";
    String PATTERN_FILTER = "pattern-filter";
    String PERIODIC_ROTATING_FILE_AUDIT_LOG = "periodic-rotating-file-audit-log";
    String PERMISSION = "permission";
    String PERMISSIONS = "permissions";
    String PERMISSION_MAPPER = "permission-mapper";
    String PERMISSION_MAPPING = "permission-mapping";
    String PERMISSION_MAPPINGS = "permission-mappings";
    String PERMISSION_SET = "permission-set";
    String PERMISSION_SETS = "permission-sets";
    String PLAIN_TEXT = "plain-text";
    String POLICY = "policy";
    String POPULATE = "populate";
    String PORT = "port";
    String PREDEFINED_FILTER = "predefined-filter";
    String PREFIX = "prefix";
    String PRINCIPAL = "principal";
    String PRINCIPALS = "principals";
    String PRINCIPAL_CLAIM = "principal-claim";
    String PRINCIPAL_DECODER = "principal-decoder";
    String PRINCIPAL_DECODERS = "principal-decoders";
    String PRINCIPAL_TRANSFORMER = "principal-transformer";
    String PRINCIPAL_TRANSFORMERS = "principal-transformers";
    String PRINCIPAL_QUERY = "principal-query";
    String PROPERTIES = "properties";
    String PROPERTIES_REALM = "properties-realm";
    String PROVIDER_HTTP_SERVER_MECHANISM_FACTORY = "provider-http-server-mechanism-factory";
    String PROVIDER_SASL_SERVER_FACTORY = "provider-sasl-server-factory";
    String PROVIDER_VERSION = "provider-version";

    String RDN_IDENTIFIER = "rdn-identifier";
    String READ_IDENTITY = "read-identity";
    String READ_TIMEOUT = "read-timeout";
    String REALM = "realm";
    String REALM_MAP = "realm-map";
    String REALM_MAPPING = "realm-mapping";
    String REALM_NAME = "realm-name";
    String REALMS = "realms";
    String RECONNECT_ATTEMPTS = "reconnect-attempts";
    String REFERENCE = "reference";
    String REFERRAL_MODE = "referral-mode";
    String REGISTER_JASPI_FACTORY = "register-jaspi-factory";
    String REGEX_PRINCIPAL_TRANSFORMER = "regex-principal-transformer";
    String REGEX_ROLE_MAPPER = "regex-role-mapper";
    String REGEX_VALIDATING_PRINCIPAL_TRANSFORMER = "regex-validating-principal-transformer";
    String REMOVE_IDENTITY = "remove-identity";
    String REMOVE_IDENTITY_ATTRIBUTE = "remove-identity-attribute";
    String REPLACE_ALL = "replace-all";
    String REPLACEMENT = "replacement";
    String REQUEST_LIFETIME = "request-lifetime";
    String REQUIRED_OIDS = "required-oids";
    String REQUIRED_ATTRIBUTES = "required-attributes";
    String REQUISITE = "requisite";
    String RESOLVER = "resolver";
    String RESOLVERS = "resolvers";
    String REVERSE = "reverse";
    String RIGHT = "right";
    String ROLE_DECODER = "role-decoder";
    String ROLE_DECODERS = "role-decoders";
    String ROLE_RECURSION = "role-recursion";
    String ROLE_RECURSION_NAME = "role-recursion-name";
    String ROLE_MAP = "role-map";
    String ROLE_MAPPER = "role-mapper";
    String ROLE_MAPPERS = "role-mappers";
    String ROLE_MAPPING = "role-mapping";
    String ROLES = "roles";
    String ROTATE_SIZE = "rotate-size";
    String ROTATE_ON_BOOT = "rotate-on-boot";

    String SALT = "salt";
    String SALT_ENCODING = "salt-encoding";
    String SALT_INDEX = "salt-index";
    String SALTED_SIMPLE_DIGEST = "salted-simple-digest";
    String SALTED_SIMPLE_DIGEST_MAPPER = "salted-simple-digest-mapper";
    String SASL = "sasl";
    String SASL_AUTHENTICATION_FACTORY = "sasl-authentication-factory";
    String SASL_MECHANISM_SELECTOR = "sasl-mechanism-selector";
    String SASL_SERVER_FACTORIES = "sasl-server-factories";
    String SASL_SERVER_FACTORY = "sasl-server-factory";
    String SCRAM_DIGEST = "scram-digest";
    String SCRAM_MAPPER = "scram-mapper";
    String SEARCH_BASE_DN = "search-base-dn";
    String SECURITY_DOMAINS = "security-domains";
    String SECURITY_EVENT_LISTENER = "security-event-listener";
    String SECURITY_EVENT_LISTENERS = "security-event-listeners";
    String SECURITY_PROPERTY = "security-property";
    String SECURITY_REALMS = "security-realms";
    String SECRET_KEY = "secret-key";
    String SECRET_KEY_CREDENTIAL_STORE = "secret-key-credential-store";
    String SECRET_VALUE = "secret-value";
    String SELECTION_CRITERIA = "selection-criteria";
    String SEED = "seed";
    String SEED_FROM = "seed-from";
    String SEGMENT = "segment";
    String SERVER_NAME = "server-name";
    String SERIAL_NUMBER_FROM = "serial-number-from";
    String SERVER = "server";
    String SERVER_ADDRESS = "server-address";
    String SERVER_AUTH_MODULE = "server-auth-module";
    String SERVER_AUTH_MODULES = "server-auth-modules";
    String SET_PASSWORD = "set-password";
    String SET_SECRET = "set-secret";
    String SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY = "service-loader-http-server-mechanism-factory";
    String SERVICE_LOADER_SASL_SERVER_FACTORY = "service-loader-sasl-server-factory";
    String SEQUENCE = "sequence";
    String SEQUENCE_FROM = "sequence-from";
    String SIMPLE_DIGEST = "simple-digest";
    String SIMPLE_DIGEST_MAPPER = "simple-digest-mapper";
    String SIMPLE_PERMISSION_MAPPER = "simple-permission-mapper";
    String SIMPLE_REGEX_REALM_MAPPER = "simple-regex-realm-mapper";
    String SIMPLE_ROLE_DECODER = "simple-role-decoder";
    String SIZE_ROTATING_FILE_AUDIT_LOG = "size-rotating-file-audit-log";
    String SOURCE_ADDRESS_ROLE_DECODER = "source-address-role-decoder";
    String SQL = "sql";
    String SSL_CONTEXT_REGISTRATION = "ssl-context-registration";
    String SSL_V2_HELLO = "SSLv2Hello";
    String SOURCE_ADDRESS = "source-address";
    String START_SEGMENT = "start-segment";
    String SUBJECT_DN_FROM = "subject-dn-from";
    String SUFFICIENT = "sufficient";
    String SUFFIX = "suffix";
    String SYSLOG_AUDIT_LOG = "syslog-audit-log";
    String SYSLOG_FORMAT = "syslog-format";

    String TARGET_NAME = "target-name";
    String TO = "to";
    String TOKEN_REALM = "token-realm";
    String TRANSPORT = "transport";
    String TRUSTED_SECURITY_DOMAINS = "trusted-security-domains";
    String TRUSTED_VIRTUAL_SECURITY_DOMAINS = "trusted-virtual-security-domains";

    String RELOAD = "reload";
    String UNLESS = "unless";
    String UPPER_CASE = "upper-case";
    String USE_RECURSIVE_SEARCH = "use-recursive-search";
    String USERS_PROPERTIES = "users-properties";
    String USER_PASSWORD_MAPPER = "user-password-mapper";
    String UTF_8 = "UTF-8";

    String VERIFIABLE = "verifiable";
    String VERIFY_INTEGRITY = "verify-integrity";
    String VERSION_COMPARISON = "version-comparison";
    String VIRTUAL_SECURITY_DOMAIN = "virtual-security-domain";
    String VIRTUAL_SECURITY_DOMAIN_CREATION = "virtual-security-domain-creation";

    String WRAP_GSS_CREDENTIAL = "wrap-gss-credential";
    String WRITABLE = "writable";
    String WEBSERVICES = "webservices";
    String WS_SECURITY_TYPE = "ws-security-type";

    String X500_ATTRIBUTE_PRINCIPAL_DECODER = "x500-attribute-principal-decoder";
    String X500_SUBJECT_EVIDENCE_DECODER = "x500-subject-evidence-decoder";
    String X509_CREDENTIAL_MAPPER = "x509-credential-mapper";
    String X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER = "x509-subject-alt-name-evidence-decoder";
    String XOR = "xor";

}
