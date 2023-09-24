/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RemoveManagementRealmTestCase {

    private String removeLocalAuthCommand = "/core-service=management/security-realm=ManagementRealm/authentication=local:remove";

    @Inject
    private ServerController container;

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();
    private Path target;
    private Path source;

    @Before
    public void beforeTest() throws Exception {
        Assert.assertNotNull(container);
        container.start();
        String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        source = Paths.get(jbossDist, "standalone", "configuration", "standalone.xml");
        target = Paths.get(temporaryUserHome.getRoot().getAbsolutePath(), "standalone.xml");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        // Determine the command to use
        final ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("core-service", "management", "management-interface", "http-interface"));
        ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            result = Operations.readResult(result);
            if (result.hasDefined("http-upgrade")) {
                final ModelNode httpUpgrade = result.get("http-upgrade");
                if (httpUpgrade.hasDefined("sasl-authentication-factory")) {
                    // We could query this further to get the actual name of the configurable-sasl-server-factory. Since this
                    // is a test we're making some assumptions to limit the number of query calls made to the server.
                    removeLocalAuthCommand = "/subsystem=elytron/configurable-sasl-server-factory=configured:map-remove(name=properties, key=wildfly.sasl.local-user.default-user)";
                }
            }
        } else {
            fail(Operations.getFailureDescription(result).asString());
        }
    }

    @After
    public void afterTest() throws Exception {
        try {
            Assert.assertNotNull(container);
            container.stop();
        } finally {
            // source and target are only null if the container fails to start,
            // which is already visible in the logs
            if (source != null && target != null) {
                Files.copy(target, source, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Test
    public void testReload() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli.executeInteractive();
        cli.clearOutput();
        cli.pushLineAndWaitForResults(removeLocalAuthCommand);
        cmdAndCtrlC("reload", cli);
    }

    private void cmdAndCtrlC(String cmd, CliProcessWrapper cli) throws IOException {
        cli.clearOutput();
        boolean prompt = cli.pushLineAndWaitForResults(cmd, "Username:");
        assertTrue("Expected prompt not seen in output: " + cli.getOutput(), prompt);
        assertTrue("Process not terminated. Output is: " + cli.getOutput(), cli.ctrlCAndWaitForClose());
    }

    @Test
    public void testAnyCommand() throws Exception {
        CliProcessWrapper cli1 = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli1.executeInteractive();
        cli1.clearOutput();

        CliProcessWrapper cli2 = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli2.executeInteractive();
        cli2.clearOutput();

        cli1.pushLineAndWaitForResults(removeLocalAuthCommand);
        cmdAndCtrlC("reload", cli1);

        // Send ls from cli2.
        cmdAndCtrlC("ls", cli2);
    }
}
