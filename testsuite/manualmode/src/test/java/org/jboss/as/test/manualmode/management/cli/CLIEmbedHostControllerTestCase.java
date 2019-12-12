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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.integration.management.extension.optypes.OpTypesExtension;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

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
    private static boolean uninstallStdio;

    private static final String JBOSS_DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String JBOSS_DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String JBOSS_DOMAIN_CONTENT_DIR = "jboss.domain.content.dir";
    private static final String JBOSS_DOMAIN_DEPLOY_DIR = "jboss.domain.deploy.dir";
    private static final String JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";
    private static final String JBOSS_DOMAIN_DATA_DIR = "jboss.domain.data.dir";

    private static final String[] DOMAIN_PROPS = {
            JBOSS_DOMAIN_BASE_DIR, JBOSS_DOMAIN_CONFIG_DIR, JBOSS_DOMAIN_CONTENT_DIR, JBOSS_DOMAIN_DEPLOY_DIR,
            JBOSS_DOMAIN_TEMP_DIR, JBOSS_DOMAIN_LOG_DIR, JBOSS_DOMAIN_DATA_DIR
    };
    // Sink for embedding app (i.e. this class and the CLI) and embedded HC writes to stdout
    private ByteArrayOutputStream logOut;

    private static StdioContext initialStdioContext;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Assume.assumeFalse("This test does not work with the IBM J9 JVM. There seems to be an issue with stdout" +
                " logging.", TestSuiteEnvironment.isIbmJvm());

        CLIEmbedUtil.copyConfig(ROOT, "domain", "logging.properties", "logging.properties.backup", false);

        // Set up ability to manipulate stdout
        initialStdioContext = StdioContext.getStdioContext();
        try {
            StdioContext.install();
            uninstallStdio = true;
        } catch (IllegalStateException ignore) {
            //
        }

        ExtensionUtils.createExtensionModule(BlockerExtension.MODULE_NAME, BlockerExtension.class, EmptySubsystemParser.class.getPackage());
        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class, EmptySubsystemParser.class.getPackage());

        // Silly assertion just to stop IDEs complaining about field 'out' not being used.
        assertNotNull(out);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        if (!TestSuiteEnvironment.isIbmJvm()) {
            CLIEmbedUtil.copyConfig(ROOT, "domain", "logging.properties.backup", "logging.properties", false);
            try {
                StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(initialStdioContext));
            } finally {
                if (uninstallStdio) {
                    StdioContext.uninstall();
                }
            }
            ExtensionUtils.deleteExtensionModule(BlockerExtension.MODULE_NAME);
            ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);
        }
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
        CLIEmbedUtil.delConfigFile(ROOT, "domain", "host-empty-cli.xml");
        CLIEmbedUtil.delConfigFile(ROOT, "domain", "domain-empty-cli.xml");
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
     */
    @Test
    public void testDefaultServerConfig() throws IOException {
        validateServerConnectivity("");
    }

    /**
     * Test the -c shorthand for specifying a domain config works.
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
            // WFCORE-1187
            //File f = new File(ROOT + File.separator + newDomain + File.separator + "log" + File.separator + "host-controller.log");
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
        File newLogDir;
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
            // WFCORE-1187, when this overrides logging.properties correctly, we can check this
            //File f = new File(ROOT + File.separator + newDomain + File.separator + "newlog" + File.separator + "host-controller.log");
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

    @Test
    public void testRBACEnabled() throws IOException, InterruptedException {
        String line = "embed-host-controller  --host-config=host-cli.xml --domain-config=domain-cli.xml  " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/core-service=management/access=authorization:write-attribute(name=provider,value=rbac");
        assertState("reload-required", 0);
        cli.sendLine("reload --host=master --admin-only=true");
        assertState("running", TimeoutUtil.adjust(30000));
    }

    @Test
    public void testPrivateHiddenOperations() throws IOException {
        validateServerConnectivity();

        try {
            // Setup
            cli.sendLine("/extension=" + OpTypesExtension.EXTENSION_NAME +":add", true);
            cli.readAllAsOpResult();
            cli.sendLine("/profile=default/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":add", true);
            cli.readAllAsOpResult();
            cli.sendLine("/host=master/extension=" + OpTypesExtension.EXTENSION_NAME +":add", true);
            cli.readAllAsOpResult();
            cli.sendLine("/host=master/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":add", true);
            cli.readAllAsOpResult();

            cli.sendLine("/profile=default/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":hidden", true);
            CLIOpResult result = cli.readAllAsOpResult();
            assertTrue(result.getResponseNode().toString(), result.isIsOutcomeSuccess());
            cli.sendLine("/profile=default/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":private", true);
            result = cli.readAllAsOpResult();
            assertFalse(result.getResponseNode().toString(), result.isIsOutcomeSuccess());

            cli.sendLine("/host=master/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":hidden", true);
            result = cli.readAllAsOpResult();
            assertTrue(result.getResponseNode().toString(), result.isIsOutcomeSuccess());
            cli.sendLine("/host=master/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":private", true);
            result = cli.readAllAsOpResult();
            assertFalse(result.getResponseNode().toString(), result.isIsOutcomeSuccess());
        } finally {
            try {
                cli.sendLine("/profile=default/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":remove", true);
                cli.readAllAsOpResult();
            } finally {
                try {
                    cli.sendLine("/extension=" + OpTypesExtension.EXTENSION_NAME + ":remove", true);
                    cli.readAllAsOpResult();
                } finally {
                    try {
                        cli.sendLine("/host=master/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":remove", true);
                        cli.readAllAsOpResult();
                    } finally {
                        cli.sendLine("/host=master/extension=" + OpTypesExtension.EXTENSION_NAME + ":remove", true);
                        cli.readAllAsOpResult();

                    }
                }
            }
        }
    }

    @Test
    public void testEmptyHostConfigDefaultExists() throws Exception {
        String line = "embed-host-controller --std-out=echo --host-config=host-cli.xml --domain-config=domain-cli.xml --empty-host-config" + JBOSS_HOME;
        cli.sendLine(line, true);
        String result = cli.readOutput();
        // should fail, host-cli.xml exists
        assertTrue(result.contains("The specified host configuration file already exists"));
    }

    @Test
    public void testEmptyDomainConfigDefaultExists() throws Exception {
        String line = "embed-host-controller --std-out=echo --host-config=host-cli.xml --domain-config=domain-cli.xml --empty-domain-config" + JBOSS_HOME;
        cli.sendLine(line, true);
        String result = cli.readOutput();
        // should fail, domain-cli.xml exists
        assertTrue(result.contains("The specified domain configuration file already exists"));
    }

    @Test
    public void testEmptyHostConfig() throws Exception {
        String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config" + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("ls /host");
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode mn = result.getResponseNode();
        assertNull(mn);

        cli.sendLine("ls /host=foo", true);
        final String output = cli.readOutput();
        assertTrue(output.startsWith("WFLYDC0033:"));
        assertTrue(cli.isConnected());

        cli.sendLine(":read-children-names(child-type=host)");
        result = cli.readAllAsOpResult();
        mn = result.getResponseNode();
        assertEquals(SUCCESS, mn.get(OUTCOME).asString());
        assertEquals(0, mn.get(RESULT).asList().size());
        assertTrue(cli.isConnected());
    }

    @Test
    public void testEmptyDomainConfig() throws Exception {
        String line = "embed-host-controller --std-out=echo --host-config=host-cli.xml --domain-config=domain-empty-cli.xml --empty-domain-config" + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
    }

    @Test
    public void testEmptyHostConfigConfigureWithReload() throws Exception {
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/host=foo:add()");
        assertTrue(cli.isConnected());
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=foo/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=foo/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");
        cli.sendLine("reload --host=foo --admin-only=true");
        assertTrue(cli.isConnected());
        cli.sendLine("ls /host=foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=foo/core-service=management/management-interface=http-interface");
        cli.sendLine(":read-attribute(name=local-host-name)");
        final CLIOpResult result = cli.readAllAsOpResult();
        final ModelNode response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("foo", response.get(RESULT).asString());
    }

    @Test
    public void testEmptyHostConfigConfigureWithReloadAndHostRename() throws Exception {
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/host=foo:add()");
        assertTrue(cli.isConnected());

        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=foo/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=foo/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");
        cli.sendLine("/host=foo:write-attribute(name=name,value=renamed-foo)");
        cli.sendLine("/host=foo:reload(admin-only=true)");
        assertState("running", TimeoutUtil.adjust(30000), "/host=renamed-foo:read-attribute(name=host-state)");

        assertTrue(cli.isConnected());

        cli.sendLine("ls /host=renamed-foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=renamed-foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=renamed-foo/core-service=management/management-interface=http-interface");
        cli.sendLine(":read-attribute(name=local-host-name)");

        final CLIOpResult result = cli.readAllAsOpResult();
        final ModelNode response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("renamed-foo", response.get(RESULT).asString());
    }

    @Test
    public void testEmptyHostConfigConfigureWithReloadNamePersist() throws Exception {
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/host=foo:add(persist-name=true)");
        assertTrue(cli.isConnected());
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=foo/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=foo/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");
        cli.sendLine("reload --host=foo --admin-only=true");
        assertTrue(cli.isConnected());
        cli.sendLine("ls /host=foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=foo/core-service=management/management-interface=http-interface");

        cli.sendLine("stop-embedded-host-controller");
        cli.sendLine("embed-host-controller --std-out=echo --host-config=host-empty-cli.xml " + JBOSS_HOME);
        assertTrue(cli.isConnected());
        cli.sendLine(":read-children-names(child-type=host)");
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("foo", response.get(RESULT).asList().get(0).asString());
        cli.sendLine("ls /host=foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=foo/core-service=management/management-interface=http-interface");
        assertTrue(cli.isConnected());

        cli.sendLine(":read-attribute(name=local-host-name)");
        result = cli.readAllAsOpResult();
        response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("foo", response.get(RESULT).asString());
    }

    @Test
    public void testEmptyHostConfigConfigureWithReloadNamePersistAndHostRename() throws Exception {
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/host=foo:add(persist-name=true)");
        assertTrue(cli.isConnected());
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=foo/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=foo/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=foo/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");

        cli.sendLine("/host=foo:write-attribute(name=name,value=renamed-foo)");
        cli.sendLine("/host=foo:reload(admin-only=true)");
        assertState("running", TimeoutUtil.adjust(30000), "/host=renamed-foo:read-attribute(name=host-state)");

        cli.sendLine("ls /host=renamed-foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=renamed-foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=renamed-foo/core-service=management/management-interface=http-interface");

        cli.sendLine("stop-embedded-host-controller");
        cli.sendLine("embed-host-controller --std-out=echo --host-config=host-empty-cli.xml " + JBOSS_HOME);
        assertTrue(cli.isConnected());
        cli.sendLine(":read-children-names(child-type=host)");
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("renamed-foo", response.get(RESULT).asList().get(0).asString());
        cli.sendLine("ls /host=renamed-foo/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=renamed-foo/interface=test-mgmt-interface");
        cli.sendLine("ls /host=renamed-foo/core-service=management/management-interface=http-interface");
        assertTrue(cli.isConnected());

        cli.sendLine(":read-attribute(name=local-host-name)");
        result = cli.readAllAsOpResult();
        response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals("renamed-foo", response.get(RESULT).asString());
    }

    @Test
    public void testEmptyHostConfigConfigureWithReloadNoNamePersist() throws Exception {
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/host=foo33f:add(persist-name=false)");
        assertTrue(cli.isConnected());
        cli.sendLine("/host=foo33f/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=foo33f/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=foo33f/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=foo33f/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");
        cli.sendLine("reload --host=foo33f --admin-only=true");
        // should still be foo after reload
        assertTrue(cli.isConnected());
        cli.sendLine("ls /host=foo33f/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=foo33f/interface=test-mgmt-interface");
        cli.sendLine("ls /host=foo33f/core-service=management/management-interface=http-interface");

        cli.sendLine("stop-embedded-host-controller");
        // should be default derived local host name after stop and start
        cli.sendLine("embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --domain-config=domain-cli.xml " + JBOSS_HOME);
        assertTrue(cli.isConnected());
        cli.sendLine(":read-children-names(child-type=host)");
        final CLIOpResult result = cli.readAllAsOpResult();
        final ModelNode response = result.getResponseNode();
        assertTrue(response.has(OUTCOME));
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.has(RESULT));
        // whatever the unpersisted hostname is, it shouldn't be the one we added.
        assertNotEquals("foo33f", response.get(RESULT).asList().get(0).asString());
        assertNotEquals(0, response.get(RESULT).asList().size());
        assertTrue(cli.isConnected());
    }

    @Test
    public void testEmptyDomainConfigConfigureWithReload() throws Exception {
        final String line = "embed-host-controller --std-out=echo --domain-config=domain-empty-cli.xml --empty-domain-config --remove-existing-domain-config" + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        cli.sendLine("/interface=test-interface:add(any-address)");
        cli.sendLine("/socket-binding-group=test-sbg:add(default-interface=test-interface)");
        cli.sendLine("/profile=test-profile:add()");
        cli.sendLine("/server-group=test-server-group:add(profile=test-profile, socket-binding-group=test-sbg)");

        cli.sendLine("ls /server-group=test-server-group");
        cli.sendLine("reload --host=master --admin-only=true");
        assertTrue(cli.isConnected());
        cli.sendLine("ls /interface=test-interface");
        cli.sendLine("ls /socket-binding-group=test-sbg");
        cli.sendLine("ls /profile=test-profile");
        cli.sendLine("ls /server-group=test-server-group");
    }

    @Test
    public void testEmptyDomainConfigAndHostConfigConfigureWithReload() throws Exception {
        final String line = "embed-host-controller --std-out=echo --domain-config=domain-empty-cli.xml --empty-domain-config --remove-existing-domain-config --host-config=host-empty-cli.xml --empty-host-config --remove-existing-host-config" + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());

        // host-empty-cli.xml
        assertTrue(cli.isConnected());
        cli.sendLine("/host=master1:add()");
        cli.sendLine("/host=master1/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=master1/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=master1/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=master1/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");

        // domain-empty-cli.xml
        cli.sendLine("/interface=test-interface:add(any-address)");
        cli.sendLine("/socket-binding-group=test-sbg:add(default-interface=test-interface)");
        cli.sendLine("/profile=test-profile:add()");
        cli.sendLine("/server-group=test-server-group:add(profile=test-profile, socket-binding-group=test-sbg)");

        cli.sendLine("reload --host=master1 --admin-only=true");
        assertTrue(cli.isConnected());

        cli.sendLine("ls /interface=test-interface");
        cli.sendLine("ls /socket-binding-group=test-sbg");
        cli.sendLine("ls /profile=test-profile");
        cli.sendLine("ls /server-group=test-server-group");
        cli.sendLine("ls /host=master1/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=master1/interface=test-mgmt-interface");
        cli.sendLine("ls /host=master1/core-service=management/management-interface=http-interface");
    }

    @Test
    public void addNewDomainSlave() throws Exception {
        // this adds a new slave HC. Note that most of the add ops below are just to get to a minimal config that will allow the reload to be
        // successful.
        final String line = "embed-host-controller --std-out=echo --host-config=host-empty-cli.xml --empty-host-config --remove-existing-host-config " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        cli.sendLine("/host=fooslave:add()");
        // host-empty-cli.xml
        assertTrue(cli.isConnected());
        cli.sendLine("/host=fooslave/core-service=management/security-realm=test-realm:add()");
        cli.sendLine("/host=fooslave/core-service=management/security-realm=test-realm/authentication=local:add(default-user=\"$local\", skip-group-loading=true)");
        cli.sendLine("/host=fooslave/interface=test-mgmt-interface:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/host=fooslave/core-service=management/management-interface=http-interface:add(interface=test-mgmt-interface, security-realm=test-realm, http-upgrade-enabled=true, port=${jboss.management.http.port:9990})");
        // add the remote DC
        cli.sendLine("/host=fooslave:write-remote-domain-controller(host=foomaster)");
        cli.sendLine("reload --host=fooslave --admin-only=true");
        assertTrue(cli.isConnected());
        cli.sendLine("ls /host=fooslave/core-service=management/security-realm=test-realm");
        cli.sendLine("ls /host=fooslave/interface=test-mgmt-interface");
        cli.sendLine("ls /host=fooslave/core-service=management/management-interface=http-interface");
        cli.sendLine("/host=fooslave:read-attribute(name=domain-controller)");
        final CLIOpResult resp = cli.readAllAsOpResult();
        final ModelNode result = resp.getResponseNode();
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        // verify that the slave is configurted with a remote DC named "foomaster"
        assertEquals("foomaster", result.get(RESULT).get(REMOTE).get(HOST).asString());
    }

    private void assertProperty(final String propertyName, final String expected, final boolean notPresent) throws IOException, InterruptedException {
        cli.sendLine("/system-property=" + propertyName + " :read-attribute(name=value)", true);
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode resp = result.getResponseNode();
        ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
        if (notPresent) {
            assertTrue(stateNode.asString().contains("WFLYCTL0216"));
        } else {
            assertEquals(expected, stateNode.asString());
        }
    }

    private void assertState(String expected, int timeout) throws IOException, InterruptedException {
        assertState(expected, timeout, "/host=master:read-attribute(name=host-state)");
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
