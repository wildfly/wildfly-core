/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
