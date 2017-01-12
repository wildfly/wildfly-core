/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
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
