/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;


import static org.jboss.as.controller.client.helpers.ClientConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.helpers.ClientConstants;
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

    private static final String ELYTRON = "elytron";

    private ModelNode serverModel;
    private KernelServices kernelServices;

    public ResolveExpressionAttributesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void init() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml());
        kernelServices = builder.build();
        Assert.assertTrue("Subsystem boot failed!", kernelServices.isSuccessfulBoot());
        ModelNode rootModel = kernelServices.readWholeModel();
        serverModel = rootModel.require(ModelDescriptionConstants.SUBSYSTEM).require(ElytronExtension.SUBSYSTEM_NAME);
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
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.AGGREGATE_REALM, "AggregateOne"), ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER), getValue(aggRealm, ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER));
    }

    private void testCertificateAuthorityAccount() {
        ModelNode caAccount = serverModel.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).get("MyCA");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, "MyCA");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CONTACT_URLS, true), getValue(caAccount, ElytronDescriptionConstants.CONTACT_URLS, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE_AUTHORITY), getValue(caAccount, ElytronDescriptionConstants.CERTIFICATE_AUTHORITY));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALIAS), getValue(caAccount, ElytronDescriptionConstants.ALIAS));
    }

    private void testCertificateAuthority() {
        ModelNode ca = serverModel.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).get("testCA");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY, "testCA");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.STAGING_URL), getValue(ca, ElytronDescriptionConstants.STAGING_URL));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.URL), getValue(ca, ElytronDescriptionConstants.URL));
    }

    private void testCredentialStore() {
        // Credential Stores
        ModelNode cs = serverModel.get(ElytronDescriptionConstants.CREDENTIAL_STORE).get("test1");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.CREDENTIAL_STORE, "test1");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.LOCATION), getValue(cs, ElytronDescriptionConstants.LOCATION));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.TYPE), getValue(cs, ElytronDescriptionConstants.TYPE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PROVIDER_NAME), getValue(cs, ElytronDescriptionConstants.PROVIDER_NAME));

        cs = cs.get(ElytronDescriptionConstants.IMPLEMENTATION_PROPERTIES);
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.IMPLEMENTATION_PROPERTIES), "keyStoreType"), getValue(cs, "keyStoreType"));

        cs = serverModel.get(ElytronDescriptionConstants.CREDENTIAL_STORE).get("test4");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CREDENTIAL_STORE, "test4"), ElytronDescriptionConstants.PATH), getValue(cs, ElytronDescriptionConstants.PATH));

        // Secret Credential Store
        cs = serverModel.get(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE).get("test3");
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "test3");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CREATE), getValue(cs, ElytronDescriptionConstants.CREATE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.POPULATE), getValue(cs, ElytronDescriptionConstants.POPULATE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.KEY_SIZE), getValue(cs, ElytronDescriptionConstants.KEY_SIZE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.DEFAULT_ALIAS), getValue(cs, ElytronDescriptionConstants.DEFAULT_ALIAS));
    }

    private void testCustomComponent() {
        // Using custom permission mapper as example
        ModelNode mapper = serverModel.get(ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER).get("MyPermissionMapper");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER, "MyPermissionMapper").get(ElytronDescriptionConstants.CONFIGURATION), "test"),
                getValue(mapper.get(ElytronDescriptionConstants.CONFIGURATION), "test"));
    }

    private void testElytronDefinition() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add(SUBSYSTEM, ELYTRON);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        ModelNode evaluatedResult = kernelServices.executeOperation(operation).get(ClientConstants.RESULT);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.DISALLOWED_PROVIDERS, true), getValue(serverModel, ElytronDescriptionConstants.DISALLOWED_PROVIDERS, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REGISTER_JASPI_FACTORY), getValue(serverModel, ElytronDescriptionConstants.REGISTER_JASPI_FACTORY));

    }

    private void testEvidenceDecoder() {
        ModelNode decoder = serverModel.get(ElytronDescriptionConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER).get("rfc822Decoder");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER, "rfc822Decoder");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALT_NAME_TYPE), getValue(decoder, ElytronDescriptionConstants.ALT_NAME_TYPE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SEGMENT), getValue(decoder, ElytronDescriptionConstants.SEGMENT));
    }

    private void testFailoverRealm() {
        ModelNode failoverRealm = serverModel.get(ElytronDescriptionConstants.FAILOVER_REALM).get("FailoverRealm");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.FAILOVER_REALM, "FailoverRealm"), ElytronDescriptionConstants.EMIT_EVENTS), getValue(failoverRealm, ElytronDescriptionConstants.EMIT_EVENTS));
    }

    private void testFileSystemRealm() {
        ModelNode fileRealm = serverModel.get(ElytronDescriptionConstants.FILESYSTEM_REALM).get("FileRealm");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.FILESYSTEM_REALM, "FileRealm");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.LEVELS), getValue(fileRealm, ElytronDescriptionConstants.LEVELS));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ENCODED), getValue(fileRealm, ElytronDescriptionConstants.ENCODED));
    }

    private void testFilteringKeyStoreDefinition() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.FILTERING_KEY_STORE).get("FilteringKeyStore");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.FILTERING_KEY_STORE, "FilteringKeyStore"), ElytronDescriptionConstants.ALIAS_FILTER), getValue(keystore, ElytronDescriptionConstants.ALIAS_FILTER));
    }

    private void testIdentityRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.IDENTITY_REALM).get("local");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.IDENTITY_REALM, "local");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.IDENTITY), getValue(realm, ElytronDescriptionConstants.IDENTITY));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ATTRIBUTE_NAME), getValue(realm, ElytronDescriptionConstants.ATTRIBUTE_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ATTRIBUTE_VALUES, true), getValue(realm, ElytronDescriptionConstants.ATTRIBUTE_VALUES, true));
    }

    private void testJaspiConfiguration() {
        ModelNode jaspi = serverModel.get(ElytronDescriptionConstants.JASPI_CONFIGURATION).get("test");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JASPI_CONFIGURATION, "test");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.LAYER), getValue(jaspi, ElytronDescriptionConstants.LAYER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.APPLICATION_CONTEXT), getValue(jaspi, ElytronDescriptionConstants.APPLICATION_CONTEXT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.DESCRIPTION), getValue(jaspi, ElytronDescriptionConstants.DESCRIPTION));

        ModelNode testModule = jaspi.get(ElytronDescriptionConstants.SERVER_AUTH_MODULES).get(0);
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.SERVER_AUTH_MODULES).get(0), ElytronDescriptionConstants.FLAG), getValue(testModule, ElytronDescriptionConstants.FLAG));

        ModelNode options = testModule.get(ElytronDescriptionConstants.OPTIONS);
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.SERVER_AUTH_MODULES).get(0).get(ElytronDescriptionConstants.OPTIONS), "a"), getValue(options, "a"));
    }

    private void testJdbcRealm() {

        ModelNode jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmBcrypt").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);

        // Bcrypt password mapper
        ModelNode mapper = jdbcRealm.get(ElytronDescriptionConstants.BCRYPT_MAPPER);
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcRealmBcrypt").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.BCRYPT_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_INDEX), getValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ITERATION_COUNT_INDEX), getValue(mapper, ElytronDescriptionConstants.ITERATION_COUNT_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HASH_ENCODING), getValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_ENCODING), getValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Clear password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmClearPassword").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.CLEAR_PASSWORD_MAPPER);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcRealmClearPassword").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.CLEAR_PASSWORD_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));

        // Simple digest password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmSimple").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcRealmSimple").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HASH_ENCODING), getValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));

        // Salted simple digest password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmSalted").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcRealmSalted").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_INDEX), getValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALGORITHM), getValue(mapper, ElytronDescriptionConstants.ALGORITHM));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HASH_ENCODING), getValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_ENCODING), getValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Scram password mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcScram").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.SCRAM_MAPPER);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcScram").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.SCRAM_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_INDEX), getValue(mapper, ElytronDescriptionConstants.SALT_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ITERATION_COUNT_INDEX), getValue(mapper, ElytronDescriptionConstants.ITERATION_COUNT_INDEX));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HASH_ENCODING), getValue(mapper, ElytronDescriptionConstants.HASH_ENCODING));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SALT_ENCODING), getValue(mapper, ElytronDescriptionConstants.SALT_ENCODING));

        // Modular crypt mapper
        jdbcRealm = serverModel.get(ElytronDescriptionConstants.JDBC_REALM).get("JdbcRealmModular").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0);
        mapper = jdbcRealm.get(ElytronDescriptionConstants.MODULAR_CRYPT_MAPPER);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.JDBC_REALM, "JdbcRealmModular").get(ElytronDescriptionConstants.PRINCIPAL_QUERY).get(0).get(ElytronDescriptionConstants.MODULAR_CRYPT_MAPPER);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PASSWORD_INDEX), getValue(mapper, ElytronDescriptionConstants.PASSWORD_INDEX));
    }

    private void testKerberosSecurityFactory() {
        ModelNode kerberos = serverModel.get(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY).get("KerberosFactory");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, "KerberosFactory");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PRINCIPAL), getValue(kerberos, ElytronDescriptionConstants.PRINCIPAL));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MINIMUM_REMAINING_LIFETIME), getValue(kerberos, ElytronDescriptionConstants.MINIMUM_REMAINING_LIFETIME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REQUEST_LIFETIME), getValue(kerberos, ElytronDescriptionConstants.REQUEST_LIFETIME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.FAIL_CACHE), getValue(kerberos, ElytronDescriptionConstants.FAIL_CACHE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SERVER), getValue(kerberos, ElytronDescriptionConstants.SERVER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.OBTAIN_KERBEROS_TICKET), getValue(kerberos, ElytronDescriptionConstants.OBTAIN_KERBEROS_TICKET));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.DEBUG), getValue(kerberos, ElytronDescriptionConstants.DEBUG));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.WRAP_GSS_CREDENTIAL), getValue(kerberos, ElytronDescriptionConstants.WRAP_GSS_CREDENTIAL));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REQUIRED), getValue(kerberos, ElytronDescriptionConstants.REQUIRED));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MECHANISM_NAMES, true), getValue(kerberos, ElytronDescriptionConstants.MECHANISM_NAMES, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MECHANISM_OIDS, true), getValue(kerberos, ElytronDescriptionConstants.MECHANISM_OIDS, true));
    }

    private void testKeyStore() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.KEY_STORE).get("jks_store");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.KEY_STORE, "jks_store");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.TYPE), getValue(keystore, ElytronDescriptionConstants.TYPE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PROVIDER_NAME), getValue(keystore, ElytronDescriptionConstants.PROVIDER_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALIAS_FILTER), getValue(keystore, ElytronDescriptionConstants.ALIAS_FILTER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REQUIRED), getValue(keystore, ElytronDescriptionConstants.REQUIRED));
    }

    private void testLdapKeyStore() {
        ModelNode keystore = serverModel.get(ElytronDescriptionConstants.LDAP_KEY_STORE).get("LdapKeyStore");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.LDAP_KEY_STORE, "LdapKeyStore");

        // search
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SEARCH_PATH), getValue(keystore, ElytronDescriptionConstants.SEARCH_PATH));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SEARCH_RECURSIVE), getValue(keystore, ElytronDescriptionConstants.SEARCH_RECURSIVE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SEARCH_TIME_LIMIT), getValue(keystore, ElytronDescriptionConstants.SEARCH_TIME_LIMIT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.FILTER_ALIAS), getValue(keystore, ElytronDescriptionConstants.FILTER_ALIAS));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.FILTER_CERTIFICATE), getValue(keystore, ElytronDescriptionConstants.FILTER_CERTIFICATE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.FILTER_ITERATE), getValue(keystore, ElytronDescriptionConstants.FILTER_ITERATE));

        // attribute mapping
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALIAS_ATTRIBUTE), getValue(keystore, ElytronDescriptionConstants.ALIAS_ATTRIBUTE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE), getValue(keystore, ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE_TYPE), getValue(keystore, ElytronDescriptionConstants.CERTIFICATE_TYPE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE), getValue(keystore, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING), getValue(keystore, ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.KEY_ATTRIBUTE), getValue(keystore, ElytronDescriptionConstants.KEY_ATTRIBUTE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.KEY_TYPE), getValue(keystore, ElytronDescriptionConstants.KEY_TYPE));

        // new item template
        ModelNode template = keystore.get(ElytronDescriptionConstants.NEW_ITEM_TEMPLATE);
        evaluatedResult = evaluatedResult.get(ElytronDescriptionConstants.NEW_ITEM_TEMPLATE);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.NEW_ITEM_PATH), getValue(template, ElytronDescriptionConstants.NEW_ITEM_PATH));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.NEW_ITEM_RDN), getValue(template, ElytronDescriptionConstants.NEW_ITEM_RDN));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.NAME), getValue(template.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.NAME));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.VALUE, true), getValue(template.get(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES).get(0), ElytronDescriptionConstants.VALUE, true));
    }

    private void testLdapRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.LDAP_REALM).get("LdapRealmWithAttributeMapping");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.LDAP_REALM, "LdapRealmWithAttributeMapping");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.DIRECT_VERIFICATION), getValue(realm, ElytronDescriptionConstants.DIRECT_VERIFICATION));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALLOW_BLANK_PASSWORD), getValue(realm, ElytronDescriptionConstants.ALLOW_BLANK_PASSWORD));

        // Identity mapping
        ModelNode identityMapping = realm.get(ElytronDescriptionConstants.IDENTITY_MAPPING);
        ModelNode identityEvaluatedResult = evaluatedResult.get(ElytronDescriptionConstants.IDENTITY_MAPPING);
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.RDN_IDENTIFIER), getValue(identityMapping, ElytronDescriptionConstants.RDN_IDENTIFIER));
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.USE_RECURSIVE_SEARCH), getValue(identityMapping, ElytronDescriptionConstants.USE_RECURSIVE_SEARCH));
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.SEARCH_BASE_DN), getValue(identityMapping, ElytronDescriptionConstants.SEARCH_BASE_DN));
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.FILTER_NAME), getValue(identityMapping, ElytronDescriptionConstants.FILTER_NAME));
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.ITERATOR_FILTER), getValue(identityMapping, ElytronDescriptionConstants.ITERATOR_FILTER));
        assertEquals(getValue(identityEvaluatedResult, ElytronDescriptionConstants.NEW_IDENTITY_PARENT_DN), getValue(identityMapping, ElytronDescriptionConstants.NEW_IDENTITY_PARENT_DN));

        // Attribute mapping
        ModelNode attributeMapping = identityMapping.get(ElytronDescriptionConstants.ATTRIBUTE_MAPPING);
        ModelNode attributeEvaluatedResult = identityEvaluatedResult.get(ElytronDescriptionConstants.ATTRIBUTE_MAPPING);
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.FROM), getValue(attributeMapping.get(0), ElytronDescriptionConstants.FROM));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.TO), getValue(attributeMapping.get(0), ElytronDescriptionConstants.TO));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.REFERENCE), getValue(attributeMapping.get(0), ElytronDescriptionConstants.REFERENCE));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.FILTER), getValue(attributeMapping.get(0), ElytronDescriptionConstants.FILTER));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.FILTER_BASE_DN), getValue(attributeMapping.get(0), ElytronDescriptionConstants.FILTER_BASE_DN));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.SEARCH_RECURSIVE), getValue(attributeMapping.get(0), ElytronDescriptionConstants.SEARCH_RECURSIVE));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.ROLE_RECURSION), getValue(attributeMapping.get(0), ElytronDescriptionConstants.ROLE_RECURSION));
        assertEquals(getValue(attributeEvaluatedResult.get(0), ElytronDescriptionConstants.ROLE_RECURSION_NAME), getValue(attributeMapping.get(0), ElytronDescriptionConstants.ROLE_RECURSION_NAME));
        assertEquals(getValue(attributeEvaluatedResult.get(1), ElytronDescriptionConstants.EXTRACT_RDN), getValue(attributeMapping.get(1), ElytronDescriptionConstants.EXTRACT_RDN));

        // User password credential mapping
        ModelNode userPass = identityMapping.get(ElytronDescriptionConstants.USER_PASSWORD_MAPPER);
        ModelNode userPassEvaluatedResult = identityEvaluatedResult.get(ElytronDescriptionConstants.USER_PASSWORD_MAPPER);
        assertEquals(getValue(userPassEvaluatedResult, ElytronDescriptionConstants.FROM), getValue(userPass, ElytronDescriptionConstants.FROM));
        assertEquals(getValue(userPassEvaluatedResult, ElytronDescriptionConstants.WRITABLE), getValue(userPass, ElytronDescriptionConstants.WRITABLE));
        assertEquals(getValue(userPassEvaluatedResult, ElytronDescriptionConstants.VERIFIABLE), getValue(userPass, ElytronDescriptionConstants.VERIFIABLE));

        // Otp credential mapping
        ModelNode otp = identityMapping.get(ElytronDescriptionConstants.OTP_CREDENTIAL_MAPPER);
        ModelNode otpEvaluatedResult = identityEvaluatedResult.get(ElytronDescriptionConstants.OTP_CREDENTIAL_MAPPER);
        assertEquals(getValue(otpEvaluatedResult, ElytronDescriptionConstants.ALGORITHM_FROM), getValue(otp, ElytronDescriptionConstants.ALGORITHM_FROM));
        assertEquals(getValue(otpEvaluatedResult, ElytronDescriptionConstants.HASH_FROM), getValue(otp, ElytronDescriptionConstants.HASH_FROM));
        assertEquals(getValue(otpEvaluatedResult, ElytronDescriptionConstants.SEED_FROM), getValue(otp, ElytronDescriptionConstants.SEED_FROM));
        assertEquals(getValue(otpEvaluatedResult, ElytronDescriptionConstants.SEQUENCE_FROM), getValue(otp, ElytronDescriptionConstants.SEQUENCE_FROM));

        // X509 Credential mapping
        ModelNode x509 = identityMapping.get(ElytronDescriptionConstants.X509_CREDENTIAL_MAPPER);
        ModelNode x509EvaluatedResult = identityEvaluatedResult.get(ElytronDescriptionConstants.X509_CREDENTIAL_MAPPER);
        assertEquals(getValue(x509EvaluatedResult, ElytronDescriptionConstants.DIGEST_FROM), getValue(x509, ElytronDescriptionConstants.DIGEST_FROM));
        assertEquals(getValue(x509EvaluatedResult, ElytronDescriptionConstants.CERTIFICATE_FROM), getValue(x509, ElytronDescriptionConstants.CERTIFICATE_FROM));
        assertEquals(getValue(x509EvaluatedResult, ElytronDescriptionConstants.DIGEST_ALGORITHM), getValue(x509, ElytronDescriptionConstants.DIGEST_ALGORITHM));
        assertEquals(getValue(x509EvaluatedResult, ElytronDescriptionConstants.SERIAL_NUMBER_FROM), getValue(x509, ElytronDescriptionConstants.SERIAL_NUMBER_FROM));
        assertEquals(getValue(x509EvaluatedResult, ElytronDescriptionConstants.SUBJECT_DN_FROM), getValue(x509, ElytronDescriptionConstants.SUBJECT_DN_FROM));

        // New identity attribute
        ModelNode newIdentity = identityMapping.get(ElytronDescriptionConstants.NEW_IDENTITY_ATTRIBUTES).get(0);
        ModelNode newIdentityEvaluatedResult = identityEvaluatedResult.get(ElytronDescriptionConstants.NEW_IDENTITY_ATTRIBUTES).get(0);
        assertEquals(getValue(newIdentityEvaluatedResult, ElytronDescriptionConstants.NAME), getValue(newIdentity, ElytronDescriptionConstants.NAME));
        assertEquals(getValue(newIdentityEvaluatedResult, ElytronDescriptionConstants.VALUE, true), getValue(newIdentity, ElytronDescriptionConstants.VALUE, true));
    }

    private void testPermissionMappers() {
        ModelNode mapper = serverModel.get(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER).get("SimplePermissionMapperLegacy");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER, "SimplePermissionMapperLegacy");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MAPPING_MODE), getValue(mapper, ElytronDescriptionConstants.MAPPING_MODE));

        mapper = mapper.get(ElytronDescriptionConstants.PERMISSION_MAPPINGS).get(0);
        evaluatedResult = evaluatedResult.get(ElytronDescriptionConstants.PERMISSION_MAPPINGS).get(0);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PRINCIPALS, true), getValue(mapper, ElytronDescriptionConstants.PRINCIPALS, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ROLES, true), getValue(mapper, ElytronDescriptionConstants.ROLES, true));

        mapper = mapper.get(ElytronDescriptionConstants.PERMISSIONS).get(1);
        evaluatedResult = evaluatedResult.get(ElytronDescriptionConstants.PERMISSIONS).get(1);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.TARGET_NAME), getValue(mapper, ElytronDescriptionConstants.TARGET_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ACTION), getValue(mapper, ElytronDescriptionConstants.ACTION));
    }

    private void testPrincipalDecoders() {
        ModelNode decoder = serverModel.get(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyX500PrincipalDecoderTwo");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER, "MyX500PrincipalDecoderTwo");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.OID), getValue(decoder, ElytronDescriptionConstants.OID));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.JOINER), getValue(decoder, ElytronDescriptionConstants.JOINER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.START_SEGMENT), getValue(decoder, ElytronDescriptionConstants.START_SEGMENT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MAXIMUM_SEGMENTS), getValue(decoder, ElytronDescriptionConstants.MAXIMUM_SEGMENTS));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REVERSE), getValue(decoder, ElytronDescriptionConstants.REVERSE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CONVERT), getValue(decoder, ElytronDescriptionConstants.CONVERT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REQUIRED_OIDS, true), getValue(decoder, ElytronDescriptionConstants.REQUIRED_OIDS, true));

        decoder = serverModel.get(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER).get("MyCnDecoder");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER, "MyCnDecoder"), ElytronDescriptionConstants.ATTRIBUTE_NAME), getValue(decoder, ElytronDescriptionConstants.ATTRIBUTE_NAME));

        decoder = serverModel.get(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_DECODER).get("ConstantDecoder");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_DECODER, "ConstantDecoder"), ElytronDescriptionConstants.CONSTANT), getValue(decoder, ElytronDescriptionConstants.CONSTANT));
    }

    private void testPrincipalTransformers() {
        ModelNode pt = serverModel.get(ElytronDescriptionConstants.REGEX_PRINCIPAL_TRANSFORMER).get("NameRewriterXY");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.REGEX_PRINCIPAL_TRANSFORMER, "NameRewriterXY");
        assertEquals( getValue(evaluatedResult, ElytronDescriptionConstants.REPLACEMENT), getValue(pt, ElytronDescriptionConstants.REPLACEMENT));
        assertEquals( getValue(evaluatedResult, ElytronDescriptionConstants.PATTERN), getValue(pt, ElytronDescriptionConstants.PATTERN));
        assertEquals( getValue(evaluatedResult, ElytronDescriptionConstants.REPLACE_ALL), getValue(pt, ElytronDescriptionConstants.REPLACE_ALL));

        pt = serverModel.get(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_TRANSFORMER).get("ConstantNameRewriter");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_TRANSFORMER, "ConstantNameRewriter"), ElytronDescriptionConstants.CONSTANT), getValue(pt, ElytronDescriptionConstants.CONSTANT));

        pt = serverModel.get(ElytronDescriptionConstants.CASE_PRINCIPAL_TRANSFORMER).get("CaseNameRewriter");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CASE_PRINCIPAL_TRANSFORMER, "CaseNameRewriter"), ElytronDescriptionConstants.UPPER_CASE), getValue(pt, ElytronDescriptionConstants.UPPER_CASE));

        pt = serverModel.get(ElytronDescriptionConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER).get("RegexValidateNameRewriter");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER, "RegexValidateNameRewriter"), ElytronDescriptionConstants.MATCH), getValue(pt, ElytronDescriptionConstants.MATCH));
    }

    private void testPropertiesRealm() {
        ModelNode propRealm = serverModel.get(ElytronDescriptionConstants.PROPERTIES_REALM).get("PropRealm");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.PROPERTIES_REALM, "PropRealm");
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.DIGEST_REALM_NAME), getValue(propRealm.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.DIGEST_REALM_NAME));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.PLAIN_TEXT), getValue(propRealm.get(ElytronDescriptionConstants.USERS_PROPERTIES), ElytronDescriptionConstants.PLAIN_TEXT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.GROUPS_ATTRIBUTE), getValue(propRealm, ElytronDescriptionConstants.GROUPS_ATTRIBUTE));
    }

    private void testProvider() {
        ModelNode provider = serverModel.get(ElytronDescriptionConstants.PROVIDER_LOADER).get("openssl");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.PROVIDER_LOADER, "openssl").get(ElytronDescriptionConstants.CONFIGURATION), "prop"), getValue(provider.get(ElytronDescriptionConstants.CONFIGURATION), "prop"));

        provider = serverModel.get(ElytronDescriptionConstants.PROVIDER_LOADER).get(ELYTRON);
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.PROVIDER_LOADER, ELYTRON), ElytronDescriptionConstants.ARGUMENT), getValue(provider, ElytronDescriptionConstants.ARGUMENT));
    }

    private void testRealmMappers() {
        ModelNode realmMapper = serverModel.get(ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER).get("MappedRealmMapper");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER, "MappedRealmMapper");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PATTERN), getValue(realmMapper, ElytronDescriptionConstants.PATTERN));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.REALM_MAP), "test"), getValue(realmMapper.get(ElytronDescriptionConstants.REALM_MAP), "test"));
    }

    private void testRoleDecoders() {
        ModelNode roleDecoder = serverModel.get(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER).get("ipRoleDecoder");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER, "ipRoleDecoder");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ROLES, true), getValue(roleDecoder, ElytronDescriptionConstants.ROLES, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SOURCE_ADDRESS), getValue(roleDecoder, ElytronDescriptionConstants.SOURCE_ADDRESS));

        roleDecoder = serverModel.get(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER).get("regexRoleDecoder");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER, "regexRoleDecoder"), ElytronDescriptionConstants.PATTERN), getValue(roleDecoder, ElytronDescriptionConstants.PATTERN));
    }

    private void testRoleMappers() {
        ModelNode roleMapper = serverModel.get(ElytronDescriptionConstants.ADD_PREFIX_ROLE_MAPPER).get("RolePrefixer");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.ADD_PREFIX_ROLE_MAPPER, "RolePrefixer"), ElytronDescriptionConstants.PREFIX), getValue(roleMapper, ElytronDescriptionConstants.PREFIX));

        roleMapper = serverModel.get(ElytronDescriptionConstants.ADD_SUFFIX_ROLE_MAPPER).get("RoleSuffixer");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.ADD_SUFFIX_ROLE_MAPPER, "RoleSuffixer"), ElytronDescriptionConstants.SUFFIX), getValue(roleMapper, ElytronDescriptionConstants.SUFFIX));

        roleMapper = serverModel.get(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER).get("MappedRoleMapper");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER, "MappedRoleMapper");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.KEEP_MAPPED), getValue(roleMapper, ElytronDescriptionConstants.KEEP_MAPPED));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.KEEP_NON_MAPPED), getValue(roleMapper, ElytronDescriptionConstants.KEEP_NON_MAPPED));

        roleMapper = serverModel.get(ElytronDescriptionConstants.REGEX_ROLE_MAPPER).get("RegexRoleMapper");
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.REGEX_ROLE_MAPPER, "RegexRoleMapper");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REPLACEMENT), getValue(roleMapper, ElytronDescriptionConstants.REPLACEMENT));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.REPLACE_ALL), getValue(roleMapper, ElytronDescriptionConstants.REPLACE_ALL));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PATTERN), getValue(roleMapper, ElytronDescriptionConstants.PATTERN));

        roleMapper = serverModel.get(ElytronDescriptionConstants.CONSTANT_ROLE_MAPPER).get("ConstantRoleMapper");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.CONSTANT_ROLE_MAPPER, "ConstantRoleMapper"), ElytronDescriptionConstants.ROLES, true), getValue(roleMapper, ElytronDescriptionConstants.ROLES, true));

        roleMapper = serverModel.get(ElytronDescriptionConstants.LOGICAL_ROLE_MAPPER).get("LogicalRoleMapper");
        assertEquals(getValue(getEvaluatedNode(ElytronDescriptionConstants.LOGICAL_ROLE_MAPPER, "LogicalRoleMapper"), ElytronDescriptionConstants.LOGICAL_OPERATION), getValue(roleMapper, ElytronDescriptionConstants.LOGICAL_OPERATION));
    }

    private void testSaslServer() {
        ModelNode factory = serverModel.get(ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY).get("SaslAuthenticationDefinition").get(ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS).get(0);
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY, "SaslAuthenticationDefinition").get(ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS).get(0);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MECHANISM_NAME), getValue(factory, ElytronDescriptionConstants.MECHANISM_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HOST_NAME), getValue(factory, ElytronDescriptionConstants.HOST_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PROTOCOL), getValue(factory, ElytronDescriptionConstants.PROTOCOL));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS).get(0), ElytronDescriptionConstants.REALM_NAME), getValue(factory.get(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS).get(0), ElytronDescriptionConstants.REALM_NAME));
    }

    private void testSSLComponents() {
        // SSL Context
        ModelNode context = serverModel.get(ElytronDescriptionConstants.SERVER_SSL_CONTEXT).get("server");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, "server");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PROTOCOLS, true), getValue(context, ElytronDescriptionConstants.PROTOCOLS, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.WANT_CLIENT_AUTH), getValue(context, ElytronDescriptionConstants.WANT_CLIENT_AUTH));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.NEED_CLIENT_AUTH), getValue(context, ElytronDescriptionConstants.NEED_CLIENT_AUTH));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL), getValue(context, ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER), getValue(context, ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.WRAP), getValue(context, ElytronDescriptionConstants.WRAP));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PROVIDER_NAME), getValue(context, ElytronDescriptionConstants.PROVIDER_NAME));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CIPHER_SUITE_FILTER), getValue(context, ElytronDescriptionConstants.CIPHER_SUITE_FILTER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CIPHER_SUITE_NAMES), getValue(context, ElytronDescriptionConstants.CIPHER_SUITE_NAMES));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MAXIMUM_SESSION_CACHE_SIZE), getValue(context, ElytronDescriptionConstants.MAXIMUM_SESSION_CACHE_SIZE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.SESSION_TIMEOUT), getValue(context, ElytronDescriptionConstants.SESSION_TIMEOUT));

        // Trust Managers
        ModelNode tm = serverModel.get(ElytronDescriptionConstants.TRUST_MANAGER).get("trust-with-ocsp").get(ElytronDescriptionConstants.OCSP);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.TRUST_MANAGER, "trust-with-ocsp").get(ElytronDescriptionConstants.OCSP);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.RESPONDER), getValue(tm, ElytronDescriptionConstants.RESPONDER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.RESPONDER_KEYSTORE), getValue(tm, ElytronDescriptionConstants.RESPONDER_KEYSTORE));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.RESPONDER_CERTIFICATE), getValue(tm, ElytronDescriptionConstants.RESPONDER_CERTIFICATE));

        tm = serverModel.get(ElytronDescriptionConstants.TRUST_MANAGER).get("trust-with-crl").get(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.TRUST_MANAGER, "trust-with-crl").get(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PATH), getValue(tm, ElytronDescriptionConstants.PATH));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.MAXIMUM_CERT_PATH), getValue(tm, ElytronDescriptionConstants.MAXIMUM_CERT_PATH));

        // Key Managers
        ModelNode keyManager = serverModel.get(ElytronDescriptionConstants.KEY_MANAGER).get("serverKey2");
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.KEY_MANAGER, "serverKey2");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALGORITHM), getValue(keyManager, ElytronDescriptionConstants.ALGORITHM));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ALIAS_FILTER), getValue(keyManager, ElytronDescriptionConstants.ALIAS_FILTER));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST), getValue(keyManager, ElytronDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST));
    }

    private void testTokenRealm() {
        ModelNode realm = serverModel.get(ElytronDescriptionConstants.TOKEN_REALM).get("JwtRealmOne");
        ModelNode evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.TOKEN_REALM, "JwtRealmOne");
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PRINCIPAL_CLAIM), getValue(realm, ElytronDescriptionConstants.PRINCIPAL_CLAIM));

        ModelNode jwt = realm.get(ElytronDescriptionConstants.JWT);
        evaluatedResult = evaluatedResult.get(ElytronDescriptionConstants.JWT);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.ISSUER, true), getValue(jwt, ElytronDescriptionConstants.ISSUER, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.AUDIENCE, true), getValue(jwt, ElytronDescriptionConstants.AUDIENCE, true));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.PUBLIC_KEY), getValue(jwt, ElytronDescriptionConstants.PUBLIC_KEY));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CERTIFICATE), getValue(jwt, ElytronDescriptionConstants.CERTIFICATE));
        assertEquals(getValue(evaluatedResult.get(ElytronDescriptionConstants.KEY_MAP), ElytronDescriptionConstants.KID), getValue(jwt.get(ElytronDescriptionConstants.KEY_MAP), ElytronDescriptionConstants.KID));

        // OAuth
        ModelNode oauth = serverModel.get(ElytronDescriptionConstants.TOKEN_REALM).get("OAuth2Realm").get(ElytronDescriptionConstants.OAUTH2_INTROSPECTION);
        evaluatedResult = getEvaluatedNode(ElytronDescriptionConstants.TOKEN_REALM, "OAuth2Realm").get(ElytronDescriptionConstants.OAUTH2_INTROSPECTION);
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.HOST_NAME_VERIFICATION_POLICY), getValue(oauth, ElytronDescriptionConstants.HOST_NAME_VERIFICATION_POLICY));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CLIENT_ID), getValue(oauth, ElytronDescriptionConstants.CLIENT_ID));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.CLIENT_SECRET), getValue(oauth, ElytronDescriptionConstants.CLIENT_SECRET));
        assertEquals(getValue(evaluatedResult, ElytronDescriptionConstants.INTROSPECTION_URL), getValue(oauth, ElytronDescriptionConstants.INTROSPECTION_URL));
    }

    private Object getValue(ModelNode node, String attributeName) {
        return getValue(node, attributeName, false);
    }

    private Object getValue(ModelNode node, String attributeName, boolean isList) {
        ModelNode result = node.get(attributeName).resolve();
        if (!isList) {
            return result.asString();
        }
        List<String> results = new ArrayList<>();
        for (ModelNode n : result.asList()) {
            results.add(n.asString());
        }
        return results;
    }

    private ModelNode getEvaluatedNode(String resource, String childResource) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add(SUBSYSTEM, ELYTRON)
                .add(resource).add(childResource);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);

        return kernelServices.executeOperation(operation).get(ClientConstants.RESULT);
    }
}
