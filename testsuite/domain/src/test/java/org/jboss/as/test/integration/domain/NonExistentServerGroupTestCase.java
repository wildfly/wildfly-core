/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author Peter Mackay
 */
public class NonExistentServerGroupTestCase {

    private static DomainTestSupport testSupport;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @After
    public void cleanUp() {
        if (testSupport != null) {
            testSupport.close();
        }
    }

    /**
     * Test that the host doesn't start when there are servers assigned to non-existent groups.
     */
    @Test
    public void testFailureOnBoot() {
        DomainTestSupport.Configuration configuration = getDomainConfiguration();
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Could not start container");
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
    }

    /**
     * Test that the host does start in admin-only mode even with servers assigned to non-existent groups.
     */
    @Test
    public void testBootAdminOnly() {
        DomainTestSupport.Configuration configuration = getDomainConfiguration();
        configuration.getMasterConfiguration().setAdminOnly(true);
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        Assert.assertTrue(testSupport.getDomainMasterLifecycleUtil().areServersStarted());
    }

    private static DomainTestSupport.Configuration getDomainConfiguration() {
        DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
            NonExistentServerGroupTestCase.class.getSimpleName(),
            "domain-configs/domain-minimal.xml",
            "host-configs/host-nonexistent-group.xml",
            null);
        return configuration;
    }

}
