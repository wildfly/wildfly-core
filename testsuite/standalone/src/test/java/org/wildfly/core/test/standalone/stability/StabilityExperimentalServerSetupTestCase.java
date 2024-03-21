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

@ServerSetup(StabilityExperimentalServerSetupTestCase.ExperimentalStabilitySetupTask.class)
@RunWith(WildFlyRunner.class)
public class StabilityExperimentalServerSetupTestCase extends AbstractStabilityServerSetupTaskTest {
    public StabilityExperimentalServerSetupTestCase() {
        super(Stability.EXPERIMENTAL);
    }


    public static class ExperimentalStabilitySetupTask extends StabilityServerSetupSnapshotRestoreTasks.Experimental {
        @Override
        protected void doSetup(ManagementClient managementClient) throws Exception {
            // Write a system property so the model ges stored with a lower stability level.
            // This is to make sure we can reload back to the higher level from the snapshot
            AbstractStabilityServerSetupTaskTest.addSystemProperty(managementClient, StabilityExperimentalServerSetupTestCase.class);
        }
    }

}
