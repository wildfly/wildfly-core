/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.management.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for embedded host-controller in the CLI.
 *
 * @author Ken Wills (c) 2016 Red Hat Inc.
 */
public class CLIEmbedHostControllerTestCase extends AbstractCliTestBase {

    /**
     * Use this if you are debugging and want to look at the original System.out output,
     * as the tests play tricks with it later.
     */
    private static final PrintStream out = System.out;
    private static final File ROOT = new File(System.getProperty("jboss.home"));
    private static final String JBOSS_HOME = " --jboss-home=" + ROOT.getAbsolutePath();
    private static final String STOP = "stop-embedded-host-controller";
    private static final String SERVICE_ACTIVATOR_DEPLOYMENT_NAME = "service-activated.jar";
    private static JavaArchive serviceActivatorDeployment;
    private static File serviceActivatorDeploymentFile;
    private static boolean uninstallStdio;

    public static final String JBOSS_DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    public static final String JBOSS_DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    public static final String JBOSS_DOMAIN_CONTENT_DIR = "jboss.domain.content.dir";
    public static final String JBOSS_DOMAIN_DEPLOY_DIR = "jboss.domain.deploy.dir";
    public static final String JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    public static final String JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";
    public static final String JBOSS_DOMAIN_DATA_DIR = "jboss.domain.data.dir";

    private static final String[] DOMAIN_PROPS = {
            JBOSS_DOMAIN_BASE_DIR, JBOSS_DOMAIN_CONFIG_DIR, JBOSS_DOMAIN_CONTENT_DIR, JBOSS_DOMAIN_DEPLOY_DIR,
            JBOSS_DOMAIN_TEMP_DIR, JBOSS_DOMAIN_LOG_DIR, JBOSS_DOMAIN_DATA_DIR
    };
    // Sink for embedding app (i.e. this class and the CLI) and embedded HC writes to stdout
    private ByteArrayOutputStream logOut;

    private static StdioContext initialStdioContext;

    @BeforeClass
    public static void beforeClass() throws Exception {

        CLIEmbedUtil.copyConfig(ROOT, "domain", "logging.properties", "logging.properties.backup", false);

        // Set up ability to manipulate stdout
        initialStdioContext = StdioContext.getStdioContext();
        try {
            StdioContext.install();
            uninstallStdio = true;
        } catch (IllegalStateException ignore) {
            //
        }

        serviceActivatorDeployment = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(SERVICE_ACTIVATOR_DEPLOYMENT_NAME, Collections.emptyMap());

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        serviceActivatorDeploymentFile = new File(tmpDir, SERVICE_ACTIVATOR_DEPLOYMENT_NAME);
        serviceActivatorDeployment.as(ZipExporter.class).exportTo(serviceActivatorDeploymentFile, true);

        ExtensionUtils.createExtensionModule(BlockerExtension.MODULE_NAME, BlockerExtension.class, EmptySubsystemParser.class.getPackage());

        // Silly assertion just to stop IDEs complaining about field 'out' not being used.
        assertNotNull(out);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        CLIEmbedUtil.copyConfig(ROOT, "domain", "logging.properties.backup", "logging.properties", false);
        try {
            StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(initialStdioContext));
        } finally {
            if (uninstallStdio) {
                StdioContext.uninstall();
            }
        }
        ExtensionUtils.deleteExtensionModule(BlockerExtension.MODULE_NAME);
    }

    @Before
    public void setup() throws Exception {
        CLIEmbedUtil.copyConfig(ROOT, "domain", "logging.properties.backup", "logging.properties", false);

        CLIEmbedUtil.copyConfig(ROOT, "domain", "host.xml", "host-cli.xml", true);
        CLIEmbedUtil.copyConfig(ROOT, "domain", "domain.xml", "domain-cli.xml", true);
        // Capture stdout
        logOut = new ByteArrayOutputStream();
        final StdioContext replacement = StdioContext.create(initialStdioContext.getIn(), logOut, initialStdioContext.getErr());
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(replacement));

        initCLI(false);
    }

    @After
    public void cleanup() throws Exception {
        try {
            closeCLI();
        } finally {
            StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(initialStdioContext));
        }
    }

    /** Tests logging behavior with no --std-out param */
    @Test
    public void testStdOutDefault() throws Exception {
        stdoutTest(null);
    }

    /** Tests logging behavior with --std-out=discard */
    @Test
    public void testStdOutDiscard() throws Exception {
        stdoutTest("discard");
    }

    /** Tests logging behavior with --std-out=echo */
    @Test
    public void testStdOutEcho() throws Exception {
        stdoutTest("echo");
    }

    /**
     * This test is about confirming the CLI side and embedded server side logging to stdout
     * works as expected. CLI logging should always be captured in the 'logOut' sink, while
     * embedded server logging should only be captured if paramVal is 'echo'.
     *
     * @param paramVal value to assign to the --std-out param, or null if the param should be omitted
     * @throws Exception just because
     */
    private void stdoutTest(String paramVal) throws Exception {
        String stdoutParam = paramVal == null ? "" : ("--std-out=" + paramVal);
        boolean expectServerLogging = "echo".equals(paramVal);

        // The app embedding the server should be able to log
        checkClientSideLogging();

        // Use  or the logging subsystem won't log anyway
        String line = "embed-host-controller --host-config=host-cli.xml --domain-config=domain-cli.xml " + stdoutParam + JBOSS_HOME;
        cli.sendLine(line);
        if (expectServerLogging) {
            checkLogging("WFLYSRV0025");
        } else {
            checkNoLogging("WFLYSRV0025");
        }
        assertState("running", TimeoutUtil.adjust(30000));

        // The app embedding the server should still be able to log
        checkClientSideLogging();

        // Do something that certainly creates a server-side logger after boot,
        // as part of executing a management op. Confirm its logging gets the
        // appropriate server-side behavior.
        cli.sendLine("/extension=" + BlockerExtension.MODULE_NAME + ":add");
        if (expectServerLogging) {
            checkLogging(BlockerExtension.REGISTERED_MESSAGE);
        } else {
            checkNoLogging(BlockerExtension.REGISTERED_MESSAGE);
        }

        // The app embedding the server should still be able to log
        checkClientSideLogging();

        cli.sendLine(STOP);
        if (expectServerLogging) {
            checkLogging("WFLYSRV0050");
        } else {
            checkNoLogging("WFLYSRV0050");
        }

        // The app embedding the server should still be able to log
        checkClientSideLogging();

    }

    private void checkClientSideLogging() throws IOException {
        String text = "test." + System.nanoTime();
        Logger.getLogger(text).error(text);
        checkLogging(text);
    }

    /** Confirms that low level and high level reloads work */
    @Test
    public void testReload() throws Exception {
        // since this is admin-only always for now, just add/remove an extension,
        // and make sure it works.
        String line = "embed-host-controller  --host-config=host-cli.xml --domain-config=domain-cli.xml  " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/extension=org.wildfly.extension.io:add");
        assertState("running", 0);
        // embedded-hc requires admin-only
        cli.sendLine("/host=master:reload(admin-only=true");
        assertState("running", TimeoutUtil.adjust(30000));
        cli.sendLine("/extension=org.wildfly.extension.io:remove");
        assertState("running", 0);
        // High level
        cli.sendLine("reload --host=master --admin-only=true");
        assertState("running", TimeoutUtil.adjust(30000));
    }

    /** Confirms that the low and high level shutdown commands are not available */
    @Test
    public void testShutdownNotAvailable() throws Exception {

        String line = "embed-host-controller  --host-config=host-cli.xml --domain-config=domain-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        assertState("running", 0);
        cli.sendLine(":shutdown", true);
        assertState("running", 0);
        cli.sendLine("shutdown", true);
        assertState("running", 0);
    }

    @Test
    public void testTimeout() throws Exception {
        cli.sendLine("command-timeout set 60");
        String line = "embed-host-controller  --host-config=host-cli.xml --domain-config=domain-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("stop-embedded-host-controller");
    }

    /**
     * Test not specifying a server config works.
     * @throws IOException
     */
    @Test
    public void testDefaultServerConfig() throws IOException {
        validateServerConnectivity("");
    }

    /**
     * Test the -c shorthand for specifying a domain config works.
     * @throws IOException
     */
    @Test
    public void testDashC() throws IOException {
        validateServerConnectivity("-c=domain-cli.xml");
    }

    /** Tests the stop-embedded-host-controller command */
    @Test
    public void testStopEmbeddedServer() throws IOException {
        validateServerConnectivity();
        cli.sendLine("stop-embedded-host-controller");
        assertFalse(cli.isConnected());
    }

    /** Tests that the quit command stops any embedded server */
    @Test
    public void testStopServerOnQuit() throws IOException {
        validateServerConnectivity();
        cli.sendLine("quit");
        assertFalse(cli.isConnected());
    }

    /** Tests the the CommandContext terminateSession method stops any embedded server */
    @Test
    public void testStopServerOnTerminateSession() throws IOException {
        validateServerConnectivity();
        cli.getCommandContext().terminateSession();
        assertFalse(cli.isConnected());
    }

    /**
     * Tests the --help param works.
     * @throws IOException
     */
    @Test
    public void testHelp() throws IOException {
        cli.sendLine("embed-host-controller --help");
        checkLogging("embed-host-controller");

        String line = "embed-host-controller --host-config=host-cli.xml --domain-config=domain-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());

        cli.sendLine("stop-embedded-host-controller --help");
        checkLogging("stop-embedded-host-controller");

    }

    @Test
    public void testBaseDir() throws IOException, InterruptedException {
        String currBaseDir = null;
        final String newDomain = "CLIEmbedServerTestCaseHostControllerTmp";

        assertFalse(cli.isConnected());

        try {
            // save the current value, if it has one
            currBaseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_DOMAIN_BASE_DIR, null);

            CLIEmbedUtil.copyServerBaseDir(ROOT, "domain", newDomain, true);
            CLIEmbedUtil.copyConfig(ROOT, newDomain, "logging.properties.backup", "logging.properties", false);

            String newBaseDir = ROOT + File.separator + newDomain;

            setProperties(newBaseDir);
            final String line = "embed-host-controller --host-config=host-cli.xml --domain-config=domain-cli.xml --std-out=echo " + JBOSS_HOME;
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            cli.sendLine("/system-property=" + newDomain + ":add(value=" + newDomain +")");
            assertProperty(newDomain, newDomain, false);
            // verify we've logged to the right spot at least, and directories were created
            File f = new File(ROOT + File.separator + newDomain + File.separator + "log" + File.separator + "host-controller.log");
            // WFCORE-1187
            //assertTrue(f.exists());
            //assertTrue(f.length() > 0);

            // stop the hc, and restart it with default properties
            cli.sendLine("stop-embedded-host-controller");

            setProperties(null);
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            // shouldn't be set
            assertProperty(newDomain, null, true);
            cli.sendLine("stop-embedded-host-controller");

            setProperties(newBaseDir);
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            // shouldn't be set
            assertProperty(newDomain, newDomain, false);
        } finally {
            cli.sendLine("stop-embedded-host-controller");
            // clean up copy
            FileUtils.deleteDirectory(ROOT + File.separator + newDomain);
            // restore the originals
            setProperties(currBaseDir);
        }
    }

    @Test
    public void testLogDir() throws IOException, InterruptedException {
        String currBaseDir = null;
        final String newDomain = "CLIEmbedServerTestCaseHostControllerTmp";

        assertFalse(cli.isConnected());
        File newLogDir = null;
        try {
            // save the current value, if it has one
            currBaseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_DOMAIN_BASE_DIR, null);
            if (currBaseDir == null) {
                currBaseDir = ROOT + File.separator + "domain";
            }
            CLIEmbedUtil.copyServerBaseDir(ROOT, "domain", newDomain, true);
            newLogDir = new File(ROOT + File.separator + newDomain, "newlog");
            if (newLogDir.exists()) {
                FileUtils.deleteDirectory(newLogDir);
            }
            assertTrue(newLogDir.mkdir());
            // only set log.dir
            WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_LOG_DIR, ROOT + File.separator + newDomain + File.separator + "newlog");

            String line = "embed-host-controller --host-config=host-cli.xml --domain-config=domain-cli.xml --std-out=echo " + JBOSS_HOME;
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            // verify we've logged to the right spot and directories were created
            File f = new File(ROOT + File.separator + newDomain + File.separator + "newlog" + File.separator + "host-controller.log");
            // WFCORE-1187, when this overrides logging.properties correctly, we can check this
            //assertTrue(f.exists());
            //assertTrue(f.length() > 0);
        } finally {
            cli.sendLine("stop-embedded-host-controller");
            // clean up copy
            FileUtils.deleteDirectory(ROOT + File.separator + newDomain);
            // restore the originals
            setProperties(currBaseDir);
        }
    }

    private void assertProperty(final String propertyName, final String expected, final boolean notPresent) throws IOException, InterruptedException {
        cli.sendLine("/system-property=" + propertyName + " :read-attribute(name=value)", true);
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode resp = result.getResponseNode();
        ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
        if (notPresent) {
            assertTrue(stateNode.asString().indexOf("WFLYCTL0216") != -1);
        } else {
            assertEquals(expected, stateNode.asString());
        }
    }

    private void assertState(String expected, int timeout) throws IOException, InterruptedException {
        long done = timeout < 1 ? 0 : System.currentTimeMillis() + timeout;
        String history = "";
        String state = null;
        do {
            try {
                cli.sendLine("/host=master:read-attribute(name=host-state)", true);
                CLIOpResult result = cli.readAllAsOpResult();
                ModelNode resp = result.getResponseNode();
                ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
                state = stateNode.asString();
                history += state+"\n";
            } catch (Exception ignored) {
                //
                history += ignored.toString()+ "--" + cli.readOutput() + "\n";
            }
            if (expected.equals(state)) {
                return;
            } else {
                Thread.sleep(20);
            }
        } while (timeout > 0 && System.currentTimeMillis() < done);
        assertEquals(history, expected, state);
    }

    private void checkNoLogging(String line) throws IOException {
        String output = readLogOut();
        assertFalse(output, checkLogging(output, line));
    }

    private String readLogOut() {
        if (logOut.size() > 0) {
            String output = new String(logOut.toByteArray()).trim();
            logOut.reset();
            return output;
        }
        return null;
    }

    private void checkLogging(String line) throws IOException {
        String logOutput = readLogOut();
        assertTrue(logOutput, checkLogging(logOutput, line));
    }

    private boolean checkLogging(String logOutput, String line) throws IOException {
        List<String> output = CLIEmbedUtil.getOutputLines(logOutput);
        for (String s : output) {
            if (s.contains(line)) {
                return true;
            }
        }
        return false;
    }

    private void validateServerConnectivity() throws IOException {
        validateServerConnectivity("--host-config=host-cli.xml --domain-config=domain-cli.xml");
    }

    private void validateServerConnectivity(String serverConfigParam) throws IOException {
        // does basically nothing yet, other than accepts params, and starts a server.
        String line = "embed-host-controller  " + serverConfigParam + " " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
    }

    private void setProperties(final String newBaseDir) {
        if (newBaseDir == null) {
            for (String prop : DOMAIN_PROPS) {
                WildFlySecurityManager.clearPropertyPrivileged(prop);
            }
            return;
        }
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_BASE_DIR, newBaseDir);
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_CONFIG_DIR, newBaseDir + File.separator + "configuration");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_DATA_DIR, newBaseDir + File.separator + "data");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_LOG_DIR, newBaseDir + File.separator + "log");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_DOMAIN_TEMP_DIR, newBaseDir + File.separator + "tmp");
    }

}
