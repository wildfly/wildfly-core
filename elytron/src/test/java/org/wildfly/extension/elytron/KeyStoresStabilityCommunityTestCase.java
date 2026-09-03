/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class KeyStoresStabilityCommunityTestCase extends KeyStoresTestCase {

    public KeyStoresStabilityCommunityTestCase() {
    }

    @Before
    public void init() throws Exception {
        String subsystemXml;
        if (JdkUtils.isIbmJdk()) {
            subsystemXml = "tls-ibm.xml";
        } else {
            subsystemXml = JdkUtils.getJavaSpecVersion() <= 12 ? "tls-sun.xml" : "tls-oracle13plus.xml";
        }
        super.services = super.createKernelServicesBuilder(new TestEnvironment(Stability.COMMUNITY)).setSubsystemXmlResource(subsystemXml).build();
        if (!super.services.isSuccessfulBoot()) {
            if (super.services.getBootError() != null) {
                Assert.fail(super.services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testExpirationOnOldCertificate() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(30).plusMinutes(1);
        //cert chain, with Elytron self signed as trailing
        ModelNode result = readCertificateValidity(notValidBeforeDate, notValidAfterDate);
        ModelNode chain = result.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN);
        assertEquals((CertificateValidity.VALID.toString()), chain.get(0).get(ElytronDescriptionConstants.VALIDITY).asString());
    }

    @Test
    public void testExpirationOnAboutToExpireCertificate() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC")).minusDays(50);
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(56).plusMinutes(1);
        //cert chain, with Elytron self signed as trailing
        ModelNode result = readCertificateValidity(notValidBeforeDate, notValidAfterDate);
        ModelNode chain = result.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN);
        assertEquals((CertificateValidity.ABOUT_TO_EXPIRE.toString()), chain.get(0).get(ElytronDescriptionConstants.VALIDITY).asString());
    }

    @Test
    public void testExpirationOnHealthyCertificate() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.of(2018, 03, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        final ZonedDateTime notValidAfterDate = ZonedDateTime.of(2018, 04, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        //cert chain, with Elytron self signed as trailing
        ModelNode result = readCertificateValidity(notValidBeforeDate, notValidAfterDate);
        ModelNode chain = result.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN);
        assertEquals((CertificateValidity.EXPIRED.toString()), chain.get(0).get(ElytronDescriptionConstants.VALIDITY).asString());
    }

    private ModelNode readCertificateValidity(ZonedDateTime notValidBeforeDate, ZonedDateTime notValidAfterDate) throws Exception {
        final String alias = "expiry";
        final ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        return performOperationOnGeneratedKeyStore(notValidBeforeDate, notValidAfterDate, operation);
    }
}