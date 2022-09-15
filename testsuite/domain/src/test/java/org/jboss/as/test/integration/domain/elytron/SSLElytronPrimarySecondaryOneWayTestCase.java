/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain.elytron;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.domain.AbstractSSLPrimarySecondaryTestCase;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.AfterClass;
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
        testSupport.close();
        testSupport = null;

        FileUtils.deleteDirectory(WORK_DIR);
    }

    @Test
    public void testReadSecondaryStatusFromPrimary() throws Exception {
        checkHostStatusOnPrimaryOverRemote("secondary", testSupport.getDomainPrimaryLifecycleUtil().getDomainClient());
    }
}
