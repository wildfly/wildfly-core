/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
        configuration.getPrimaryConfiguration().setAdminOnly(true);
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        Assert.assertTrue(testSupport.getDomainPrimaryLifecycleUtil().areServersStarted());
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
