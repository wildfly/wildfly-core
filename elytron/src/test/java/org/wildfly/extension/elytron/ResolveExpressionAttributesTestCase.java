/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that attributes that allow expressions resolve them correctly.
 *
 * @author <a href="mailto:szaldana@redhat.com">Sonia Zaldana</a>
 */
public class ResolveExpressionAttributesTestCase extends AbstractElytronSubsystemBaseTest {

    private ModelNode serverModel;

    public ResolveExpressionAttributesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void init() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml());
        KernelServices kernelServices = builder.build();
        Assert.assertTrue("Subsystem boot failed!", kernelServices.isSuccessfulBoot());
        ModelNode rootModel = kernelServices.readWholeModel();
        serverModel =  rootModel.require(ModelDescriptionConstants.SUBSYSTEM).require(ElytronExtension.SUBSYSTEM_NAME);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("elytron-expressions.xml");
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        // Our use of the expression=encryption resource requires kernel capability setup that TestEnvironment provides
        return new TestEnvironment(RunningMode.ADMIN_ONLY);
    }

    @Test
    public void testExpressionAttributesResolved() {
        testAggregateRealm();
        testCertificateAuthorityAccount();
        testCertificateAuthority();
        testCredentialStore();
        testCustomComponent();
        testElytronDefinition();
        testEvidenceDecoder();
        testFailoverRealm();
        testFileSystemRealm();
        testFilteringKeyStoreDefinition();
        testIdentityRealm();
        testJaspiConfiguration();
        testJdbcRealm();
        testKerberosSecurityFactory();
        testKeyStore();
        testLdapKeyStore();
        testLdapRealm();
        testPermissionMappers();
        testPrincipalDecoders();
        testPrincipalTransformers();
        testPropertiesRealm();
        testProvider();
        testRealmMappers();
        testRoleDecoders();
        testRoleMappers();
        testSaslServer();
        testSSLComponents();
        testTokenRealm();
    }

    private void testAggregateRealm() {
        ModelNode aggRealm = serverModel.get(ElytronCommonConstants.AGGREGATE_REALM).get("AggregateOne");
        assertEquals("NameRewriterXY", getValue(aggRealm, ElytronCommonConstants.PRINCIPAL_TRANSFORMER));
    }

    private void testCertificateAuthorityAccount() {
        ModelNode caAccount = serverModel.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT).get("MyCA");
        assertEquals(Arrays.asList("https://www.test.com"), getValue(caAccount, ElytronCommonConstants.CONTACT_URLS, true));
        assertEquals("LetsEncrypt", getValue(caAccount, ElytronCommonConstants.CERTIFICATE_AUTHORITY));
        assertEquals("server", getValue(caAccount, ElytronCommonConstants.ALIAS));
    }

    private void testCertificateAuthority() {
        ModelNode ca = serverModel.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).get("testCA");
        assertEquals("https://www.test.com", getValue(ca, ElytronCommonConstants.STAGING_URL));
        assertEquals("https://www.test.com", getValue(ca, ElytronCommonConstants.URL));
    }

    private void testCredentialStore() {
        // Credential Stores
        ModelNode cs = serverModel.get(ElytronCommonConstants.CREDENTIAL_STORE).get("test1");
        assertEquals("test1.store", getValue(cs, ElytronCommonConstants.LOCATION));
        assertEquals("JCEKS", getValue(cs, ElytronCommonConstants.TYPE));
        assertEquals("provider", getValue(cs, ElytronCommonConstants.PROVIDER_NAME));
        cs = cs.get(ElytronCommonConstants.IMPLEMENTATION_PROPERTIES);
        assertEquals("JCEKS", getValue(cs, "keyStoreType"));
        cs = serverModel.get(ElytronCommonConstants.CREDENTIAL_STORE).get("test4");
        assertEquals("test1.store", getValue(cs, ElytronCommonConstants.PATH));

        // Secret Credential Store
        cs = serverModel.get(ElytronCommonConstants.SECRET_KEY_CREDENTIAL_STORE).get("test3");
        assertEquals("false", getValue(cs, ElytronCommonConstants.CREATE));
        assertEquals("false", getValue(cs, ElytronCommonConstants.POPULATE));
        assertEquals("192", getValue(cs, ElytronCommonConstants.KEY_SIZE));
        assertEquals("test3", getValue(cs, ElytronCommonConstants.DEFAULT_ALIAS));
    }

    private void testCustomComponent() {
        // Using custom permission mapper as example
        ModelNode mapper = serverModel.get(ElytronCommonConstants.CUSTOM_PERMISSION_MAPPER).get("MyPermissionMapper");
        assertEquals("value", getValue(mapper.get(ElytronCommonConstants.CONFIGURATION), "test"));
    }

    private void testElytronDefinition() {
        assertEquals(Arrays.asList("test"), getValue(serverModel, ElytronCommonConstants.DISALLOWED_PROVIDERS, true));
        assertEquals("false", getValue(serverModel, ElytronCommonConstants.REGISTER_JASPI_FACTORY));

    }

    private void testEvidenceDecoder() {
        ModelNode decoder = serverModel.get(ElytronCommonConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER).get("rfc822Decoder");
        assertEquals("rfc822Name", getValue(decoder, ElytronCommonConstants.ALT_NAME_TYPE));
        assertEquals("1", getValue(decoder, ElytronCommonConstants.SEGMENT));
    }

    private void testFailoverRealm() {
        ModelNode failoverRealm = serverModel.get(ElytronCommonConstants.FAILOVER_REALM).get("FailoverRealm");
        assertEquals("true", getValue(failoverRealm, ElytronCommonConstants.EMIT_EVENTS));
    }

    private void testFileSystemRealm()  {
        ModelNode fileRealm = serverModel.get(ElytronCommonConstants.FILESYSTEM_REALM).get("FileRealm");
        assertEquals("2", getValue(fileRealm, ElytronCommonConstants.LEVELS));
        assertEquals("false", getValue(fileRealm, ElytronCommonConstants.ENCODED));
    }

    private void testFilteringKeyStoreDefinition() {
        ModelNode keystore = serverModel.get(ElytronCommonConstants.FILTERING_KEY_STORE).get("FilteringKeyStore");
        assertEquals("NONE:+firefly", getValue(keystore, ElytronCommonConstants.ALIAS_FILTER));
    }

    private void testIdentityRealm() {
        ModelNode realm = serverModel.get(ElytronCommonConstants.IDENTITY_REALM).get("local");
        assertEquals("$local", getValue(realm, ElytronCommonConstants.IDENTITY));
        assertEquals("groups", getValue(realm, ElytronCommonConstants.ATTRIBUTE_NAME));
        assertEquals(Arrays.asList("SuperUser"), getValue(realm, ElytronCommonConstants.ATTRIBUTE_VALUES, true));
    }

    private void testJaspiConfiguration() {
        ModelNode jaspi = serverModel.get(ElytronCommonConstants.JASPI_CONFIGURATION).get("test");
        assertEquals("HttpServlet", getValue(jaspi, ElytronCommonConstants.LAYER));
        assertEquals("default /test", getValue(jaspi, ElytronCommonConstants.APPLICATION_CONTEXT));
        assertEquals("Test Definition", getValue(jaspi, ElytronCommonConstants.DESCRIPTION));

        ModelNode testModule = jaspi.get(ElytronCommonConstants.SERVER_AUTH_MODULES).get(0);
        assertEquals("REQUISITE", getValue(testModule, ElytronCommonConstants.FLAG));

        ModelNode options = testModule.get(ElytronCommonConstants.OPTIONS);
        assertEquals("b", getValue(options, "a"));
    }

    private void testJdbcRealm() {
        ModelNode jdbcRealm = serverModel.get(ElytronCommonConstants.JDBC_REALM).get("JdbcRealm").get(ElytronCommonConstants.PRINCIPAL_QUERY).get(0);

        // Bcrypt password mapper
        ModelNode mapper = jdbcRealm.get(ElytronCommonConstants.BCRYPT_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));
        assertEquals("3", getValue(mapper, ElytronCommonConstants.SALT_INDEX));
        assertEquals("4", getValue(mapper, ElytronCommonConstants.ITERATION_COUNT_INDEX));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.HASH_ENCODING));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.SALT_ENCODING));

        // Clear password mapper
        mapper = jdbcRealm.get(ElytronCommonConstants.CLEAR_PASSWORD_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));

        // Simple digest password mapper
        mapper = jdbcRealm.get(ElytronCommonConstants.SIMPLE_DIGEST_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.HASH_ENCODING));

        // Salted simple digest password mapper
        mapper = jdbcRealm.get(ElytronCommonConstants.SALTED_SIMPLE_DIGEST_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));
        assertEquals("3", getValue(mapper, ElytronCommonConstants.SALT_INDEX));
        assertEquals("password-salt-digest-sha-1", getValue(mapper, ElytronCommonConstants.ALGORITHM));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.HASH_ENCODING));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.SALT_ENCODING));

        // Scram password mapper
        mapper = jdbcRealm.get(ElytronCommonConstants.SCRAM_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));
        assertEquals("3", getValue(mapper, ElytronCommonConstants.SALT_INDEX));
        assertEquals("4", getValue(mapper, ElytronCommonConstants.ITERATION_COUNT_INDEX));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.HASH_ENCODING));
        assertEquals("hex", getValue(mapper, ElytronCommonConstants.SALT_ENCODING));

        // Modular crypt mapper
        mapper = jdbcRealm.get(ElytronCommonConstants.MODULAR_CRYPT_MAPPER);
        assertEquals("2", getValue(mapper, ElytronCommonConstants.PASSWORD_INDEX));
    }

    private void testKerberosSecurityFactory() {
        ModelNode kerberos = serverModel.get(ElytronCommonConstants.KERBEROS_SECURITY_FACTORY).get("KerberosFactory");
        assertEquals("bob@Elytron.org", getValue(kerberos, ElytronCommonConstants.PRINCIPAL));
        assertEquals("10", getValue(kerberos, ElytronCommonConstants.MINIMUM_REMAINING_LIFETIME));
        assertEquals("120", getValue(kerberos, ElytronCommonConstants.REQUEST_LIFETIME));
        assertEquals("100", getValue(kerberos, ElytronCommonConstants.FAIL_CACHE));
        assertEquals("false", getValue(kerberos, ElytronCommonConstants.SERVER));
        assertEquals("true", getValue(kerberos, ElytronCommonConstants.OBTAIN_KERBEROS_TICKET));
        assertEquals("true", getValue(kerberos, ElytronCommonConstants.DEBUG));
        assertEquals("true", getValue(kerberos, ElytronCommonConstants.WRAP_GSS_CREDENTIAL));
        assertEquals("true", getValue(kerberos, ElytronCommonConstants.REQUIRED));
        assertEquals(Arrays.asList("KRB5", "KRB5LEGACY"), getValue(kerberos, ElytronCommonConstants.MECHANISM_NAMES, true));
        assertEquals(Arrays.asList("1.2.840.113554.1.2.2", "1.3.6.1.5.5.2"), getValue(kerberos, ElytronCommonConstants.MECHANISM_OIDS, true));
    }

    private void testKeyStore() {
        ModelNode keystore = serverModel.get(ElytronCommonConstants.KEY_STORE).get("jks_store");
        assertEquals("jks", getValue(keystore, ElytronCommonConstants.TYPE));
        assertEquals("SunJSSE", getValue(keystore, ElytronCommonConstants.PROVIDER_NAME));
        assertEquals("one,two,three", getValue(keystore, ElytronCommonConstants.ALIAS_FILTER));
        assertEquals("true", getValue(keystore, ElytronCommonConstants.REQUIRED));
    }

    private void testLdapKeyStore() {
        ModelNode keystore = serverModel.get(ElytronCommonConstants.LDAP_KEY_STORE).get("LdapKeyStore");

        // search
        assertEquals("dc=elytron,dc=wildfly,dc=org", getValue(keystore, ElytronCommonConstants.SEARCH_PATH));
        assertEquals("true", getValue(keystore, ElytronCommonConstants.SEARCH_RECURSIVE));
        assertEquals("1000", getValue(keystore, ElytronCommonConstants.SEARCH_TIME_LIMIT));
        assertEquals("(&(objectClass=inetOrgPerson)(sn={0}))", getValue(keystore, ElytronCommonConstants.FILTER_ALIAS));
        assertEquals("(&(objectClass=inetOrgPerson)(usercertificate={0}))", getValue(keystore, ElytronCommonConstants.FILTER_CERTIFICATE));
        assertEquals("(sn=serenity*)", getValue(keystore, ElytronCommonConstants.FILTER_ITERATE));

        // attribute mapping
        assertEquals("sn", getValue(keystore, ElytronCommonConstants.ALIAS_ATTRIBUTE));
        assertEquals("usercertificate", getValue(keystore, ElytronCommonConstants.CERTIFICATE_ATTRIBUTE));
        assertEquals("X.509", getValue(keystore, ElytronCommonConstants.CERTIFICATE_TYPE));
        assertEquals("userSMIMECertificate", getValue(keystore, ElytronCommonConstants.CERTIFICATE_CHAIN_ATTRIBUTE));
        assertEquals("PKCS7", getValue(keystore, ElytronCommonConstants.CERTIFICATE_CHAIN_ENCODING));
        assertEquals("userPKCS12", getValue(keystore, ElytronCommonConstants.KEY_ATTRIBUTE));
        assertEquals("PKCS12", getValue(keystore, ElytronCommonConstants.KEY_TYPE));

        // new item template
        ModelNode template = keystore.get(ElytronCommonConstants.NEW_ITEM_TEMPLATE);
        assertEquals("ou=keystore,dc=elytron,dc=wildfly,dc=org", getValue(template, ElytronCommonConstants.NEW_ITEM_PATH));
        assertEquals("cn", getValue(template, ElytronCommonConstants.NEW_ITEM_RDN));
        assertEquals("objectClass", getValue(template.get(ElytronCommonConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronCommonConstants.NAME));
        assertEquals(Arrays.asList("top", "inetOrgPerson"), getValue(template.get(ElytronCommonConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronCommonConstants.VALUE, true));
    }

    private void testLdapRealm() {
        ModelNode realm = serverModel.get(ElytronCommonConstants.LDAP_REALM).get("LdapRealmWithAttributeMapping");
        assertEquals("false", getValue(realm, ElytronCommonConstants.DIRECT_VERIFICATION));
        assertEquals("true", getValue(realm, ElytronCommonConstants.ALLOW_BLANK_PASSWORD));

        // Identity mapping
        ModelNode identityMapping = realm.get(ElytronCommonConstants.IDENTITY_MAPPING);
        assertEquals("uid", getValue(identityMapping, ElytronCommonConstants.RDN_IDENTIFIER));
        assertEquals("true", getValue(identityMapping, ElytronCommonConstants.USE_RECURSIVE_SEARCH));
        assertEquals("dc=elytron,dc=wildfly,dc=org", getValue(identityMapping, ElytronCommonConstants.SEARCH_BASE_DN));
        assertEquals("(rdn_identifier={0})", getValue(identityMapping, ElytronCommonConstants.FILTER_NAME));
        assertEquals("(uid=*)", getValue(identityMapping, ElytronCommonConstants.ITERATOR_FILTER));
        assertEquals("dc=elytron,dc=wildfly,dc=org", getValue(identityMapping, ElytronCommonConstants.NEW_IDENTITY_PARENT_DN));

        // Attribute mapping
        ModelNode attributeMapping = identityMapping.get(ElytronCommonConstants.ATTRIBUTE_MAPPING);
        assertEquals("CN", getValue(attributeMapping.get(0), ElytronCommonConstants.FROM));
        assertEquals("businessUnit", getValue(attributeMapping.get(0), ElytronCommonConstants.TO));
        assertEquals("ref", getValue(attributeMapping.get(0), ElytronCommonConstants.REFERENCE));
        assertEquals("(&(objectClass=groupOfNames)(member={0}))", getValue(attributeMapping.get(0), ElytronCommonConstants.FILTER));
        assertEquals("ou=Finance,dc=elytron,dc=wildfly,dc=org", getValue(attributeMapping.get(0), ElytronCommonConstants.FILTER_BASE_DN));
        assertEquals("true", getValue(attributeMapping.get(0), ElytronCommonConstants.SEARCH_RECURSIVE));
        assertEquals("0", getValue(attributeMapping.get(0), ElytronCommonConstants.ROLE_RECURSION));
        assertEquals("cn", getValue(attributeMapping.get(0), ElytronCommonConstants.ROLE_RECURSION_NAME));
        assertEquals("OU", getValue(attributeMapping.get(1), ElytronCommonConstants.EXTRACT_RDN));

        // User password credential mapping
        ModelNode userPass = identityMapping.get(ElytronCommonConstants.USER_PASSWORD_MAPPER);
        assertEquals("userPassword", getValue(userPass, ElytronCommonConstants.FROM));
        assertEquals("true", getValue(userPass, ElytronCommonConstants.WRITABLE));
        assertEquals("true", getValue(userPass, ElytronCommonConstants.VERIFIABLE));

        // Otp credential mapping
        ModelNode otp = identityMapping.get(ElytronCommonConstants.OTP_CREDENTIAL_MAPPER);
        assertEquals("otpAlgorithm", getValue(otp, ElytronCommonConstants.ALGORITHM_FROM));
        assertEquals("otpHash", getValue(otp, ElytronCommonConstants.HASH_FROM));
        assertEquals("otpSeed", getValue(otp, ElytronCommonConstants.SEED_FROM));
        assertEquals("otpSequence", getValue(otp, ElytronCommonConstants.SEQUENCE_FROM));

        // X509 Credential mapping
        ModelNode x509 = identityMapping.get(ElytronCommonConstants.X509_CREDENTIAL_MAPPER);
        assertEquals("x509digest", getValue(x509, ElytronCommonConstants.DIGEST_FROM));
        assertEquals("usercertificate", getValue(x509, ElytronCommonConstants.CERTIFICATE_FROM));
        assertEquals("SHA-1", getValue(x509, ElytronCommonConstants.DIGEST_ALGORITHM));
        assertEquals("x509serialNumber", getValue(x509, ElytronCommonConstants.SERIAL_NUMBER_FROM));
        assertEquals("x509subject", getValue(x509, ElytronCommonConstants.SUBJECT_DN_FROM));

        // New identity attribute
        ModelNode newIdentity = identityMapping.get(ElytronCommonConstants.NEW_IDENTITY_ATTRIBUTES).get(0);
        assertEquals("sn", getValue(newIdentity, ElytronCommonConstants.NAME));
        assertEquals(Arrays.asList("BlankSurname"), getValue(newIdentity, ElytronCommonConstants.VALUE, true));
    }

    private void testPermissionMappers() {
        ModelNode mapper = serverModel.get(ElytronCommonConstants.SIMPLE_PERMISSION_MAPPER).get("SimplePermissionMapperLegacy");
        assertEquals("and", getValue(mapper, ElytronCommonConstants.MAPPING_MODE));

        mapper = mapper.get(ElytronCommonConstants.PERMISSION_MAPPINGS).get(0);
        assertEquals(Arrays.asList("John", "Joe"), getValue(mapper, ElytronCommonConstants.PRINCIPALS, true));
        assertEquals(Arrays.asList("User", "Administrator"), getValue(mapper, ElytronCommonConstants.ROLES, true));

        mapper = mapper.get(ElytronCommonConstants.PERMISSIONS).get(1);
        assertEquals("../c", getValue(mapper, ElytronCommonConstants.TARGET_NAME));
        assertEquals("delete", getValue(mapper, ElytronCommonConstants.ACTION));
    }

    private void testPrincipalDecoders() {
        ModelNode decoder = serverModel.get(ElytronCommonConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyX500PrincipalDecoderTwo");
        assertEquals("2.5.4.3", getValue(decoder, ElytronCommonConstants.OID));
        assertEquals(",", getValue(decoder, ElytronCommonConstants.JOINER));
        assertEquals("2", getValue(decoder, ElytronCommonConstants.START_SEGMENT));
        assertEquals("1", getValue(decoder, ElytronCommonConstants.MAXIMUM_SEGMENTS));
        assertEquals("true", getValue(decoder, ElytronCommonConstants.REVERSE));
        assertEquals("true", getValue(decoder, ElytronCommonConstants.CONVERT));
        assertEquals(Arrays.asList("2.5.4.3", "2.5.4.11"), getValue(decoder, ElytronCommonConstants.REQUIRED_OIDS, true));

        decoder = serverModel.get(ElytronCommonConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyCnDecoder");
        assertEquals("Cn", getValue(decoder, ElytronCommonConstants.ATTRIBUTE_NAME));

        decoder = serverModel.get(ElytronCommonConstants.CONSTANT_PRINCIPAL_DECODER).get("ConstantDecoder");
        assertEquals("test", getValue(decoder, ElytronCommonConstants.CONSTANT));
    }

    private void testPrincipalTransformers() {
        ModelNode pt = serverModel.get(ElytronCommonConstants.REGEX_PRINCIPAL_TRANSFORMER).get("NameRewriterXY");
        assertEquals("y$1", getValue(pt, ElytronCommonConstants.REPLACEMENT));
        assertEquals("x(.*)", getValue(pt, ElytronCommonConstants.PATTERN));
        assertEquals("false", getValue(pt, ElytronCommonConstants.REPLACE_ALL));

        pt = serverModel.get(ElytronCommonConstants.CONSTANT_PRINCIPAL_TRANSFORMER).get("ConstantNameRewriter");
        assertEquals("test", getValue(pt, ElytronCommonConstants.CONSTANT));

        pt = serverModel.get(ElytronCommonConstants.CASE_PRINCIPAL_TRANSFORMER).get("CaseNameRewriter");
        assertEquals("true", getValue(pt, ElytronCommonConstants.UPPER_CASE));

        pt = serverModel.get(ElytronCommonConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER).get("RegexValidateNameRewriter");
        assertEquals("true", getValue(pt, ElytronCommonConstants.MATCH));
    }

    private void testPropertiesRealm() {
        ModelNode propRealm = serverModel.get(ElytronCommonConstants.PROPERTIES_REALM).get("PropRealm");
        assertEquals("FileRealm", getValue(propRealm.get(ElytronCommonConstants.USERS_PROPERTIES), ElytronCommonConstants.DIGEST_REALM_NAME));
        assertEquals("false", getValue(propRealm.get(ElytronCommonConstants.USERS_PROPERTIES), ElytronCommonConstants.PLAIN_TEXT));
        assertEquals("groups", getValue(propRealm, ElytronCommonConstants.GROUPS_ATTRIBUTE));
    }

    private void testProvider() {
        ModelNode provider = serverModel.get(ElytronCommonConstants.PROVIDER_LOADER).get("openssl");
        assertEquals("val", getValue(provider.get(ElytronCommonConstants.CONFIGURATION), "prop"));
        provider = serverModel.get(ElytronCommonConstants.PROVIDER_LOADER).get("elytron");
        assertEquals("arg", getValue(provider, ElytronCommonConstants.ARGUMENT));
    }

    private void testRealmMappers() {
        ModelNode realmMapper = serverModel.get(ElytronCommonConstants.MAPPED_REGEX_REALM_MAPPER).get("MappedRealmMapper");
        assertEquals(".*@(.*)", getValue(realmMapper, ElytronCommonConstants.PATTERN));
        assertEquals("test", getValue(realmMapper.get(ElytronCommonConstants.REALM_MAP), "test"));
    }

    private void testRoleDecoders() {
        ModelNode roleDecoder = serverModel.get(ElytronCommonConstants.SOURCE_ADDRESS_ROLE_DECODER).get("ipRoleDecoder");
        assertEquals(Arrays.asList("admin", "user"), getValue(roleDecoder, ElytronCommonConstants.ROLES, true));
        assertEquals("10.12.14.16", getValue(roleDecoder, ElytronCommonConstants.SOURCE_ADDRESS));
        roleDecoder = serverModel.get(ElytronCommonConstants.SOURCE_ADDRESS_ROLE_DECODER).get("regexRoleDecoder");
        assertEquals("10\\.12\\.14\\.\\d+$", getValue(roleDecoder, ElytronCommonConstants.PATTERN));
    }

    private void testRoleMappers() {
        ModelNode roleMapper = serverModel.get(ElytronCommonConstants.ADD_PREFIX_ROLE_MAPPER).get("RolePrefixer");
        assertEquals("prefix", getValue(roleMapper, ElytronCommonConstants.PREFIX));
        roleMapper = serverModel.get(ElytronCommonConstants.ADD_SUFFIX_ROLE_MAPPER).get("RoleSuffixer");
        assertEquals("suffix", getValue(roleMapper, ElytronCommonConstants.SUFFIX));

        roleMapper = serverModel.get(ElytronCommonConstants.MAPPED_ROLE_MAPPER).get("MappedRoleMapper");
        assertEquals("false", getValue(roleMapper, ElytronCommonConstants.KEEP_MAPPED));
        assertEquals("true", getValue(roleMapper, ElytronCommonConstants.KEEP_NON_MAPPED));

        roleMapper = serverModel.get(ElytronCommonConstants.REGEX_ROLE_MAPPER).get("RegexRoleMapper");
        assertEquals("false", getValue(roleMapper, ElytronCommonConstants.REPLACEMENT));
        assertEquals("false", getValue(roleMapper, ElytronCommonConstants.REPLACE_ALL));
        assertEquals("*(x.)", getValue(roleMapper, ElytronCommonConstants.PATTERN));

        roleMapper = serverModel.get(ElytronCommonConstants.CONSTANT_ROLE_MAPPER).get("ConstantRoleMapper");
        assertEquals(Arrays.asList("role"), getValue(roleMapper, ElytronCommonConstants.ROLES, true));

        roleMapper = serverModel.get(ElytronCommonConstants.LOGICAL_ROLE_MAPPER).get("LogicalRoleMapper");
        assertEquals("and", getValue(roleMapper, ElytronCommonConstants.LOGICAL_OPERATION));
    }

    private void testSaslServer() {
        ModelNode factory = serverModel.get(ElytronCommonConstants.SASL_AUTHENTICATION_FACTORY).get("SaslAuthenticationDefinition").get(ElytronCommonConstants.MECHANISM_CONFIGURATIONS).get(0);
        assertEquals("PLAIN", getValue(factory, ElytronCommonConstants.MECHANISM_NAME));
        assertEquals("host", getValue(factory, ElytronCommonConstants.HOST_NAME));
        assertEquals("protocol", getValue(factory, ElytronCommonConstants.PROTOCOL));
        assertEquals("Test Realm", getValue(factory.get(ElytronCommonConstants.MECHANISM_REALM_CONFIGURATIONS).get(0), ElytronCommonConstants.REALM_NAME));
    }

    private void testSSLComponents() {
        // SSL Context
        ModelNode context = serverModel.get(ElytronCommonConstants.SERVER_SSL_CONTEXT).get("server");
        assertEquals(Arrays.asList("TLSv1.2"), getValue(context, ElytronCommonConstants.PROTOCOLS, true));
        assertEquals("true", getValue(context, ElytronCommonConstants.WANT_CLIENT_AUTH));
        assertEquals("true", getValue(context, ElytronCommonConstants.NEED_CLIENT_AUTH));
        assertEquals("true", getValue(context, ElytronCommonConstants.AUTHENTICATION_OPTIONAL));
        assertEquals("false", getValue(context, ElytronCommonConstants.USE_CIPHER_SUITES_ORDER));
        assertEquals("false", getValue(context, ElytronCommonConstants.WRAP));
        assertEquals("first", getValue(context, ElytronCommonConstants.PROVIDER_NAME));
        assertEquals("DEFAULT", getValue(context, ElytronCommonConstants.CIPHER_SUITE_FILTER));
        assertEquals("name", getValue(context, ElytronCommonConstants.CIPHER_SUITE_NAMES));
        assertEquals("10", getValue(context, ElytronCommonConstants.MAXIMUM_SESSION_CACHE_SIZE));
        assertEquals("120", getValue(context, ElytronCommonConstants.SESSION_TIMEOUT));

        // Trust Managers
        ModelNode tm = serverModel.get(ElytronCommonConstants.TRUST_MANAGER).get("trust-with-ocsp").get(ElytronCommonConstants.OCSP);
        assertEquals("http://localhost/ocsp", getValue(tm, ElytronCommonConstants.RESPONDER));
        assertEquals("jceks_store", getValue(tm, ElytronCommonConstants.RESPONDER_KEYSTORE));
        assertEquals("responder-alias", getValue(tm, ElytronCommonConstants.RESPONDER_CERTIFICATE));

        tm = serverModel.get(ElytronCommonConstants.TRUST_MANAGER).get("trust-with-crl").get(ElytronCommonConstants.CERTIFICATE_REVOCATION_LIST);
        assertEquals("crl.pem", getValue(tm, ElytronCommonConstants.PATH));
        assertEquals("2", getValue(tm, ElytronCommonConstants.MAXIMUM_CERT_PATH));

        // Key Managers
        ModelNode keyManager = serverModel.get(ElytronCommonConstants.KEY_MANAGER).get("serverKey2");
        assertEquals("SunX509", getValue(keyManager, ElytronCommonConstants.ALGORITHM));
        assertEquals("one,two,three", getValue(keyManager, ElytronCommonConstants.ALIAS_FILTER));
        assertEquals("localhost", getValue(keyManager, ElytronCommonConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST));
    }

    private void testTokenRealm() {
        ModelNode realm = serverModel.get(ElytronCommonConstants.TOKEN_REALM).get("JwtRealmOne");
        assertEquals("sub", getValue(realm, ElytronCommonConstants.PRINCIPAL_CLAIM));

        ModelNode jwt = realm.get(ElytronCommonConstants.JWT);
        assertEquals(Arrays.asList("some-issuer-a"), getValue(jwt, ElytronCommonConstants.ISSUER, true));
        assertEquals(Arrays.asList("some-audience-a"), getValue(jwt, ElytronCommonConstants.AUDIENCE, true));
        assertEquals("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrVrCuTtArbgaZzL1hvh0xtL5mc7o0NqP", getValue(jwt, ElytronCommonConstants.PUBLIC_KEY));
        assertEquals("cert", getValue(jwt, ElytronCommonConstants.CERTIFICATE));
        assertEquals("public", getValue(jwt.get(ElytronCommonConstants.KEY_MAP), ElytronCommonConstants.KID));

        // OAuth
        ModelNode oauth = serverModel.get(ElytronCommonConstants.TOKEN_REALM).get("OAuth2Realm").get(ElytronCommonConstants.OAUTH2_INTROSPECTION);
        assertEquals("host", getValue(oauth, ElytronCommonConstants.HOST_NAME_VERIFICATION_POLICY));
        assertEquals("a", getValue(oauth, ElytronCommonConstants.CLIENT_ID));
        assertEquals("b", getValue(oauth, ElytronCommonConstants.CLIENT_SECRET));
        assertEquals("https://localhost/token/introspect", getValue(oauth, ElytronCommonConstants.INTROSPECTION_URL));
    }

    private Object getValue(ModelNode node, String attributeName) {
        return getValue(node, attributeName, false);
    }

    private Object getValue(ModelNode node, String attributeName, boolean isList) {
        ModelNode result = node.get(attributeName).resolve();
        if (! isList) {
            return result.asString();
        }
        List<String> results = new ArrayList<>();
        for (ModelNode n : result.asList()) {
            results.add(n.asString());
        }
        return results;
    }
}
