/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.slavereconnect;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;

/**
 * To hook into the {@link SlaveReconnectTestCase}, create an implementation of this class
 * to perform the required tests.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class ReconnectTestScenario {

    /**
     * Used to set up the domain for this test
     *
     * @param masterClient
     * @param slaveClient
     */
    void setUpDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {

    }

    /**
     * Clean up the domain after ourselves
     *  @param masterClient
     * @param slaveClient
     */
    void tearDownDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {

    }

    /**
     * Tests on initial startup, before the master is set into admin-only mode
     *
     * @param masterClient
     * @param slaveClient
     */
    void testOnInitialStartup(DomainClient masterClient, DomainClient slaveClient) throws Exception {

    }

    /**
     * Tests while the master is in admin only mode
     *
     * @param masterClient
     * @param slaveClient
     */
    void testWhileMasterInAdminOnly(DomainClient masterClient, DomainClient slaveClient) throws Exception {

    }

    /**
     * Tests once the master is back in normal mode, and the slave has reconnected
     *
     * @param masterClient
     * @param slaveClient
     */
    void testAfterReconnect(DomainClient masterClient, DomainClient slaveClient) throws Exception {

    }

}
