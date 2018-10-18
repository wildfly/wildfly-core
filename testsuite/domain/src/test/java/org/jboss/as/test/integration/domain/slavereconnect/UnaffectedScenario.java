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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.SLAVE_ADDR;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.cloneProfile;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.createServer;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.createServerGroup;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.removeProfile;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.startServer;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.stopServer;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;

/**
 * Adds a profile, server-group and server which should not be affected by the other scenarios in the test
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class UnaffectedScenario extends ReconnectTestScenario {


    static final PathElement PROFILE = PathElement.pathElement(ModelDescriptionConstants.PROFILE, "unaffacted");
    static final PathElement GROUP = PathElement.pathElement(SERVER_GROUP, "group-unaffected");
    static final PathElement SERVER_CFG = PathElement.pathElement(SERVER_CONFIG, "server-unaffected");
    static final PathElement SERVER = PathElement.pathElement(ModelDescriptionConstants.SERVER, "server-unaffected");

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialized = 0;

    private final int portOffset;

    public UnaffectedScenario(int portOffset) {
        this.portOffset = portOffset;
    }

    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Add minimal server
        cloneProfile(masterClient, "minimal", PROFILE.getValue());
        initialized = 1;
        createServerGroup(masterClient, GROUP.getValue(), PROFILE.getValue());
        initialized = 2;
        createServer(slaveClient, SERVER.getValue(), GROUP.getValue(), portOffset);
        initialized = 3;
        startServer(slaveClient, SERVER.getValue());
        initialized = 4;
    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {
        if (initialized >= 4) {
            stopServer(slaveClient, SERVER.getValue());
        }
        if (initialized >= 3) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(SLAVE_ADDR.append(SERVER_CFG)), masterClient);
        }
        if (initialized >= 2) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(GROUP)), masterClient);
        }
        if (initialized >= 1) {
            removeProfile(masterClient, PROFILE.getValue());
        }
    }
}
