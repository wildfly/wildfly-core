/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.remoting.Protocol;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import static org.jboss.as.remoting.Protocol.HTTPS_REMOTING;
import static org.jboss.as.remoting.Protocol.HTTP_REMOTING;
import static org.jboss.as.remoting.Protocol.REMOTE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for jboss-cli.xml controller aliases
 *
 * @author Martin Schvarcbacher
 */
@RunWith(WildFlyRunner.class)
public class CliControllerConfigTestCase {

    private static final Protocol TEST_RUNNER_PROTOCOL = HTTP_REMOTING;
    private static final String CONTROLLER_ALIAS_NAME = "Test_Suite_Server1_Name";
    private static final int INVALID_PORT = TestSuiteEnvironment.getServerPort() - 1;
    private static final String DISCONNECTED_PROMPT = "[disconnected /]";
    private static final String CONNECTED_PROMPT = "@";
    private static final String READ_SERVER_STATE = ":read-attribute(name=server-state)";
    private File tempJbossConfigFile;

    @Before
    public void prepareFiles() {
        try {
            tempJbossConfigFile = File.createTempFile("tmp-jboss-cli", ".xml");
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @After
    public void deleteFiles() {
        assertTrue("failed to delete test config file", tempJbossConfigFile.delete());
    }

    /**
     * Default controller from jboss-cli.xml should be invalid to ensure settings are loaded from controller alias
     */
    @Test
    public void testInvalidDefaultConfiguration() {
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setDefaultProtocol(REMOTE)
                .setUseLegacyOverride(true)
                .setDefaultControllerPort(INVALID_PORT)
                .setDefaultControllerProtocol(HTTP_REMOTING);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeInteractive();
            boolean returnValue = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnValue);
        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests connection to a controller aliased in jboss-cli.xml using --controller,
     * with all options (protocol, hostname, port) specified
     */
    @Test
    public void testConnectToAliasedController() {
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true)
                .setDefaultProtocol(REMOTE)
                .setDefaultControllerPort(INVALID_PORT)
                .setDefaultControllerProtocol(HTTPS_REMOTING)
                .setAliasControllerPort(TestSuiteEnvironment.getServerPort())
                .setAliasControllerProtocol(TEST_RUNNER_PROTOCOL);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("connect");
            boolean returnState = cli.pushLineAndWaitForResults(READ_SERVER_STATE, CONNECTED_PROMPT);
            assertTrue(returnState);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests if protocol specified in default-controller overrides default-protocol
     * when calling --connect without --controller
     */
    @Test
    public void testProtocolOverridingConnected() {
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true).setDefaultProtocol(HTTPS_REMOTING) //invalid settings
                .setDefaultControllerPort(TestSuiteEnvironment.getServerPort())
                .setDefaultControllerProtocol(TEST_RUNNER_PROTOCOL);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("connect");
            boolean returnState = cli.pushLineAndWaitForResults(READ_SERVER_STATE, CONNECTED_PROMPT);
            assertTrue(returnState);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Test for use-legacy-override=true, no connection protocol specified and port set to 9999
     * Missing options should not be loaded from default-controller
     */
    @Test
    public void testUseLegacyOverrideTrue() {
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true)
                .setDefaultProtocol(HTTP_REMOTING)
                .setDefaultControllerProtocol(HTTPS_REMOTING)
                .setDefaultControllerPort(INVALID_PORT)
                .setAliasControllerProtocol(null)
                .setAliasControllerPort(9999);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeInteractive();
            cli.clearOutput();
            boolean returnConnect = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnConnect);
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9999));
            assertTrue(output.contains("remoting://") || output.contains("remote://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Test for use-legacy-override=false, ALIAS: no connection protocol specified and port set to 9999
     * Missing options should not be loaded from default-controller
     */
    @Test
    public void testUseLegacyOverrideFalse() {
        final Protocol expectedProtocol = HTTPS_REMOTING;
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(false)
                .setDefaultProtocol(expectedProtocol)
                .setDefaultControllerProtocol(HTTP_REMOTING)
                .setDefaultControllerPort(INVALID_PORT)
                .setAliasControllerProtocol(null)
                .setAliasControllerPort(9999);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeInteractive();
            boolean returnConnect = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnConnect);
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9999));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests behavior of default-controller without specified protocol and using non-standard port
     * Protocol should be taken from default-protocol
     */
    @Test
    public void testDefaultControllerNoExplicitPort() {
        Protocol expectedProtocol = HTTPS_REMOTING;
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true)
                .setDefaultProtocol(expectedProtocol)
                .setDefaultControllerProtocol(null)
                .setDefaultControllerPort(INVALID_PORT);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeInteractive();
            boolean returnConnect = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnConnect);
            String output = cli.getOutput();
            assertTrue(output.contains(":" + INVALID_PORT));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests if default-protocol is overridden by protocol defined in default-controller
     */
    @Test
    public void testDefaultProtocolOverriding() {
        Protocol expectedProtocol = HTTPS_REMOTING;
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true)
                .setDefaultProtocol(REMOTE)
                .setDefaultControllerProtocol(expectedProtocol)
                .setDefaultControllerPort(INVALID_PORT);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeInteractive();
            boolean returnConnect = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnConnect);
            String output = cli.getOutput();
            assertTrue(output.contains(":" + INVALID_PORT));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Test to ensure https-remoting defaults to port 9993 when no port is specified
     */
    @Test
    public void testHttpsRemotingConnection() {
        Protocol expectedProtocol = HTTPS_REMOTING;
        JbossCliConfig jbossCliConfig = new JbossCliConfig()
                .setUseLegacyOverride(true).setDefaultProtocol(REMOTE) //non-working default protocol
                .setDefaultControllerProtocol(expectedProtocol)
                .setDefaultControllerPort(null);
        jbossCliConfig.writeJbossCliConfig(tempJbossConfigFile);

        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeInteractive();
            boolean returnConnect = cli.pushLineAndWaitForResults("connect", DISCONNECTED_PROMPT);
            assertTrue(returnConnect);
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9993));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Returns CliProcessWrapper with settings loaded from tempJbossConfigFile
     *
     * @param connectToAlias if true connects to aliased controller, {@code: <default-controller>} otherwise
     * @return configured CliProcessWrapper
     */
    private CliProcessWrapper getTestCliProcessWrapper(boolean connectToAlias) {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + tempJbossConfigFile.getAbsolutePath())
                .addCliArgument("--echo-command")
                .addCliArgument("--no-color-output");
        if (connectToAlias) {
            cli.addCliArgument("--controller=" + CONTROLLER_ALIAS_NAME);
        }
        return cli;
    }

    private class JbossCliConfig {
        private Boolean useLegacyOverride;
        private Protocol defaultProtocol;
        private Protocol defaultControllerProtocol;
        private Integer defaultControllerPort;
        private Protocol aliasControllerProtocol;
        private Integer aliasControllerPort;

        public JbossCliConfig setUseLegacyOverride(boolean useLegacyOverride) {
            this.useLegacyOverride = useLegacyOverride;
            return this;
        }

        public JbossCliConfig setDefaultProtocol(Protocol defaultProtocol) {
            this.defaultProtocol = defaultProtocol;
            return this;
        }

        public JbossCliConfig setDefaultControllerProtocol(Protocol defaultControllerProtocol) {
            this.defaultControllerProtocol = defaultControllerProtocol;
            return this;
        }

        public JbossCliConfig setDefaultControllerPort(Integer defaultControllerPort) {
            this.defaultControllerPort = defaultControllerPort;
            return this;
        }

        public JbossCliConfig setAliasControllerProtocol(Protocol aliasControllerProtocol) {
            this.aliasControllerProtocol = aliasControllerProtocol;
            return this;
        }

        public JbossCliConfig setAliasControllerPort(Integer aliasControllerPort) {
            this.aliasControllerPort = aliasControllerPort;
            return this;
        }

        private String createDefaultProtocol() {
            if (defaultProtocol == null && useLegacyOverride == null) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("<default-protocol  use-legacy-override=\"").append(useLegacyOverride).append("\">");
            builder.append(defaultProtocol).append("</default-protocol>\n");
            return builder.toString();
        }

        private String createDefaultController() {
            if (defaultControllerProtocol == null && defaultControllerPort == null) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("<default-controller>\n");
            if (defaultControllerProtocol != null) {
                builder.append("<protocol>").append(defaultControllerProtocol).append("</protocol>\n");
            }
            builder.append("<host>").append(TestSuiteEnvironment.getServerAddress()).append("</host>\n");
            if (defaultControllerPort != null) {
                builder.append("<port>").append(defaultControllerPort).append("</port>\n");
            }
            builder.append("</default-controller>\n");
            return builder.toString();
        }

        private String createControllerAlias() {
            if (aliasControllerProtocol == null && aliasControllerPort == null) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("<controllers>\n");
            builder.append("<controller name=\"" + CONTROLLER_ALIAS_NAME + "\">\n");
            if (aliasControllerProtocol != null) {
                builder.append("<protocol>").append(aliasControllerProtocol).append("</protocol>\n");
            }
            builder.append("<host>").append(TestSuiteEnvironment.getServerAddress()).append("</host>\n");
            if (aliasControllerPort != null) {
                builder.append("<port>").append(aliasControllerPort).append("</port>\n");
            }
            builder.append("</controller>\n");
            builder.append("</controllers>\n");
            return builder.toString();
        }

        /**
         * Writes specified config to jbossConfig for use as jboss-cli.[sh/bat/ps1] settings
         *
         * @param jbossConfig configuration file to be written
         */
        public void writeJbossCliConfig(final File jbossConfig) {
            final String defaultProtocol = createDefaultProtocol();
            final String defaultController = createDefaultController();
            final String aliasController = createControllerAlias();

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(jbossConfig), "UTF-8"))) {
                writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
                writer.write("<jboss-cli xmlns=\"urn:jboss:cli:3.1\">\n");
                if (defaultProtocol != null) {
                    writer.write(defaultProtocol);
                }
                if (defaultController != null) {
                    writer.write(defaultController);
                }
                if (aliasController != null) {
                    writer.write(aliasController);
                }
                writer.write("</jboss-cli>\n");
            } catch (IOException e) {
                fail(e.getLocalizedMessage());
            }
        }
    }
}
