/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.elytron;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.domain.AbstractSSLPrimarySecondaryTestCase;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests one way SSL secured communication between primary and secondary, using Elytron.
 *
 * @author Richard Janík <rjanik@redhat.com>
 */
public class SSLElytronPrimarySecondaryOneWayTestCase extends AbstractSSLPrimarySecondaryTestCase {

    private static final File WORK_DIR = new File("target" + File.separatorChar +  "ssl-primary-secondary-1way-workdir-elytron");
    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void setupDomain() throws Exception {
        keyMaterialSetup(WORK_DIR);

        DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
                SSLElytronPrimarySecondaryOneWayTestCase.class.getSimpleName(), "domain-configs/domain-standard.xml",
                "host-configs/host-primary-ssl-1way-elytron.xml", "host-configs/host-secondary-ssl-1way-elytron.xml");

        testSupport = DomainTestSupport.createAndStartSupport(configuration);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            Assert.assertNotNull(testSupport);
            testSupport.close();
        } finally {
            testSupport = null;
            FileUtils.deleteDirectory(WORK_DIR);
        }
    }

    @Test
    public void testReadSecondaryStatusFromPrimary() throws Exception {
        checkHostStatusOnPrimaryOverRemote("secondary", testSupport.getDomainPrimaryLifecycleUtil().getDomainClient());
    }
}
