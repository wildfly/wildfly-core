/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Starts WildFly before running the tests.
 *
 * @author <a href="mailto:lthon@redhat.com">Ladislav Thon</a>
 */
public class PropertiesRoleMappingTestCase extends AbstractPropertiesRoleMappingTestCase {

    @BeforeClass
    public static void setUp() throws Exception {
        startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        stopServer();
    }
}
