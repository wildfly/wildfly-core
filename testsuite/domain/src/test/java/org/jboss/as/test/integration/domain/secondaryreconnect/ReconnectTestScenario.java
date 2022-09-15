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

package org.jboss.as.test.integration.domain.secondaryreconnect;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;

/**
 * To hook into the {@link SecondaryReconnectTestCase}, create an implementation of this class
 * to perform the required tests.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class ReconnectTestScenario {

    /**
     * Used to set up the domain for this test
     *
     * @param primaryClient
     * @param secondaryClient
     */
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {

    }

    /**
     * Clean up the domain after ourselves
     *  @param primaryClient
     * @param secondaryClient
     */
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {

    }

    /**
     * Tests on initial startup, before the primary is set into admin-only mode
     *
     * @param primaryClient
     * @param secondaryClient
     */
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {

    }

    /**
     * Tests while the primary is in admin only mode
     *
     * @param primaryClient
     * @param secondaryClient
     */
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {

    }

    /**
     * Tests once the primary is back in normal mode, and the secondary has reconnected
     *
     * @param primaryClient
     * @param secondaryClient
     */
    void testAfterReconnect(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {

    }

}
