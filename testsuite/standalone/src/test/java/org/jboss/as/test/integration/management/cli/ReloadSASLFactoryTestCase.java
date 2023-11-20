/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import static org.jboss.as.cli.Util.RESULT;
import org.jboss.as.cli.impl.CommandContextConfiguration;
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
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class ReloadSASLFactoryTestCase {

    private static final String MANAGEMENT_NATIVE_PORT = "9999";

    private static final String ROOT = TestSuiteEnvironment.getSystemProperty("jboss.home");
    private static final Path OTHER = Paths.get(ROOT, "standalone"
            + File.separator + "configuration" + File.separator + "mgmt-users-other.properties");
    private static final Path FOO = Paths.get(ROOT, "standalone"
            + File.separator + "configuration" + File.separator + "mgmt-users-foo.properties");

    @ClassRule
    public static final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    private static CommandContext cleaner;
    private static CommandContext ctx;
    private static final Path SOURCE = Paths.get(ROOT, "standalone", "configuration", "standalone.xml");
    private static Path TARGET;

    private static ModelNode existingSaslManagementUpgrade;

    @BeforeClass
    public static void setup() throws Exception {
        TARGET = Paths.get(temporaryUserHome.getRoot().getAbsolutePath(), "standalone.xml");
        Files.copy(SOURCE, TARGET, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Paths.get(ReloadSASLFactoryTestCase.class.getClassLoader().
                getResource("mgmt-users-other.properties").toURI()), OTHER, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Paths.get(ReloadSASLFactoryTestCase.class.getClassLoader().
                getResource("mgmt-users-foo.properties").toURI()), FOO, StandardCopyOption.REPLACE_EXISTING);

        // Create ctx, used to setup the test and do the final reload.
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setConsoleOutput(System.out).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();

        //setup native interface used for cleanup.
        setupNativeInterface();

        // Add the Foo realm.
        ctx.handle("/subsystem=elytron/properties-realm=FooRealm:"
                + "add(users-properties={path=mgmt-users-foo.properties,relative-to=jboss.server.config.dir})");
        // Add security domain
        ctx.handle("/subsystem=elytron/security-domain=Foo:"
                + "add(default-realm=FooRealm, permission-mapper=default-permission-mapper,realms=[{realm=FooRealm}])");
        // add factory
        ctx.handle("/subsystem=elytron/sasl-authentication-factory=foo-digest:"
                + "add(security-domain=Foo,sasl-server-factory=elytron,mechanism-configurations="
                + "[{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=FooRealm}]}])");

        // Add the Other realm.
        ctx.handle("/subsystem=elytron/properties-realm=OtherRealm:"
                + "add(users-properties={path=mgmt-users-other.properties,relative-to=jboss.server.config.dir})");
        // Add security domain
        ctx.handle("/subsystem=elytron/security-domain=Other:"
                + "add(default-realm=OtherRealm, permission-mapper=default-permission-mapper,realms=[{realm=OtherRealm}])");
        // add factory
        ctx.handle("/subsystem=elytron/sasl-authentication-factory=other-digest:"
                + "add(security-domain=Other,sasl-server-factory=elytron,mechanism-configurations="
                + "[{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=OtherRealm}]}])");

        ModelNode getSaslFactory = createOpNode("core-service=management/management-interface=http-interface", "read-attribute");
        getSaslFactory.get("name").set("http-upgrade");
        ModelNode res = ctx.getModelControllerClient().execute(getSaslFactory);
        if (res.hasDefined(RESULT) && res.get(RESULT).hasDefined("sasl-authentication-factory")) {
            existingSaslManagementUpgrade = res.get(RESULT);
        }
    }

    @Test
    public void test() throws Exception {
        // This is the connection used by test that is reconnected following authentication
        // configuration changes.
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            // attach foo factory to management interface
            cli.pushLineAndWaitForResults("/core-service=management/management-interface=http-interface:"
                    + "write-attribute(name=http-upgrade.sasl-authentication-factory,value=foo-digest)");

            // reload cli and use Foo credentials.
            boolean res = cli.pushLineAndWaitForResults("reload", "Username:");
            Assert.assertTrue(cli.getOutput(), res);
            res = cli.pushLineAndWaitForResults("jojo", "Password:");
            Assert.assertTrue(cli.getOutput(), res);
            res = cli.pushLineAndWaitForResults("jojo", null);
            Assert.assertTrue(cli.getOutput(), res);

            //switch to other sasl-factory.
            cli.pushLineAndWaitForResults("/core-service=management/management-interface=http-interface:"
                    + "write-attribute(name=http-upgrade.sasl-authentication-factory,value=other-digest)");
            // reload cli and use Other credentials.
            res = cli.pushLineAndWaitForResults("reload", "Username:");
            Assert.assertTrue(cli.getOutput(), res);
            res = cli.pushLineAndWaitForResults("toto", "Password:");
            Assert.assertTrue(cli.getOutput(), res);
            res = cli.pushLineAndWaitForResults("toto", null);
            Assert.assertTrue(cli.getOutput(), res);
        } finally {
            cli.destroyProcess();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            if (ctx != null) {
                // If cleaner is null, we were not able
                // to attach to native interface so
                // ctx context is clean and can be used to clean
                // the model.
                cleanConfig(cleaner == null ? ctx : cleaner);
            }
        } finally {
            try {
                if (cleaner != null) {
                    cleaner.terminateSession();
                }
            } finally {
                try {
                    if (ctx != null) {
                        ctx.terminateSession();
                    }
                } finally {
                    try {
                        Files.delete(OTHER);
                    } finally {
                        Files.delete(FOO);
                    }
                }
            }
        }
    }

    private static void setupNativeInterface() throws Exception {
        ctx.handle("/socket-binding-group=standard-sockets/socket-binding=management-native:"
                + "add(port=" + MANAGEMENT_NATIVE_PORT + ",interface=management");
        ctx.handle("/core-service=management/management-interface=native-interface:"
                + "add(socket-binding=management-native");
        ctx.handle("reload");

        // Build the cleaner
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setConsoleOutput(System.out).
                setController("remote://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + MANAGEMENT_NATIVE_PORT);
        cleaner = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        cleaner.connectController();
    }

    private static void cleanConfig(CommandContext context) throws Exception {
        Exception e = null;
        try {
            eraseFactory(context);
        } catch (Exception ex) {
            e = ex;
        } finally {
            try {
                removeFactory(context, "other-digest", "Other", "OtherRealm");
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            } finally {
                try {
                    removeFactory(context, "foo-digest", "Foo", "FooRealm");
                } catch (Exception ex) {
                    if (e == null) {
                        e = ex;
                    }
                } finally {
                    try {
                        if (existingSaslManagementUpgrade != null) {
                            ModelNode resetFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
                            resetFactory.get("name").set("http-upgrade");
                            resetFactory.get("value").set(existingSaslManagementUpgrade);
                            context.getModelControllerClient().execute(resetFactory);
                        }
                    } catch (Exception ex) {
                        if (e == null) {
                            e = ex;
                        }
                    } finally {
                        try {
                            // make the http-interface usable again for ctx context.
                            context.handle("reload");
                        } catch (Exception ex) {
                            if (e == null) {
                                e = ex;
                            }
                        } finally {
                            try {
                                removeNativeInterface(context);
                            } catch (Exception ex) {
                                if (e == null) {
                                    e = ex;
                                }
                            } finally {
                                try {
                                    // put back the server in original state.
                                    // can only be done from http-interface
                                    ctx.handle("reload");
                                } catch (Exception ex) {
                                    if (e == null) {
                                        e = ex;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (e != null) {
                throw e;
            }
        }
    }

    private static void removeNativeInterface(CommandContext context) throws Exception {
        Exception e = null;
        try {
            removeNativeMgmt(context);
        } catch (Exception ex) {
            e = ex;
        } finally {
            try {
                remoteNativeMgmtPort(context);
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

    private static void removeFactory(CommandContext context, String name, String securityDomain,
            String realm) throws Exception {
        Exception e = null;
        try {
            removeFactory(context, name);
        } catch (Exception ex) {
            e = ex;
        } finally {
            try {
                removeSecurityDomain(context, securityDomain);
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            } finally {
                try {
                    removeRealm(context, realm);
                } catch (Exception ex) {
                    if (e == null) {
                        e = ex;
                    }
                }
            }
        }

        if (e != null) {
            throw e;
        }
    }

    private static void eraseFactory(CommandContext context) throws Exception {
        context.handle("/core-service=management/management-interface=http-interface:"
                + "write-attribute(name=http-upgrade.sasl-authentication-factory,value=undefined)");
    }

    private static void removeFactory(CommandContext context, String name) throws Exception {
        context.handle("/subsystem=elytron/sasl-authentication-factory=" + name + ":remove()");
    }

    private static void removeSecurityDomain(CommandContext context, String name) throws Exception {
        context.handle("/subsystem=elytron/security-domain=" + name + ":remove()");
    }

    private static void removeRealm(CommandContext context, String name) throws Exception {
        context.handle("/subsystem=elytron/properties-realm=" + name + ":remove()");
    }

    private static void removeNativeMgmt(CommandContext context) throws Exception {
        context.handle("/core-service=management/management-interface=native-interface:remove()");
    }

    private static void remoteNativeMgmtPort(CommandContext context) throws Exception {
        context.handle("/socket-binding-group=standard-sockets/socket-binding=management-native:remove");
    }
}
