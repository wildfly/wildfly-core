/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class DeploymentOverlayTestCase {

    @Test
    public void testInvalidName() throws Exception {
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        ctx.connectController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                + ":" + TestSuiteEnvironment.getServerPort());
        try {
            expectException(ctx, "deployment-overlay remove --name=toto");
            expectException(ctx, "deployment-overlay list-content --name=toto");
            expectException(ctx, "deployment-overlay list-links --name=toto");
            expectException(ctx, "deployment-overlay redeploy-affected --name=toto");
            expectException(ctx, "deployment-overlay link --name=toto");
            expectException(ctx, "deployment-overlay upload --name=toto");

            ctx.handle("batch");
            expectException(ctx, "deployment-overlay list-content --name=toto");
            expectException(ctx, "deployment-overlay list-links --name=toto");

            File f = File.createTempFile("deploymentOverlay", null);
            f.createNewFile();
            f.deleteOnExit();
            ctx.handle("deployment-overlay link --name=toto --deployments=tutu");
            ctx.handle("deployment-overlay upload --name=toto --content=tutu="
                    + f.getAbsolutePath());
        } finally {
            ctx.terminateSession();
        }

    }
    private void expectException(CommandContext ctx, String cmd) throws Exception {
        try {
            ctx.handle(cmd);
            throw new Exception(cmd + " should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().contains("toto does not exist")) {
                throw new Exception("Unexpected exception " + ex);
            }
        }
    }
}
