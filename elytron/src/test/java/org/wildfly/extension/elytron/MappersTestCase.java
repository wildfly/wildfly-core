/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.wildfly.security.authz.RoleDecoder.KEY_SOURCE_ADDRESS;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.asn1.ASN1Encodable;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.evidence.X509PeerCertificateChainEvidence;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.X500AttributeTypeAndValue;
import org.wildfly.security.x500.X500PrincipalBuilder;
import org.wildfly.security.x500.cert.SubjectAlternativeNamesExtension;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class MappersTestCase extends AbstractElytronSubsystemBaseTest {
    public MappersTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("mappers-test.xml");
    }

    /* principal-transformers test - rewriting e-mail addresses by server part */
    @Test
    public void testPrincipalTransformerTree() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomain");
        ServiceName serviceName = Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.getCapabilityServiceName("tree");
        PrincipalTransformer transformer = (PrincipalTransformer) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(transformer);

        Assert.assertEquals("alpha@jboss.org", transformer.apply(new NamePrincipal("alpha@jboss.com")).getName()); // com to org
        Assert.assertEquals("beta", transformer.apply(new NamePrincipal("beta@wildfly.org")).getName()); // remove server part
        Assert.assertEquals("gamma@example.com", transformer.apply(new NamePrincipal("gamma@example.com")).getName()); // keep
        Assert.assertNull(transformer.apply(new NamePrincipal("invalid"))); // not an e-mail address
        Assert.assertNull(transformer.apply(null));
    }

    @Test
    public void testCasePrincipalTransformerUpperCase() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainUpperCase");
        ServiceName serviceName = Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.getCapabilityServiceName("upperCase");
        PrincipalTransformer transformer = (PrincipalTransformer) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(transformer);

        Assert.assertEquals("ALPHA", transformer.apply(new NamePrincipal("alpha")).getName());
        Assert.assertNull(transformer.apply(null));
    }

    @Test
    public void testCasePrincipalTransformerLowerCase() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainLowerCase");
        ServiceName serviceName = Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.getCapabilityServiceName("lowerCase");
        PrincipalTransformer transformer = (PrincipalTransformer) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(transformer);

        Assert.assertEquals("alpha", transformer.apply(new NamePrincipal("ALPHA")).getName());
        Assert.assertNull(transformer.apply(null));
    }

    @Test
    public void testEvidenceDecoder() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomain");

        X509Certificate[] certificateChain = populateCertificateChain(true);
        X509PeerCertificateChainEvidence evidence = new X509PeerCertificateChainEvidence(certificateChain);

        ServiceName serviceName = Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("subjectDecoder");
        EvidenceDecoder evidenceDecoder = (EvidenceDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(evidenceDecoder);
        Assert.assertEquals("CN=bob0", evidenceDecoder.getPrincipal(evidence).getName());

        serviceName = Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("rfc822Decoder");
        evidenceDecoder = (EvidenceDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(evidenceDecoder);
        Assert.assertEquals("bob0@anotherexample.com", evidenceDecoder.getPrincipal(evidence).getName());

        serviceName = Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("aggregateEvidenceDecoder");
        evidenceDecoder = (EvidenceDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(evidenceDecoder);
        Assert.assertEquals("bob0@anotherexample.com", evidenceDecoder.getPrincipal(evidence).getName());

        certificateChain = populateCertificateChain(false);
        evidence = new X509PeerCertificateChainEvidence(certificateChain);

        serviceName = Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("aggregateEvidenceDecoder");
        evidenceDecoder = (EvidenceDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(evidenceDecoder);
        Assert.assertEquals("CN=bob0", evidenceDecoder.getPrincipal(evidence).getName());

    }

    @Test
    public void testCustomEvidenceDecoder() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "CustomTestingDomain");

        X509Certificate[] certificateChain = populateCertificateChain(true);
        X509PeerCertificateChainEvidence evidence = new X509PeerCertificateChainEvidence(certificateChain);

        ServiceName serviceName = Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("customEvidenceDecoder");
        EvidenceDecoder evidenceDecoder = (EvidenceDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(evidenceDecoder);
        // custom evidence decoder just converts the subject name to upper case
        Assert.assertEquals("CN=BOB0", evidenceDecoder.getPrincipal(evidence).getName());
    }

    @Test
    public void testSourceAddressRoleDecoder() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomain");

        ServiceName serviceName = Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("ipRoleDecoder1");
        RoleDecoder roleDecoder = (RoleDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(roleDecoder);

        String sourceAddress = "10.12.14.16";
        Roles decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity(sourceAddress));
        assertTrue(decodedRoles.contains("admin"));
        assertTrue(decodedRoles.contains("user"));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.16.18")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity(null)));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("0:0:0:0:ffff:0:192.0.2.128")));
    }

    @Test
    public void testSourceAddressRoleDecoderWithIPv6() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainIPv6");

        ServiceName serviceName = Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("ipv6RoleDecoder");
        RoleDecoder roleDecoder = (RoleDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(roleDecoder);

        String sourceAddress = "2001:db8:85a3:0:0:8a2e:370:7334";
        Roles decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity(sourceAddress));
        assertTrue(decodedRoles.contains("admin"));
        assertTrue(decodedRoles.contains("user"));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("0:0:0:0:ffff:0:192.0.2.128")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.16.18")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity(null)));
    }

    @Test
    public void testSourceAddressRoleDecoderWithRegex() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainRegex");

        ServiceName serviceName = Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("regexRoleDecoder");
        RoleDecoder roleDecoder = (RoleDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(roleDecoder);

        HashSet<String> expectedRoles = new HashSet<>();
        expectedRoles.add("admin");
        expectedRoles.add("user");

        Roles decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.16"));
        assertTrue(decodedRoles.containsAll(expectedRoles));
        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.18"));
        assertTrue(decodedRoles.containsAll(expectedRoles));
        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.1"));
        assertTrue(decodedRoles.containsAll(expectedRoles));

        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("12.12.16.18")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.18.20")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("0:0:0:0:ffff:0:192.0.2.128")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity(null)));
    }

    @Test
    public void testSourceAddressRoleDecoderWithRegexIPv6() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainRegexIPv6");

        ServiceName serviceName = Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("ipv6RegexRoleDecoder");
        RoleDecoder roleDecoder = (RoleDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(roleDecoder);

        HashSet<String> expectedRoles = new HashSet<>();
        expectedRoles.add("admin");
        expectedRoles.add("user");

        Roles decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:7334"));
        assertTrue(decodedRoles.containsAll(expectedRoles));
        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:7335"));
        assertTrue(decodedRoles.containsAll(expectedRoles));
        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:7000"));
        assertTrue(decodedRoles.containsAll(expectedRoles));

        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("2222:db8:85a3:0:0:8a2e:370:7335")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:7335:0")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("12.12.16.18")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity(null)));
    }

    @Test
    public void testAggregateRoleDecoder() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomainAggregate");

        ServiceName serviceName = Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY.getCapabilityServiceName("aggregateRoleDecoder");
        RoleDecoder roleDecoder = (RoleDecoder) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(roleDecoder);

        Roles decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.16"));
        assertTrue(decodedRoles.contains("admin"));
        assertTrue(decodedRoles.contains("user"));
        assertFalse(decodedRoles.contains("employee"));
        assertTrue(decodedRoles.contains("internal"));

        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.18"));
        assertFalse(decodedRoles.contains("admin"));
        assertFalse(decodedRoles.contains("user"));
        assertTrue(decodedRoles.contains("employee"));
        assertTrue(decodedRoles.contains("internal"));

        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.12.14.20"));
        assertFalse(decodedRoles.contains("admin"));
        assertFalse(decodedRoles.contains("user"));
        assertFalse(decodedRoles.contains("employee"));
        assertTrue(decodedRoles.contains("internal"));

        decodedRoles = roleDecoder.decodeRoles(getAuthorizationIdentity("10.10.14.20"));
        assertFalse(decodedRoles.contains("admin"));
        assertFalse(decodedRoles.contains("user"));
        assertFalse(decodedRoles.contains("employee"));
        assertFalse(decodedRoles.contains("internal"));

        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("2001:db8:85a3:0:0:8a2e:370:")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity("12.12.16.18")));
        Assert.assertEquals(Roles.NONE, roleDecoder.decodeRoles(getAuthorizationIdentity(null)));
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

    private AuthorizationIdentity getAuthorizationIdentity(String sourceAddress) {
        if (sourceAddress == null) {
            return AuthorizationIdentity.basicIdentity(Attributes.EMPTY);
        } else {
            MapAttributes runtimeAttributes = new MapAttributes();
            runtimeAttributes.addFirst(KEY_SOURCE_ADDRESS, sourceAddress);
            return AuthorizationIdentity.basicIdentity(AuthorizationIdentity.EMPTY, runtimeAttributes);
        }
    }
}