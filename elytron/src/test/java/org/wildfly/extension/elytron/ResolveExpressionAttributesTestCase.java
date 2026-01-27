/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;


import static org.jboss.as.controller.client.helpers.ClientConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOLVE_EXPRESSIONS;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
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

    private static final String ELYTRON = "elytron";

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

        ModelNode addr = Operations.createAddress(SUBSYSTEM, ELYTRON);
        ModelNode operation = Operations.createReadResourceOperation(addr, true);
        operation.get(RESOLVE_EXPRESSIONS).set(true);
        serverModel = kernelServices.executeOperation(operation).get(ClientConstants.RESULT);
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
        ModelNode aggRealm = serverModel.get(ElytronDescriptionConstants.AGGREGATE_REALM).get("AggregateOne");
        assertEquals("NameRewriterXY", getResolvedValue(aggRealm, ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER));
    }

    private void testCertificateAuthorityAccount() {
        ModelNode caAccount = serverModel.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).get("MyCA");
        assertEquals(Arrays.asList("https://www.test.com"), getResolvedValue(caAccount, ElytronDescriptionConstants.CONTACT_URLS, true));
        assertEquals("LetsEncrypt", getResolvedValue(caAccount, ElytronDescriptionConstants.CERTIFICATE_AUTHORITY));
        assertEquals("server", getResolvedValue(caAccount, ElytronDescriptionConstants.ALIAS));
    }

    private void testCertificateAuthority() {
        ModelNode ca = serverModel.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).get("testCA");
        assertEquals("https://www.test.com", getResolvedValue(ca, ElytronDescriptionConstants.STAGING_URL));
        assertEquals("https://www.test.com", getResolvedValue(ca, ElytronDescriptionConstants.URL));
    }

    private void testCredentialStore() {
        // Credential Stores
        ModelNode cs = serverModel.get(ElytronDescriptionConstants.CREDENTIAL_STORE).get("test1");
        assertEquals("test1.store", getResolvedValue(cs, ElytronDescriptionConstants.LOCATION));
        assertEquals("JCEKS", getResolvedValue(cs, ElytronDescriptionConstants.TYPE));
        assertEquals("provider", getResolvedValue(cs, ElytronDescriptionConstants.PROVIDER_NAME));
        cs = cs.get(ElytronDescriptionConstants.IMPLEMENTATION_PROPERTIES);
        assertEquals("JCEKS", getResolvedValue(cs, "keyStoreType"));
        cs = serverModel.get(ElytronDescriptionConstants.CREDENTIAL_STORE).get("test4");
        assertEquals("test1.store", getResolvedValue(cs, ElytronDescriptionConstants.PATH));

        // Secret Credential Store
        cs = serverModel.get(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE).get("test3");
        assertEquals("false", getResolvedValue(cs, ElytronDescriptionConstants.CREATE));
        assertEquals("false", getResolvedValue(cs, ElytronDescriptionConstants.POPULATE));
        assertEquals("192", getResolvedValue(cs, ElytronDescriptionConstants.KEY_SIZE));
        assertEquals("test3", getResolvedValue(cs, ElytronDescriptionConstants.DEFAULT_ALIAS));
    }

    private void testCustomComponent() {
        // Using custom permission mapper as example
        ModelNode mapper = serverModel.get(ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER).get("MyPermissionMapper");
        assertEquals("value", getResolvedValue(mapper.get(ElytronDescriptionConstants.CONFIGURATION), "test"));
    }

    private void testElytronDefinition() {
        assertEquals(Arrays.asList("test"), getResolvedValue(serverModel, ElytronDescriptionConstants.DISALLOWED_PROVIDERS, true));
        assertEquals("false", getResolvedValue(serverModel, ElytronDescriptionConstants.REGISTER_JASPI_FACTORY));

    }

    private void testEvidenceDecoder() {
        ModelNode decoder = serverModel.get(ElytronDescriptionConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER).get("rfc822Decoder");
        assertEquals("rfc822Name", getResolvedValue(decoder, ElytronDescriptionConstants.ALT_NAME_TYPE));
        assertEquals("1", getResolvedValue(decoder, ElytronDescriptionConstants.SEGMENT));
    }

    private void testFailoverRealm() {
        ModelNode failoverRealm = serverModel.get(ElytronDescriptionConstants.FAILOVER_REALM).get("FailoverRealm");
        assertEquals("true", getResolvedValue(failoverRealm, ElytronDescriptionConstants.EMIT_EVENTS));
    }

    private void testFileSystemRealm()  {
        ModelNode fileRealm = serverModel.get(ElytronDescriptionConstants.FILESYSTEM_REALM).get("FileRealm");
        assertEquals("2", getResolvedValue(fileRealm, ElytronDescriptionConstants.LEVELS));
        assertEquals("false", getResolvedValue(fileRealm, ElytronDescriptionConstants.ENCODED));
    }

    private void testFilteringKeyStoreDefinition() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.FILTERING_KEY_STORE).get("FilteringKeyStore");
        assertEquals("NONE:+firefly", getResolvedValue(keystore, ElytronDescriptionConstants.ALIAS_FILTER));
    }

    private void testIdentityRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.IDENTITY_REALM).get("local");
        assertEquals("$local", getResolvedValue(realm, ElytronDescriptionConstants.IDENTITY));
        assertEquals("groups", getResolvedValue(realm, ElytronDescriptionConstants.ATTRIBUTE_NAME));
        assertEquals(Arrays.asList("SuperUser"), getResolvedValue(realm, ElytronDescriptionConstants.ATTRIBUTE_VALUES, true));
    }

    private void testJaspiConfiguration() {
        ModelNode jaspi = serverModel.get(ElytronDescriptionConstants.JASPI_CONFIGURATION).get("test");
        assertEquals("HttpServlet", getResolvedValue(jaspi, ElytronDescriptionConstants.LAYER));
        assertEquals("default /test", getResolvedValue(jaspi, ElytronDescriptionConstants.APPLICATION_CONTEXT));
        assertEquals("Test Definition", getResolvedValue(jaspi, ElytronDescriptionConstants.DESCRIPTION));

        ModelNode testModule = jaspi.get(ElytronDescriptionConstants.SERVER_AUTH_MODULES).get(0);
        assertEquals("REQUISITE", getResolvedValue(testModule, ElytronDescriptionConstants.FLAG));

        ModelNode options = testModule.get(ElytronDescriptionConstants.OPTIONS);
        assertEquals("b", getResolvedValue(options, "a"));
    }

    private void testJdbcRealm() {

        ModelNode jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmBcrypt").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);

        // Bcrypt password mapper
        ModelNode mapper = jdbcRealm.get(ElytronDescriptionConstants.BCRYPT_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals("3", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals("4", getResolvedValue(mapper, ElytronDescriptionConstants.ITERATION_COUNT_INDEX));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Clear password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmClearPassword").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.CLEAR_PASSWORD_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));

        // Simple digest password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmSimple").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));

        // Salted simple digest password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmSalted").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals("3", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals("password-salt-digest-sha-1", getResolvedValue(mapper, ElytronDescriptionConstants.ALGORITHM));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Scram password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcScram").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SCRAM_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals("3", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals("4", getResolvedValue(mapper, ElytronDescriptionConstants.ITERATION_COUNT_INDEX));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals("hex", getResolvedValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Modular crypt mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmModular").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.MODULAR_CRYPT_MAPPER);
        assertEquals("2", getResolvedValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
    }

    private void testKerberosSecurityFactory() {
        ModelNode kerberos = serverModel.get(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY).get("KerberosFactory");
        assertEquals("bob@Elytron.org", getResolvedValue(kerberos, ElytronDescriptionConstants.PRINCIPAL));
        assertEquals("10", getResolvedValue(kerberos, ElytronDescriptionConstants.MINIMUM_REMAINING_LIFETIME));
        assertEquals("120", getResolvedValue(kerberos, ElytronDescriptionConstants.REQUEST_LIFETIME));
        assertEquals("100", getResolvedValue(kerberos, ElytronDescriptionConstants.FAIL_CACHE));
        assertEquals("false", getResolvedValue(kerberos, ElytronDescriptionConstants.SERVER));
        assertEquals("true", getResolvedValue(kerberos, ElytronDescriptionConstants.OBTAIN_KERBEROS_TICKET));
        assertEquals("true", getResolvedValue(kerberos, ElytronDescriptionConstants.DEBUG));
        assertEquals("true", getResolvedValue(kerberos, ElytronDescriptionConstants.WRAP_GSS_CREDENTIAL));
        assertEquals("true", getResolvedValue(kerberos, ElytronDescriptionConstants.REQUIRED));
        assertEquals(Arrays.asList("KRB5", "KRB5LEGACY"), getResolvedValue(kerberos, ElytronDescriptionConstants.MECHANISM_NAMES, true));
        assertEquals(Arrays.asList("1.2.840.113554.1.2.2", "1.3.6.1.5.5.2"), getResolvedValue(kerberos, ElytronDescriptionConstants.MECHANISM_OIDS, true));
    }

    private void testKeyStore() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.KEY_STORE).get("jks_store");
        assertEquals("jks", getResolvedValue(keystore, ElytronDescriptionConstants.TYPE));
        assertEquals("SunJSSE", getResolvedValue(keystore, ElytronDescriptionConstants.PROVIDER_NAME));
        assertEquals("one,two,three", getResolvedValue(keystore, ElytronDescriptionConstants.ALIAS_FILTER));
        assertEquals("true", getResolvedValue(keystore, ElytronDescriptionConstants.REQUIRED));
    }

    private void testLdapKeyStore() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.LDAP_KEY_STORE).get("LdapKeyStore");

        // search
        assertEquals("dc=elytron,dc=wildfly,dc=org", getResolvedValue(keystore, ElytronDescriptionConstants.SEARCH_PATH));
        assertEquals("true", getResolvedValue(keystore, ElytronDescriptionConstants.SEARCH_RECURSIVE));
        assertEquals("1000", getResolvedValue(keystore, ElytronDescriptionConstants.SEARCH_TIME_LIMIT));
        assertEquals("(&(objectClass=inetOrgPerson)(sn={0}))", getResolvedValue(keystore, ElytronDescriptionConstants.FILTER_ALIAS));
        assertEquals("(&(objectClass=inetOrgPerson)(usercertificate={0}))", getResolvedValue(keystore, ElytronDescriptionConstants.FILTER_CERTIFICATE));
        assertEquals("(sn=serenity*)", getResolvedValue(keystore, ElytronDescriptionConstants.FILTER_ITERATE));

        // attribute mapping
        assertEquals("sn", getResolvedValue(keystore, ElytronDescriptionConstants.ALIAS_ATTRIBUTE));
        assertEquals("usercertificate", getResolvedValue(keystore, ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE));
        assertEquals("X.509", getResolvedValue(keystore, ElytronDescriptionConstants.CERTIFICATE_TYPE));
        assertEquals("userSMIMECertificate", getResolvedValue(keystore, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE));
        assertEquals("PKCS7", getResolvedValue(keystore, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING));
        assertEquals("userPKCS12", getResolvedValue(keystore, ElytronDescriptionConstants.KEY_ATTRIBUTE));
        assertEquals("PKCS12", getResolvedValue(keystore, ElytronDescriptionConstants.KEY_TYPE));

        // new item template
        ModelNode template = keystore.get(ElytronDescriptionConstants.NEW_ITEM_TEMPLATE);
        assertEquals("ou=keystore,dc=elytron,dc=wildfly,dc=org", getResolvedValue(template, ElytronDescriptionConstants.NEW_ITEM_PATH));
        assertEquals("cn", getResolvedValue(template, ElytronDescriptionConstants.NEW_ITEM_RDN));
        assertEquals("objectClass", getResolvedValue(template.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.NAME));
        assertEquals(Arrays.asList("top", "inetOrgPerson"), getResolvedValue(template.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.VALUE, true));
    }

    private void testLdapRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.LDAP_REALM).get("LdapRealmWithAttributeMapping");
        assertEquals("false", getResolvedValue(realm, ElytronDescriptionConstants.DIRECT_VERIFICATION));
        assertEquals("true", getResolvedValue(realm, ElytronDescriptionConstants.ALLOW_BLANK_PASSWORD));

        // Identity mapping
        ModelNode identityMapping = realm.get(ElytronDescriptionConstants.IDENTITY_MAPPING);
        assertEquals("uid", getResolvedValue(identityMapping, ElytronDescriptionConstants.RDN_IDENTIFIER));
        assertEquals("true", getResolvedValue(identityMapping, ElytronDescriptionConstants.USE_RECURSIVE_SEARCH));
        assertEquals("dc=elytron,dc=wildfly,dc=org", getResolvedValue(identityMapping, ElytronDescriptionConstants.SEARCH_BASE_DN));
        assertEquals("(rdn_identifier={0})", getResolvedValue(identityMapping, ElytronDescriptionConstants.FILTER_NAME));
        assertEquals("(uid=*)", getResolvedValue(identityMapping, ElytronDescriptionConstants.ITERATOR_FILTER));
        assertEquals("dc=elytron,dc=wildfly,dc=org", getResolvedValue(identityMapping, ElytronDescriptionConstants.NEW_IDENTITY_PARENT_DN));

        // Attribute mapping
        ModelNode attributeMapping = identityMapping.get(ElytronDescriptionConstants.ATTRIBUTE_MAPPING);
        assertEquals("CN", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.FROM));
        assertEquals("businessUnit", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.TO));
        assertEquals("ref", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.REFERENCE));
        assertEquals("(&(objectClass=groupOfNames)(member={0}))", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.FILTER));
        assertEquals("ou=Finance,dc=elytron,dc=wildfly,dc=org", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.FILTER_BASE_DN));
        assertEquals("true", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.SEARCH_RECURSIVE));
        assertEquals("0", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.ROLE_RECURSION));
        assertEquals("cn", getResolvedValue(attributeMapping.get(0), ElytronDescriptionConstants.ROLE_RECURSION_NAME));
        assertEquals("OU", getResolvedValue(attributeMapping.get(1), ElytronDescriptionConstants.EXTRACT_RDN));

        // User password credential mapping
        ModelNode userPass = identityMapping.get(ElytronDescriptionConstants.USER_PASSWORD_MAPPER);
        assertEquals("userPassword", getResolvedValue(userPass, ElytronDescriptionConstants.FROM));
        assertEquals("true", getResolvedValue(userPass, ElytronDescriptionConstants.WRITABLE));
        assertEquals("true", getResolvedValue(userPass, ElytronDescriptionConstants.VERIFIABLE));

        // Otp credential mapping
        ModelNode otp = identityMapping.get(ElytronDescriptionConstants.OTP_CREDENTIAL_MAPPER);
        assertEquals("otpAlgorithm", getResolvedValue(otp, ElytronDescriptionConstants.ALGORITHM_FROM));
        assertEquals("otpHash", getResolvedValue(otp, ElytronDescriptionConstants.HASH_FROM));
        assertEquals("otpSeed", getResolvedValue(otp, ElytronDescriptionConstants.SEED_FROM));
        assertEquals("otpSequence", getResolvedValue(otp, ElytronDescriptionConstants.SEQUENCE_FROM));

        // X509 Credential mapping
        ModelNode x509 = identityMapping.get(ElytronDescriptionConstants.X509_CREDENTIAL_MAPPER);
        assertEquals("x509digest", getResolvedValue(x509, ElytronDescriptionConstants.DIGEST_FROM));
        assertEquals("usercertificate", getResolvedValue(x509, ElytronDescriptionConstants.CERTIFICATE_FROM));
        assertEquals("SHA-1", getResolvedValue(x509, ElytronDescriptionConstants.DIGEST_ALGORITHM));
        assertEquals("x509serialNumber", getResolvedValue(x509, ElytronDescriptionConstants.SERIAL_NUMBER_FROM));
        assertEquals("x509subject", getResolvedValue(x509, ElytronDescriptionConstants.SUBJECT_DN_FROM));

        // New identity attribute
        ModelNode newIdentity = identityMapping.get(ElytronDescriptionConstants.NEW_IDENTITY_ATTRIBUTES).get(0);
        assertEquals("sn", getResolvedValue(newIdentity, ElytronDescriptionConstants.NAME));
        assertEquals(Arrays.asList("BlankSurname"), getResolvedValue(newIdentity, ElytronDescriptionConstants.VALUE, true));
    }

    private void testPermissionMappers() {
        ModelNode mapper = serverModel.get(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER).get("SimplePermissionMapperLegacy");
        assertEquals("and", getResolvedValue(mapper, ElytronDescriptionConstants.MAPPING_MODE));

        mapper = mapper.get(ElytronDescriptionConstants.PERMISSION_MAPPINGS).get(0);
        assertEquals(Arrays.asList("John", "Joe"), getResolvedValue(mapper, ElytronDescriptionConstants.PRINCIPALS, true));
        assertEquals(Arrays.asList("User", "Administrator"), getResolvedValue(mapper, ElytronDescriptionConstants.ROLES, true));

        mapper = mapper.get(ElytronDescriptionConstants.PERMISSIONS).get(1);
        assertEquals("../c", getResolvedValue(mapper, ElytronDescriptionConstants.TARGET_NAME));
        assertEquals("delete", getResolvedValue(mapper, ElytronDescriptionConstants.ACTION));
    }

    private void testPrincipalDecoders() {
        ModelNode decoder = serverModel.get(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyX500PrincipalDecoderTwo");
        assertEquals("2.5.4.3", getResolvedValue(decoder, ElytronDescriptionConstants.OID));
        assertEquals(",", getResolvedValue(decoder, ElytronDescriptionConstants.JOINER));
        assertEquals("2", getResolvedValue(decoder, ElytronDescriptionConstants.START_SEGMENT));
        assertEquals("1", getResolvedValue(decoder, ElytronDescriptionConstants.MAXIMUM_SEGMENTS));
        assertEquals("true", getResolvedValue(decoder, ElytronDescriptionConstants.REVERSE));
        assertEquals("true", getResolvedValue(decoder, ElytronDescriptionConstants.CONVERT));
        assertEquals(Arrays.asList("2.5.4.3", "2.5.4.11"), getResolvedValue(decoder, ElytronDescriptionConstants.REQUIRED_OIDS, true));

        decoder = serverModel.get(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyCnDecoder");
        assertEquals("Cn", getResolvedValue(decoder, ElytronDescriptionConstants.ATTRIBUTE_NAME));

        decoder = serverModel.get(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_DECODER).get("ConstantDecoder");
        assertEquals("test", getResolvedValue(decoder, ElytronDescriptionConstants.CONSTANT));
    }

    private void testPrincipalTransformers() {
        ModelNode pt = serverModel.get(ElytronDescriptionConstants.REGEX_PRINCIPAL_TRANSFORMER).get("NameRewriterXY");
        assertEquals("y$1", getResolvedValue(pt, ElytronDescriptionConstants.REPLACEMENT));
        assertEquals("x(.*)", getResolvedValue(pt, ElytronDescriptionConstants.PATTERN));
        assertEquals("false", getResolvedValue(pt, ElytronDescriptionConstants.REPLACE_ALL));

        pt = serverModel.get(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_TRANSFORMER).get("ConstantNameRewriter");
        assertEquals("test", getResolvedValue(pt, ElytronDescriptionConstants.CONSTANT));

        pt = serverModel.get(ElytronDescriptionConstants.CASE_PRINCIPAL_TRANSFORMER).get("CaseNameRewriter");
        assertEquals("true", getResolvedValue(pt, ElytronDescriptionConstants.UPPER_CASE));

        pt = serverModel.get(ElytronDescriptionConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER).get("RegexValidateNameRewriter");
        assertEquals("true", getResolvedValue(pt, ElytronDescriptionConstants.MATCH));
    }

    private void testPropertiesRealm() {
        ModelNode propRealm = serverModel.get(ElytronDescriptionConstants.PROPERTIES_REALM).get("PropRealm");
        assertEquals("FileRealm", getResolvedValue(propRealm.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.DIGEST_REALM_NAME));
        assertEquals("false", getResolvedValue(propRealm.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.PLAIN_TEXT));
        assertEquals("groups", getResolvedValue(propRealm, ElytronDescriptionConstants.GROUPS_ATTRIBUTE));
    }

    private void testProvider() {
        ModelNode provider = serverModel.get(ElytronDescriptionConstants.PROVIDER_LOADER).get("openssl");
        assertEquals("val", getResolvedValue(provider.get(ElytronDescriptionConstants.CONFIGURATION), "prop"));
        provider = serverModel.get(ElytronDescriptionConstants.PROVIDER_LOADER).get("elytron");
        assertEquals("arg", getResolvedValue(provider, ElytronDescriptionConstants.ARGUMENT));
    }

    private void testRealmMappers() {
        ModelNode realmMapper = serverModel.get(ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER).get("MappedRealmMapper");
        assertEquals(".*@(.*)", getResolvedValue(realmMapper, ElytronDescriptionConstants.PATTERN));
        assertEquals("test", getResolvedValue(realmMapper.get(ElytronDescriptionConstants.REALM_MAP), "test"));
    }

    private void testRoleDecoders() {
        ModelNode roleDecoder = serverModel.get(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER).get("ipRoleDecoder");
        assertEquals(Arrays.asList("admin", "user"), getResolvedValue(roleDecoder, ElytronDescriptionConstants.ROLES, true));
        assertEquals("10.12.14.16", getResolvedValue(roleDecoder, ElytronDescriptionConstants.SOURCE_ADDRESS));
        roleDecoder = serverModel.get(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER).get("regexRoleDecoder");
        assertEquals("10\\.12\\.14\\.\\d+$", getResolvedValue(roleDecoder, ElytronDescriptionConstants.PATTERN));
    }

    private void testRoleMappers() {
        ModelNode roleMapper = serverModel.get(ElytronDescriptionConstants.ADD_PREFIX_ROLE_MAPPER).get("RolePrefixer");
        assertEquals("prefix", getResolvedValue(roleMapper, ElytronDescriptionConstants.PREFIX));
        roleMapper = serverModel.get(ElytronDescriptionConstants.ADD_SUFFIX_ROLE_MAPPER).get("RoleSuffixer");
        assertEquals("suffix", getResolvedValue(roleMapper, ElytronDescriptionConstants.SUFFIX));

        roleMapper = serverModel.get(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER).get("MappedRoleMapper");
        assertEquals("false", getResolvedValue(roleMapper, ElytronDescriptionConstants.KEEP_MAPPED));
        assertEquals("true", getResolvedValue(roleMapper, ElytronDescriptionConstants.KEEP_NON_MAPPED));

        roleMapper = serverModel.get(ElytronDescriptionConstants.REGEX_ROLE_MAPPER).get("RegexRoleMapper");
        assertEquals("false", getResolvedValue(roleMapper, ElytronDescriptionConstants.REPLACEMENT));
        assertEquals("false", getResolvedValue(roleMapper, ElytronDescriptionConstants.REPLACE_ALL));
        assertEquals("*(x.)", getResolvedValue(roleMapper, ElytronDescriptionConstants.PATTERN));

        roleMapper = serverModel.get(ElytronDescriptionConstants.CONSTANT_ROLE_MAPPER).get("ConstantRoleMapper");
        assertEquals(Arrays.asList("role"), getResolvedValue(roleMapper, ElytronDescriptionConstants.ROLES, true));

        roleMapper = serverModel.get(ElytronDescriptionConstants.LOGICAL_ROLE_MAPPER).get("LogicalRoleMapper");
        assertEquals("and", getResolvedValue(roleMapper, ElytronDescriptionConstants.LOGICAL_OPERATION));
    }

    private void testSaslServer() {
        ModelNode factory = serverModel.get(ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY).get("SaslAuthenticationDefinition").get(ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS).get(0);
        assertEquals("PLAIN", getResolvedValue(factory, ElytronDescriptionConstants.MECHANISM_NAME));
        assertEquals("host", getResolvedValue(factory, ElytronDescriptionConstants.HOST_NAME));
        assertEquals("protocol", getResolvedValue(factory, ElytronDescriptionConstants.PROTOCOL));
        assertEquals("Test Realm", getResolvedValue(factory.get(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS).get(0), ElytronDescriptionConstants.REALM_NAME));
    }

    private void testSSLComponents() {
        // SSL Context
        ModelNode context = serverModel.get(ElytronDescriptionConstants.SERVER_SSL_CONTEXT).get("server");
        assertEquals(Arrays.asList("TLSv1.2"), getResolvedValue(context, ElytronDescriptionConstants.PROTOCOLS, true));
        assertEquals("true", getResolvedValue(context, ElytronDescriptionConstants.WANT_CLIENT_AUTH));
        assertEquals("true", getResolvedValue(context, ElytronDescriptionConstants.NEED_CLIENT_AUTH));
        assertEquals("true", getResolvedValue(context, ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL));
        assertEquals("false", getResolvedValue(context, ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER));
        assertEquals("false", getResolvedValue(context, ElytronDescriptionConstants.WRAP));
        assertEquals("first", getResolvedValue(context, ElytronDescriptionConstants.PROVIDER_NAME));
        assertEquals("DEFAULT", getResolvedValue(context, ElytronDescriptionConstants.CIPHER_SUITE_FILTER));
        assertEquals("name", getResolvedValue(context, ElytronDescriptionConstants.CIPHER_SUITE_NAMES));
        assertEquals("10", getResolvedValue(context, ElytronDescriptionConstants.MAXIMUM_SESSION_CACHE_SIZE));
        assertEquals("120", getResolvedValue(context, ElytronDescriptionConstants.SESSION_TIMEOUT));

        // Trust Managers
        ModelNode tm = serverModel.get(ElytronDescriptionConstants.TRUST_MANAGER).get("trust-with-ocsp").get(ElytronDescriptionConstants.OCSP);
        assertEquals("http://localhost/ocsp", getResolvedValue(tm, ElytronDescriptionConstants.RESPONDER));
        assertEquals("jceks_store", getResolvedValue(tm, ElytronDescriptionConstants.RESPONDER_KEYSTORE));
        assertEquals("responder-alias", getResolvedValue(tm, ElytronDescriptionConstants.RESPONDER_CERTIFICATE));

        tm = serverModel.get(ElytronDescriptionConstants.TRUST_MANAGER).get("trust-with-crl").get(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST);
        assertEquals("crl.pem", getResolvedValue(tm, ElytronDescriptionConstants.PATH));
        assertEquals("2", getResolvedValue(tm, ElytronDescriptionConstants.MAXIMUM_CERT_PATH));

        // Key Managers
        ModelNode keyManager = serverModel.get(ElytronDescriptionConstants.KEY_MANAGER).get("serverKey2");
        assertEquals("SunX509", getResolvedValue(keyManager, ElytronDescriptionConstants.ALGORITHM));
        assertEquals("one,two,three", getResolvedValue(keyManager, ElytronDescriptionConstants.ALIAS_FILTER));
        assertEquals("localhost", getResolvedValue(keyManager, ElytronDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST));
    }

    private void testTokenRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.TOKEN_REALM).get("JwtRealmOne");
        assertEquals("sub", getResolvedValue(realm, ElytronDescriptionConstants.PRINCIPAL_CLAIM));

        ModelNode jwt = realm.get(ElytronDescriptionConstants.JWT);
        assertEquals(Arrays.asList("some-issuer-a"), getResolvedValue(jwt, ElytronDescriptionConstants.ISSUER, true));
        assertEquals(Arrays.asList("some-audience-a"), getResolvedValue(jwt, ElytronDescriptionConstants.AUDIENCE, true));
        assertEquals("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrVrCuTtArbgaZzL1hvh0xtL5mc7o0NqP", getResolvedValue(jwt, ElytronDescriptionConstants.PUBLIC_KEY));
        assertEquals("cert", getResolvedValue(jwt, ElytronDescriptionConstants.CERTIFICATE));
        assertEquals("public", getResolvedValue(jwt.get(ElytronDescriptionConstants.KEY_MAP), ElytronDescriptionConstants.KID));

        // OAuth
        ModelNode oauth = serverModel.get(ElytronDescriptionConstants.TOKEN_REALM).get("OAuth2Realm").get(ElytronDescriptionConstants.OAUTH2_INTROSPECTION);
        assertEquals("host", getResolvedValue(oauth, ElytronDescriptionConstants.HOST_NAME_VERIFICATION_POLICY));
        assertEquals("a", getResolvedValue(oauth, ElytronDescriptionConstants.CLIENT_ID));
        assertEquals("b", getResolvedValue(oauth, ElytronDescriptionConstants.CLIENT_SECRET));
        assertEquals("https://localhost/token/introspect", getResolvedValue(oauth, ElytronDescriptionConstants.INTROSPECTION_URL));
    }

    private Object getResolvedValue(ModelNode node, String attributeName) {
        return getResolvedValue(node, attributeName, false);
    }

    private Object getResolvedValue(ModelNode node, String attributeName, boolean isList) {
        ModelNode result = node.get(attributeName);
        if (!isList) {
            return result.asString();
        }
        List<String> results = new ArrayList<>();
        for (ModelNode n : result.asList()) {
            results.add(n.asString());
        }
        return results;
    }
}
