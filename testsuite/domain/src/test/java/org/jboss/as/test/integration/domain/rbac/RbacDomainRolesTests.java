/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import java.io.IOException;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
interface RbacDomainRolesTests {
    void testMonitor() throws Exception;
    void testOperator() throws Exception;
    void testMaintainer() throws Exception;
    void testDeployer() throws Exception;
    void testAdministrator() throws Exception;
    void testAuditor() throws Exception;
    void testSuperUser() throws Exception;

    void tearDown() throws IOException;
}
