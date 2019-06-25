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

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.asn1.ASN1Encodable;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.evidence.X509PeerCertificateChainEvidence;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.X500AttributeTypeAndValue;
import org.wildfly.security.x500.X500PrincipalBuilder;
import org.wildfly.security.x500.cert.SubjectAlternativeNamesExtension;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.security.auth.x500.X500Principal;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class MappersTestCase extends AbstractSubsystemBaseTest {
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
            Assert.fail(services.getBootError().toString());
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestingDomain");
        ServiceName serviceName = Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.getCapabilityServiceName("tree");
        PrincipalTransformer transformer = (PrincipalTransformer) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(transformer);

        Assert.assertEquals("alpha@jboss.org", transformer.apply(new NamePrincipal("alpha@jboss.com")).getName()); // com to org
        Assert.assertEquals("beta", transformer.apply(new NamePrincipal("beta@wildfly.org")).getName()); // remove server part
        Assert.assertEquals("gamma@example.com", transformer.apply(new NamePrincipal("gamma@example.com")).getName()); // keep
        Assert.assertEquals(null, transformer.apply(new NamePrincipal("invalid"))); // not an e-mail address
        Assert.assertEquals(null, transformer.apply(null));
    }

    @Test
    public void testEvidenceDecoder() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
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
            Assert.fail(services.getBootError().toString());
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
}
