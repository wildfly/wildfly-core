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

@ServerSetup(StabilityPreviewServerSetupTestCase.PreviewStabilitySetupTask.class)
@RunWith(WildFlyRunner.class)
public class StabilityPreviewServerSetupTestCase extends AbstractStabilityServerSetupTaskTest {
    public StabilityPreviewServerSetupTestCase() {
        super(Stability.PREVIEW);
    }


    public static class PreviewStabilitySetupTask extends StabilityServerSetupSnapshotRestoreTasks.Preview {
        @Override
        protected void doSetup(ManagementClient managementClient) throws Exception {
            // Write a system property so the model ges stored with a lower stability level.
            // This is to make sure we can reload back to the higher level from the snapshot
            AbstractStabilityServerSetupTaskTest.addSystemProperty(managementClient, StabilityPreviewServerSetupTestCase.class);
        }
    }

}
