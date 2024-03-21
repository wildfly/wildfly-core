/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.stability;

import org.jboss.as.version.Stability;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;

@ServerSetup(StabilityCommunityServerSetupTestCase.CommunityStabilitySetupTask.class)
@RunWith(WildFlyRunner.class)
public class StabilityCommunityServerSetupTestCase extends AbstractStabilityServerSetupTaskTest {
    public StabilityCommunityServerSetupTestCase() {
        super(Stability.COMMUNITY);
    }


    public static class CommunityStabilitySetupTask extends StabilityServerSetupSnapshotRestoreTasks.Community {
        @Override
        protected void doSetup(ManagementClient managementClient) throws Exception {
            // Not really needed since the resulting written xml will be of a higher stability level
            // than the server. Still we are doing it for experimental preview, so it doesn't hurt to
            // do the same here.
            AbstractStabilityServerSetupTaskTest.addSystemProperty(managementClient, StabilityCommunityServerSetupTestCase.class);
        }
    }

}
