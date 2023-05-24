/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.wildfly.security.authz.RoleDecoder.KEY_SOURCE_ADDRESS;

import java.io.FilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.security.asn1.ASN1Encodable;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.MechanismRealmConfiguration;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.authz.PermissionMappable;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.evidence.X509PeerCertificateChainEvidence;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.X500AttributeTypeAndValue;
import org.wildfly.security.x500.X500PrincipalBuilder;
import org.wildfly.security.x500.cert.SubjectAlternativeNamesExtension;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

import mockit.integration.junit4.JMockit;


/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
@RunWith(JMockit.class)
public class DomainTestCase extends AbstractSubsystemTest {

    public DomainTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private ModelNode assertFail(ModelNode response) {
        if (response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private void init() throws Exception {
        TestEnvironment.mockCallerModuleClassloader();
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("domain-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "MyDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "X500Domain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "X500DomainTwo");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "X500DomainThree");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "AnotherDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "AggregateEvidenceDecoderDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "SubjectAltNameEvidenceDecoderDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "SubjectEvidenceDecoderDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "SourceAddressRoleDecoderDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "PropEncodingDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "PropEncodingCharsetDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "FilesystemEncodingDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "FilesystemEncodingCharsetDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "LowerCasePrincipalTransformerDomain");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "UpperCasePrincipalTransformerDomain");
    }

    /**
     * Testing legacy properties realm BASE64 encoding and UTF-8 charset.
     */
    @Test
    public void testPropRealmBase64EncodingConfiguration() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("PropEncodingDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("elytron"); // the realm contains user "elytron"
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
    }

    /**
     * Testing legacy properties realm BASE64 encoding and GB2312 charset.
     */
    @Test
    public void testPropRealmBase64EncodingAndCharsetConfiguration() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("PropEncodingCharsetDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("elytron4"); // the realm contains user "elytron4"
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
    }

    @Test
    public void testFilesystemRealmHexEncodingConfiguration() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemEncodingDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("plainUser"); // the realm contains user "plainUser"
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
    }

    @Test
    public void testFilesystemRealmHexEncodingAndCharsetConfiguration() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemEncodingCharsetDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("plainUser"); // the realm contains user "plainUser"
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
    }

    @Test
    public void testCasePrincipalTransformerLowerCase() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("LowerCasePrincipalTransformerDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("USER1"); // the realm contains "user1"
        Assert.assertTrue(context.exists());
        context.authorize();
        context.succeed();

        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user1", identity.getPrincipal().getName()); // after pre-realm-name-rewriter only
    }

    @Test
    public void testCasePrincipalTransformerUpperCase() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("UpperCasePrincipalTransformerDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user8"); // the realm contains "USER8"
        Assert.assertTrue(context.exists());
        context.authorize();
        context.succeed();

        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("USER8", identity.getPrincipal().getName()); // after pre-realm-name-rewriter only
    }

    @Test
    public void testDefaultRealmIdentity() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("MyDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("firstUser"); // from FileRealm
        Assert.assertTrue(context.exists());
        context.authorize();
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("John", identity.getAttributes().get("firstName").get(0));
        Assert.assertEquals("Smith", identity.getAttributes().get("lastName").get(0));

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("prefixEmployeesuffix"));
        Assert.assertTrue(roles.contains("prefixManagersuffix"));
        Assert.assertTrue(roles.contains("prefixAdminsuffix"));
        Assert.assertEquals("firstUser", identity.getPrincipal().getName());

        Assert.assertTrue(identity.implies(new FilePermission("test", "read")));
        Assert.assertFalse(identity.implies(new FilePermission("test", "write")));
    }

    @Test
    public void testNonDefaultRealmIdentity() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("MyDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        MechanismConfiguration mechConf = MechanismConfiguration.builder()
                .addMechanismRealm(MechanismRealmConfiguration.builder().setRealmName("FileRealm").build())
                .addMechanismRealm(MechanismRealmConfiguration.builder().setRealmName("PropRealm").build())
                .build();
        ServerAuthenticationContext context = domain.createNewAuthenticationContext(MechanismConfigurationSelector.constantSelector(mechConf));

        context.setMechanismRealmName("PropRealm");
        context.setAuthenticationName("xser1@PropRealm");
        Assert.assertTrue(context.exists());
        context.authorize();
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("yser1@PropRealm", identity.getPrincipal().getName()); // after pre-realm-name-rewriter only
    }

    @Test
    public void testNamePrincipalMapping() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("MyDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        Assert.assertFalse(domain.getIdentity("wrong").exists());
        Assert.assertFalse(domain.getIdentity("firstUser@wrongRealm").exists());
        Assert.assertTrue(domain.getIdentity("firstUser").exists());
        Assert.assertTrue(domain.getIdentity("user1@PropRealm").exists());
        Assert.assertTrue(domain.getIdentity(new NamePrincipal("user1@PropRealm")).exists());
    }

    @Test
    public void testX500PrincipalMapping() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("X500Domain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        Assert.assertTrue(domain.getIdentity(new X500Principal("cn=firstUser,ou=group")).exists());

        serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("X500DomainTwo");
        domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);
        Assert.assertTrue(domain.getIdentity(new X500Principal("dc=com,dc=redhat,dc=example,ou=group,cn=First User,cn=firstUser,cn=User,cn=Users")).exists());
        // The given principal is missing the required OU component
        Assert.assertFalse(domain.getIdentity(new X500Principal("cn=John Smith,cn=jsmith,dc=example,dc=redhat,dc=com")).exists());

        serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("X500DomainThree");
        domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);
        Assert.assertTrue(domain.getIdentity(new X500Principal("cn=John Smith,cn=jsmith,ou=people,dc=example,dc=redhat,dc=com")).exists());
    }

    @Test
    public void testTrustedSecurityDomains() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("MyDomain");
        SecurityDomain myDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(myDomain);

        serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("X500Domain");
        SecurityDomain x500Domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(x500Domain);

        serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("AnotherDomain");
        SecurityDomain anotherDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(anotherDomain);

        SecurityIdentity establishedIdentity = getIdentityFromDomain(myDomain, "firstUser");
        ServerAuthenticationContext authenticationContext = anotherDomain.createNewAuthenticationContext();

        // AnotherDomain trusts MyDomain
        Assert.assertTrue(authenticationContext.importIdentity(establishedIdentity));

        establishedIdentity = getIdentityFromDomain(anotherDomain, "firstUser");
        authenticationContext = x500Domain.createNewAuthenticationContext();
        // X500Domain does not trust AnotherDomain
        Assert.assertFalse(authenticationContext.importIdentity(establishedIdentity));
    }
    @Test
    public void testDomainRealmsAndDefaultRealmValidation() throws Exception {
        init();

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.SECURITY_DOMAIN,"MyDomain");
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.DEFAULT_REALM);
        operation.get(ClientConstants.VALUE).set("PropRealm");
        Assert.assertNotNull(assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.SECURITY_DOMAIN,"MyDomain");
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.DEFAULT_REALM);
        operation.get(ClientConstants.VALUE).set("NonDomainRealm");
        Assert.assertNotNull(assertFail(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.SECURITY_DOMAIN,"MyDomain");
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.REALMS);
        ModelNode valueRealms = new ModelNode();
        valueRealms.add(new ModelNode().set(ElytronDescriptionConstants.REALM, "PropRealm"));
        operation.get(ClientConstants.VALUE).set(valueRealms);
        Assert.assertNotNull(assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.SECURITY_DOMAIN,"MyDomain");
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.REALMS);
        valueRealms = new ModelNode();
        valueRealms.add(new ModelNode().set(ElytronDescriptionConstants.REALM, "FileRealm"));
        operation.get(ClientConstants.VALUE).set(valueRealms);
        Assert.assertNotNull(assertFail(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());
    }

    /**
     * Regression test for WFCORE-2614 - don't allow duplicating realms referenced from a single domain.
     */
    @Test
    public void testDuplicateRealmValidation() throws Exception {
        init();

        ModelNode realmNode = new ModelNode();

        ModelNode operation = Util.createEmptyOperation("list-add", PathAddress.pathAddress("subsystem", "elytron")
                .append(ElytronDescriptionConstants.SECURITY_DOMAIN, "MyDomain"));
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.REALMS);

        realmNode.get("realm").set("PropRealm");
        operation.get("value").set(realmNode);
        Assert.assertNotNull(assertFail(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        realmNode.get("realm").set("FileRealm");
        operation.get("value").set(realmNode);
        Assert.assertNotNull(assertFail(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        realmNode.get("realm").set("NonDomainRealm");
        operation.get("value").set(realmNode);
        Assert.assertNotNull(assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());
    }

    @Test
    public void testPermissionMappers() throws Exception {
        init();

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("MyDomain");
        SecurityDomain myDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        SecurityIdentity firstUser = getIdentityFromDomain(myDomain, "firstUser");
        Roles roles = Roles.fromSet(new HashSet<>(Arrays.asList(new String[]{"role1", "role2"})));

        serviceName = Capabilities.PERMISSION_MAPPER_RUNTIME_CAPABILITY.getCapabilityServiceName("SimplePermissionMapperRole");
        PermissionMapper mapper = (PermissionMapper) services.getContainer().getService(serviceName).getValue();
        PermissionVerifier verifier = mapper.mapPermissions(firstUser, roles);
        Assert.assertTrue(verifier.implies(new LoginPermission()));
        Assert.assertFalse(verifier.implies(new FilePermission("aaa", "read")));

        serviceName = Capabilities.PERMISSION_MAPPER_RUNTIME_CAPABILITY.getCapabilityServiceName("SimplePermissionMapperPrincipal");
        mapper = (PermissionMapper) services.getContainer().getService(serviceName).getValue();
        verifier = mapper.mapPermissions(firstUser, roles);
        Assert.assertTrue(verifier.implies(new LoginPermission()));
        Assert.assertFalse(verifier.implies(new FilePermission("aaa", "read")));
    }

    @Test
    public void testSubjectEvidenceDecoder() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("SubjectEvidenceDecoderDomain");
        SecurityDomain securityDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityDomain);

        X509PeerCertificateChainEvidence evidence = new X509PeerCertificateChainEvidence(populateCertificateChain(false));
        ServerAuthenticationContext sac = securityDomain.createNewAuthenticationContext();
        sac.setDecodedEvidencePrincipal(evidence);
        Assert.assertEquals("CN=bob0", evidence.getDecodedPrincipal().getName());
        sac.setAuthenticationPrincipal(evidence.getDecodedPrincipal());
        Assert.assertEquals("0", sac.getAuthenticationPrincipal().getName());
    }

    @Test
    public void testSubjectAltNameEvidenceDecoder() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("SubjectAltNameEvidenceDecoderDomain");
        SecurityDomain securityDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityDomain);

        X509PeerCertificateChainEvidence evidence = new X509PeerCertificateChainEvidence(populateCertificateChain(true ));
        ServerAuthenticationContext sac = securityDomain.createNewAuthenticationContext();
        sac.setDecodedEvidencePrincipal(evidence);
        Assert.assertEquals("bob0@anotherexample.com", evidence.getDecodedPrincipal().getName());
        sac.setAuthenticationPrincipal(evidence.getDecodedPrincipal());
        Assert.assertEquals("bob0", sac.getAuthenticationPrincipal().getName());
    }

    @Test
    public void testAggregateEvidenceDecoder() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("AggregateEvidenceDecoderDomain");
        SecurityDomain securityDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityDomain);

        // evidence will be decoded using a subject alternative name
        X509PeerCertificateChainEvidence evidence = new X509PeerCertificateChainEvidence(populateCertificateChain(true ));
        ServerAuthenticationContext sac = securityDomain.createNewAuthenticationContext();
        sac.setDecodedEvidencePrincipal(evidence);
        Assert.assertEquals("bob0@anotherexample.com", evidence.getDecodedPrincipal().getName());
        sac.setAuthenticationPrincipal(evidence.getDecodedPrincipal());
        Assert.assertEquals("bob0", sac.getAuthenticationPrincipal().getName());

        // evidence will be decoded using the subject
        evidence = new X509PeerCertificateChainEvidence(populateCertificateChain(false));
        sac = securityDomain.createNewAuthenticationContext();
        sac.setDecodedEvidencePrincipal(evidence);
        Assert.assertEquals("CN=bob0", evidence.getDecodedPrincipal().getName());
        sac.setAuthenticationPrincipal(evidence.getDecodedPrincipal());
        Assert.assertEquals("0", sac.getAuthenticationPrincipal().getName());
    }

    @Test
    public void testSourceAddressRoleDecoderWithMatch() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("SourceAddressRoleDecoderDomain");
        SecurityDomain securityDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityDomain);

        ServerAuthenticationContext sac = securityDomain.createNewAuthenticationContext();
        sac.setAuthenticationName("user2");
        Assert.assertFalse(sac.authorize()); // based on the security realm alone, user2 does not have "admin" role

        // make use of the runtime source IP address attribute
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes("10.12.14.16"));
        sac.setAuthenticationName("user2");
        Assert.assertTrue(sac.authorize());

        // runtime source IP address attribute not specified
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes(null));
        sac.setAuthenticationName("user2");
        Assert.assertFalse(sac.authorize());

        sac = securityDomain.createNewAuthenticationContext();
        sac.setAuthenticationName("user1");
        Assert.assertTrue(sac.authorize()); // based on the security realm alone, user1 already has "admin" role

        // make use of the runtime source IP address attribute, make sure user1 still has "admin" role
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes("10.12.14.16"));
        sac.setAuthenticationName("user1");
        Assert.assertTrue(sac.authorize());

        // make use of the runtime source IP address attribute, make sure user1 still has "admin" role
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes(null));
        sac.setAuthenticationName("user1");
        Assert.assertTrue(sac.authorize());
    }

    @Test
    public void testSourceAddressRoleDecoderWithMismatch() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("SourceAddressRoleDecoderDomain");
        SecurityDomain securityDomain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityDomain);

        ServerAuthenticationContext sac = securityDomain.createNewAuthenticationContext();
        sac.setAuthenticationName("user2");
        Assert.assertFalse(sac.authorize()); // based on the security realm alone, user2 does not have "admin" role

        // make use of the runtime source IP address attribute
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes("10.12.16.16"));
        sac.setAuthenticationName("user2");
        Assert.assertFalse(sac.authorize());

        sac = securityDomain.createNewAuthenticationContext();
        sac.setAuthenticationName("user1");
        Assert.assertTrue(sac.authorize()); // based on the security realm alone, user1 already has "admin" role

        // make use of the runtime source IP address attribute, make sure user1 still has "admin" role
        sac = securityDomain.createNewAuthenticationContext();
        sac.addRuntimeAttributes(createRuntimeAttributes("10.12.16.16"));
        sac.setAuthenticationName("user1");
        Assert.assertTrue(sac.authorize());
    }

    public static class MyPermissionMapper implements PermissionMapper {
        @Override
        public PermissionVerifier mapPermissions(PermissionMappable permissionMappable, Roles roles) {
            return PermissionVerifier.from(new LoginPermission())
                    .or(permission -> roles.contains("prefixAdminsuffix") && permission.getActions().equals("read"));
        }
    }

    public static class LoginPermissionMapper implements PermissionMapper {
        @Override
        public PermissionVerifier mapPermissions(PermissionMappable permissionMappable, Roles roles) {
            return PermissionVerifier.from(new LoginPermission());
        }
    }

    private SecurityIdentity getIdentityFromDomain(final SecurityDomain securityDomain, final String userName) throws Exception {
        final ServerAuthenticationContext authenticationContext = securityDomain.createNewAuthenticationContext();
        authenticationContext.setAuthenticationName(userName);
        authenticationContext.authorize();
        authenticationContext.succeed();
        return authenticationContext.getAuthorizedIdentity();
    }

    private static X509Certificate[] populateCertificateChain(boolean includeSubjectAltNames) throws Exception {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
        final KeyPair[] keyPairs = new KeyPair[5];
        for (int i = 0; i < keyPairs.length; i++) {
            keyPairs[i] = keyPairGenerator.generateKeyPair();
        }
        final X509Certificate[] orderedCertificates = new X509Certificate[5];
        for (int i = 0; i < orderedCertificates.length; i++) {
            X509CertificateBuilder builder = new X509CertificateBuilder();
            X500PrincipalBuilder principalBuilder = new X500PrincipalBuilder();
            principalBuilder.addItem(X500AttributeTypeAndValue.create(X500.OID_AT_COMMON_NAME,
                    ASN1Encodable.ofUtf8String("bob" + i)));
            X500Principal dn = principalBuilder.build();
            builder.setSubjectDn(dn);
            if (i == orderedCertificates.length - 1) {
                // self-signed
                builder.setIssuerDn(dn);
                builder.setSigningKey(keyPairs[i].getPrivate());
            } else {
                principalBuilder = new X500PrincipalBuilder();
                principalBuilder.addItem(X500AttributeTypeAndValue.create(X500.OID_AT_COMMON_NAME,
                        ASN1Encodable.ofUtf8String("bob" + (i + 1))));
                X500Principal issuerDn = principalBuilder.build();
                builder.setIssuerDn(issuerDn);
                builder.setSigningKey(keyPairs[i + 1].getPrivate());
                if (includeSubjectAltNames) {
                    builder.addExtension(new SubjectAlternativeNamesExtension(
                            true,
                            Arrays.asList(new GeneralName.RFC822Name("bob" + i + "@example.com"),
                                    new GeneralName.DNSName("bob" + i + ".example.com"),
                                    new GeneralName.RFC822Name("bob" + i + "@anotherexample.com"))));
                }
            }
            builder.setSignatureAlgorithmName("SHA256withRSA");
            builder.setPublicKey(keyPairs[i].getPublic());
            orderedCertificates[i] = builder.build();
        }
        return orderedCertificates;
    }

    private Attributes createRuntimeAttributes(String actualSourceAddress) {
        MapAttributes runtimeAttributes = new MapAttributes();
        if (actualSourceAddress != null) {
            runtimeAttributes.addFirst(KEY_SOURCE_ADDRESS, actualSourceAddress);
        }
        return runtimeAttributes;
    }
}
