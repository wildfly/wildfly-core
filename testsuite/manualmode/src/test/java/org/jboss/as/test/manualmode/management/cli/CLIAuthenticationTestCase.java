/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import jakarta.inject.Inject;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CLIAuthenticationTestCase {

    private static final String FS_REALM_NAME = "fs-test-realm";
    private static final String USER = "user1";
    private static final String PASSWORD = "mypassword";

    private static class TestAuthenticator extends Authenticator {

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(USER, PASSWORD.toCharArray());
        }

        public String getRealm() {
            return super.getRequestingPrompt();
        }
    }
    @Inject
    private static ServerController container;

    @ClassRule
    public static final TemporaryFolder temporaryDir = new TemporaryFolder();

    private static String existingHttpManagementFactory;
    private static ModelNode existingSaslManagementUpgrade;

    @BeforeClass
    public static void initServer() throws Exception {
        container.start();
        ReloadRedirectTestCase.setupNativeInterface(container);
        ModelNode addFS = createOpNode("subsystem=elytron/filesystem-realm=" + FS_REALM_NAME,
                "add");
        addFS.get("path").set(temporaryDir.newFolder("identities").getAbsolutePath());
        container.getClient().executeForResult(addFS);

        ModelNode addIdentity = createOpNode("subsystem=elytron/filesystem-realm=" + FS_REALM_NAME,
                "add-identity");
        addIdentity.get("identity").set(USER);
        container.getClient().executeForResult(addIdentity);

        ModelNode setPassword = createOpNode("subsystem=elytron/filesystem-realm=" + FS_REALM_NAME,
                "set-password");
        setPassword.get("identity").set(USER);
        ModelNode clear = setPassword.get("clear");
        clear.get("password").set(PASSWORD);
        container.getClient().executeForResult(setPassword);

        ModelNode getHttpFactory = createOpNode("core-service=management/management-interface=http-interface", "read-attribute");
        getHttpFactory.get("name").set("http-authentication-factory");
        ModelNode res = container.getClient().executeForResult(getHttpFactory);
        if (res.isDefined()) {
            ModelNode eraseFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
            eraseFactory.get("name").set("http-authentication-factory");
            container.getClient().executeForResult(eraseFactory);
            existingHttpManagementFactory = res.asString();
        }

        ModelNode getSaslFactory = createOpNode("core-service=management/management-interface=http-interface", "read-attribute");
        getSaslFactory.get("name").set("http-upgrade");
        res = container.getClient().executeForResult(getSaslFactory);
        if (res.isDefined() && res.hasDefined("sasl-authentication-factory")) {
            ModelNode eraseFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
            eraseFactory.get("name").set("http-upgrade.sasl-authentication-factory");
            container.getClient().executeForResult(eraseFactory);
            existingSaslManagementUpgrade = res;
        }

    }

    @AfterClass
    public static void afterClass() throws Exception {
        try {
            // Even though we don't reuse this server, the next test uses the config so we
            // need to revert the config changes the test made
            ManagementClient client = ReloadRedirectTestCase.getCleanupClient();
            cleanConfig(client);
            ReloadRedirectTestCase.removeNativeInterface(client);
        } finally {
            container.stop();
        }
    }

    private static void eraseAllFactories(ManagementClient client) throws Exception {
        Exception e = null;
        try {
            ModelNode eraseHttp = createOpNode("core-service=management/management-interface=http-interface",
                    "write-attribute");
            eraseHttp.get("name").set("http-authentication-factory");
            client.executeForResult(eraseHttp);
        } catch (Exception ex) {
            if (e == null) {
                e = ex;
            }
        } finally {
            try {
                ModelNode eraseSasl = createOpNode("core-service=management/management-interface=http-interface",
                        "write-attribute");
                eraseSasl.get("name").set("http-upgrade.sasl-authentication-factory");
                client.executeForResult(eraseSasl);
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            }
        }
        if (e != null) {
            throw e;
        }
    }

    private static void cleanConfig(ManagementClient client) throws UnsuccessfulOperationException {
        try {
            ModelNode removeFS = createOpNode("subsystem=elytron/filesystem-realm=" + FS_REALM_NAME,
                    "remove");
            client.executeForResult(removeFS);
        } finally {
            try {
                if (existingHttpManagementFactory != null) {
                    ModelNode resetFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
                    resetFactory.get("name").set("http-authentication-factory");
                    resetFactory.get("value").set(existingHttpManagementFactory);
                    client.executeForResult(resetFactory);
                }
            } finally {
                if (existingSaslManagementUpgrade != null) {
                    ModelNode resetFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
                    resetFactory.get("name").set("http-upgrade");
                    resetFactory.get("value").set(existingSaslManagementUpgrade);
                    client.executeForResult(resetFactory);
                }
            }
        }
    }

    private static void cleanTest(ManagementClient client, String factory, String domain) throws Exception {
        Exception e = null;
        try {
            eraseAllFactories(client);
        } catch (Exception ex) {
            if (e == null) {
                e = ex;
            }
        } finally {
            try {
                ModelNode removeFactory = createOpNode("subsystem=elytron/" + factory,
                        "remove");
                client.executeForResult(removeFactory);
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            } finally {
                try {
                    ModelNode removeDomain = createOpNode("subsystem=elytron/security-domain=" + domain,
                            "remove");
                    client.executeForResult(removeDomain);
                } catch (Exception ex) {
                    if (e == null) {
                        e = ex;
                    }
                } finally {
                    try {
                        ModelNode removeMapper = createOpNode("subsystem=elytron/constant-realm-mapper=" + FS_REALM_NAME,
                                "remove");
                        client.executeForResult(removeMapper);
                    } catch (Exception ex) {
                        if (e == null) {
                            e = ex;
                        }
                    }
                }
            }
        }
        if (e != null) {
            throw e;
        }
    }

    @Test
    public void testHttpAuth() throws Throwable {
        CliProcessWrapper cliProc = new CliProcessWrapper()
                .addCliArgument("--no-color-output")
                .addCliArgument("--connect")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort());
        Throwable exception = null;
        try {
            cliProc.executeInteractive();
            cliProc.clearOutput();
            Assert.assertTrue(cliProc.pushLineAndWaitForResults("security enable-http-auth-management"
                    + " --mechanism=BASIC"
                    + " --exposed-realm=FOO"
                    + " --file-system-realm-name=" + FS_REALM_NAME
                    + " --new-auth-factory-name=test-http-auth-factory"
                    + " --new-security-domain-name=test-http-auth-security-domain", "[standalone@"));
            Assert.assertTrue(cliProc.getOutput(), cliProc.getOutput().contains("Command success."));
            {
                URL url = new URL("http://" + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort() + "/management");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                Assert.assertEquals(401, connection.getResponseCode());
            }
            {
                TestAuthenticator myAuth = new TestAuthenticator();
                Authenticator.setDefault(myAuth);
                try {
                    URL url = new URL("http://" + TestSuiteEnvironment.getServerAddress() + ":"
                            + TestSuiteEnvironment.getServerPort() + "/management");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    Assert.assertEquals(200, connection.getResponseCode());
                    Assert.assertEquals("FOO", myAuth.getRealm());
                } finally {
                    Authenticator.setDefault(null);
                }
            }
            Assert.assertTrue(cliProc.getOutput(),
                    cliProc.pushLineAndWaitForResults("security disable-http-auth-management",
                            "[standalone@"));
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            try {
                cleanTest(ReloadRedirectTestCase.getCleanupClient(),
                        "http-authentication-factory=test-http-auth-factory",
                        "test-http-auth-security-domain");
            } catch (Throwable ex) {
                if (exception == null) {
                    exception = ex;
                }
            } finally {
                try {
                    cliProc.ctrlCAndWaitForClose();
                } catch (Throwable ex) {
                    if (exception == null) {
                        exception = ex;
                    }
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    @Test
    public void testSaslAuth() throws Throwable {
        CliProcessWrapper cliProc = new CliProcessWrapper()
                .addCliArgument("--no-color-output")
                .addCliArgument("--connect")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort());
        Throwable exception = null;
        try {
            cliProc.executeInteractive();
            cliProc.clearOutput();
            Assert.assertTrue(cliProc.pushLineAndWaitForResults("security enable-sasl-management"
                    + " --mechanism=DIGEST-MD5"
                    + " --exposed-realm=FOO"
                    + " --file-system-realm-name=" + FS_REALM_NAME
                    + " --new-auth-factory-name=test-sasl-factory"
                    + " --new-security-domain-name=test-sasl-security-domain", "Username:"));
            Assert.assertTrue(cliProc.getOutput(), cliProc.getOutput().contains("Authenticating against security realm: FOO"));
            cliProc.clearOutput();
            Assert.assertTrue(
                    cliProc.pushLineAndWaitForResults(USER, "Password:"));
            cliProc.clearOutput();
            Assert.assertTrue(
                    cliProc.pushLineAndWaitForResults(PASSWORD, "[standalone@"));
            Assert.assertTrue(cliProc.getOutput(), cliProc.getOutput().contains("Command success."));
            Assert.assertTrue(cliProc.pushLineAndWaitForResults("security disable-sasl-management",
                    "[standalone@"));
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            try {
                cleanTest(ReloadRedirectTestCase.getCleanupClient(), "sasl-authentication-factory=test-sasl-factory", "test-sasl-security-domain");
            } catch (Throwable ex) {
                if (exception == null) {
                    exception = ex;
                }
            } finally {
                try {
                    cliProc.ctrlCAndWaitForClose();
                } catch (Throwable ex) {
                    if (exception == null) {
                        exception = ex;
                    }
                }
            }
        }
        if (exception != null) {
            throw new Exception(cliProc.getOutput(), exception);
        }
    }
}
