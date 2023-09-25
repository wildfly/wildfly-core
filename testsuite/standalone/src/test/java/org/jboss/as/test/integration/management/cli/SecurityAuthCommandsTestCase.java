/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import static org.jboss.as.test.integration.management.cli.SecurityCommandsTestCase.disableNative;
import static org.jboss.as.test.integration.management.cli.SecurityCommandsTestCase.enableNative;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class SecurityAuthCommandsTestCase {

    private static final ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();

    private static CommandContext ctx;

    @ClassRule
    public static final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    private static ManagementClient client;

    private static final String TEST_DOMAIN = "test-domain";
    private static final String TEST_SASL_FACTORY = "test-sasl-factory";
    private static final String TEST_HTTP_FACTORY = "test-http-factory";
    private static final String TEST_USERS_REALM = "test-users-realm";
    private static final String TEST_KS_REALM = "test-ks-realm";
    private static final String TEST_FS_REALM = "test-fs-realm";
    private static final String TEST_KS = "test-ks";

    private static List<ModelNode> originalPropertiesRealms;
    private static List<ModelNode> originalKSRealms;
    private static List<ModelNode> originalSaslFactories;
    private static List<ModelNode> originalHttpFactories;
    private static List<ModelNode> originalSecurityDomains;
    private static List<ModelNode> originalFSRealms;
    private static List<ModelNode> originalConstantMappers;
    private static List<ModelNode> originalConstantRoleMappers;

    private static String existingHttpManagementFactory;
    private static ModelNode existingSaslManagementUpgrade;

    @BeforeClass
    public static void setup() throws Exception {
        // Create ctx, used to setup the test and do the final reload.
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setConsoleOutput(consoleOutput).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();
        client = new ManagementClient(ctx.getModelControllerClient(), null, -1, null);

        // filesystem realm.
        addFSRealm();

        originalFSRealms = getFileSystemRealms();
        originalPropertiesRealms = getPropertiesRealm();
        originalSaslFactories = getSaslFactories();
        originalHttpFactories = getHttpFactories();
        originalSecurityDomains = getSecurityDomains();
        originalConstantMappers = getConstantRealmMappers();
        originalConstantRoleMappers = getConstantRoleMappers();
        originalKSRealms = getKSRealms();

        ModelNode getHttpFactory = createOpNode("core-service=management/management-interface=http-interface", "read-attribute");
        getHttpFactory.get("name").set("http-authentication-factory");
        ModelNode res = client.executeForResult(getHttpFactory);
        if (res.isDefined()) {
            ModelNode eraseFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
            eraseFactory.get("name").set("http-authentication-factory");
            client.executeForResult(eraseFactory);
            existingHttpManagementFactory = res.asString();
        }

        ModelNode getSaslFactory = createOpNode("core-service=management/management-interface=http-interface", "read-attribute");
        getSaslFactory.get("name").set("http-upgrade");
        res = client.executeForResult(getSaslFactory);
        if (res.isDefined() && res.hasDefined("sasl-authentication-factory")) {
            ModelNode eraseFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
            eraseFactory.get("name").set("http-upgrade.sasl-authentication-factory");
            client.executeForResult(eraseFactory);
            existingSaslManagementUpgrade = res;
        }
    }

    private static void addFSRealm() throws Exception {
        ctx.handle("/subsystem=elytron/filesystem-realm=" + TEST_FS_REALM + ":add(path="
                + CliCompletionTestCase.escapePath(temporaryUserHome.newFolder("identities").getAbsolutePath()));
        ctx.handle("/subsystem=elytron/filesystem-realm=" + TEST_FS_REALM + ":add-identity(identity=user1");
        ctx.handle("/subsystem=elytron/filesystem-realm=" + TEST_FS_REALM + ":set-password(identity=user1,clear={password=mypassword})");
    }

    private static List<ModelNode> getPropertiesRealm() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("properties-realm");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/properties-realm=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<ModelNode> getKSRealms() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("key-store-realm");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/key-store-realm=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static ModelNode getConstantRoleMapper(String name) throws UnsuccessfulOperationException {
        ModelNode prop = createOpNode("subsystem=elytron/constant-role-mapper=" + name, "read-resource");
        prop.get("recursive").set(Boolean.TRUE);
        prop.get("recursive-depth").set(100);
        return client.executeForResult(prop);
    }

    private static List<ModelNode> getFileSystemRealms() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("filesystem-realm");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/filesystem-realm=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<ModelNode> getSaslFactories() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("sasl-authentication-factory");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/sasl-authentication-factory=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<String> getNames(String childrenType) throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set(childrenType);
        List<String> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            res.add(mn.asString());
        }
        return res;
    }

    private static List<ModelNode> getHttpFactories() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("http-authentication-factory");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/http-authentication-factory=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<ModelNode> getSecurityDomains() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("security-domain");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/security-domain=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<ModelNode> getConstantRoleMappers() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("constant-role-mapper");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/constant-role-mapper=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static List<ModelNode> getConstantRealmMappers() throws UnsuccessfulOperationException {
        ModelNode props = createOpNode("subsystem=elytron",
                "read-children-names");
        props.get("child-type").set("constant-realm-mapper");
        List<ModelNode> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(props).asList()) {
            ModelNode prop = createOpNode("subsystem=elytron/constant-realm-mapper=" + mn.asString(), "read-resource");
            prop.get("recursive").set(Boolean.TRUE);
            prop.get("recursive-depth").set(100);
            res.add(client.executeForResult(prop));
        }
        return res;
    }

    private static void checkState() throws Exception {
        Assert.assertEquals(originalConstantMappers, getConstantRealmMappers());
        Assert.assertEquals(originalFSRealms, getFileSystemRealms());
        Assert.assertEquals(originalHttpFactories, getHttpFactories());
        Assert.assertEquals(originalPropertiesRealms, getPropertiesRealm());
        Assert.assertEquals(originalKSRealms, getKSRealms());
        Assert.assertEquals(originalSaslFactories, getSaslFactories());
        Assert.assertEquals(originalSecurityDomains, getSecurityDomains());
        Assert.assertEquals(originalConstantRoleMappers, getConstantRoleMappers());
    }

    @After
    public void cleanupTest() throws Exception {
        try {
            eraseAllFactories(client);
            eraseAuth();
            checkState();
        } finally {
            ctx.handle("reload");
        }
    }

    private void eraseAuth() throws Exception {
        // Clean everything tests could have created or not created.
        try {
            ModelNode op = createOpNode("subsystem=elytron/sasl-authentication-factory=" + TEST_SASL_FACTORY,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode op = createOpNode("subsystem=elytron/http-authentication-factory=" + TEST_HTTP_FACTORY,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode op = createOpNode("subsystem=elytron/security-domain=" + TEST_DOMAIN,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode op = createOpNode("subsystem=elytron/properties-realm=" + TEST_USERS_REALM,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode op = createOpNode("subsystem=elytron/key-store-realm=" + TEST_KS_REALM,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode removeMapper = createOpNode("subsystem=elytron/constant-realm-mapper=" + TEST_USERS_REALM,
                    "remove");
            client.executeForResult(removeMapper);
        } catch (Exception ex) {
            // OK,
        }
        try {
            ModelNode removeMapper = createOpNode("subsystem=elytron/constant-realm-mapper=" + TEST_FS_REALM,
                    "remove");
            client.executeForResult(removeMapper);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode removeMapper = createOpNode("subsystem=elytron/constant-realm-mapper=" + TEST_KS_REALM,
                    "remove");
            client.executeForResult(removeMapper);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode removeMapper = createOpNode("subsystem=elytron/constant-role-mapper="
                    + DefaultResourceNames.ROLE_MAPPER_NAME,
                    "remove");
            client.executeForResult(removeMapper);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode removeMapper = createOpNode("subsystem=elytron/constant-realm-mapper=ManagementRealm",
                    "remove");
            client.executeForResult(removeMapper);
        } catch (Exception ex) {
            // OK,
        }

        try {
            ModelNode op = createOpNode("subsystem=elytron/key-store=" + TEST_KS,
                    "remove");
            client.executeForResult(op);
        } catch (Exception ex) {
            // OK,
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

    @AfterClass
    public static void cleanup() throws Exception {
        Exception e = null;
        if (ctx != null) {
            try {
                if (existingHttpManagementFactory != null) {
                    ModelNode resetFactory = createOpNode("core-service=management/management-interface=http-interface", "write-attribute");
                    resetFactory.get("name").set("http-authentication-factory");
                    resetFactory.get("value").set(existingHttpManagementFactory);
                    client.executeForResult(resetFactory);
                }
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
                        client.executeForResult(resetFactory);
                    }
                } catch (Exception ex) {
                    if (e == null) {
                        e = ex;
                    }
                } finally {
                    try {
                        ctx.handle("/subsystem=elytron/filesystem-realm=" + TEST_FS_REALM + ":remove");
                    } catch (Exception ex) {
                        if (e == null) {
                            e = ex;
                        }
                    } finally {
                        try {
                            ctx.handle("reload");
                        } finally {
                            ctx.terminateSession();
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
    public void testOOBSASLManagement() throws Exception {
        ctx.handle("security enable-sasl-management --no-reload");
        Assert.assertEquals(ElytronUtil.OOTB_MANAGEMENT_SASL_FACTORY, getManagementInterfaceAuthFactory(ctx, null, true));
        ctx.handle("security disable-sasl-management --no-reload");
        Assert.assertNull(getManagementInterfaceAuthFactory(ctx, null, true));
    }

    @Test
    public void testOOBSASLManagementItf() throws Exception {
        ctx.handle("security enable-sasl-management --no-reload --management-interface=" + Util.HTTP_INTERFACE);
        Assert.assertEquals(ElytronUtil.OOTB_MANAGEMENT_SASL_FACTORY, getManagementInterfaceAuthFactory(ctx, Util.HTTP_INTERFACE, true));
        ctx.handle("security disable-sasl-management --no-reload --management-interface=" + Util.HTTP_INTERFACE);
        Assert.assertNull(getManagementInterfaceAuthFactory(ctx, Util.HTTP_INTERFACE, true));
    }

    @Test
    public void testOOBSASLManagementNative() throws Exception {
        enableNative(ctx);
        try {
            ctx.handle("security enable-sasl-management --no-reload --management-interface=" + Util.NATIVE_INTERFACE);
            Assert.assertEquals(ElytronUtil.OOTB_MANAGEMENT_SASL_FACTORY, getManagementInterfaceAuthFactory(ctx, Util.NATIVE_INTERFACE, true));
            ctx.handle("security disable-sasl-management --no-reload --management-interface=" + Util.NATIVE_INTERFACE);
            Assert.assertNull(getManagementInterfaceAuthFactory(ctx, Util.NATIVE_INTERFACE, true));
        } finally {
            disableNative(ctx);
        }
    }

    @Test
    public void testOOBSASLManagement2() throws Exception {
        // New factory but reuse OOB ManagementRealm properties realm.
        // side effect is to create a constant realm mapper for ManagementRealm.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=DIGEST-MD5 "
                + "--user-properties-file=mgmt-users.properties --group-properties-file=mgmt-groups.properties  "
                + "--relative-to=jboss.server.config.dir --exposed-realm=ManagementRealm --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_SASL_FACTORY);
        Assert.assertEquals(TEST_SASL_FACTORY, getManagementInterfaceAuthFactory(ctx, null, true));

        // Check Realm.
        Assert.assertEquals("ManagementRealm", getExposedRealmName(true, "DIGEST-MD5"));
        Assert.assertEquals("ManagementRealm", getMechanismRealmMapper(true, "DIGEST-MD5"));
        getNames(Util.CONSTANT_REALM_MAPPER).contains("ManagementRealm");

        // Replace with file system realm.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=DIGEST-MD5 "
                + "--file-system-realm-name=" + TEST_FS_REALM);

        Assert.assertEquals(TEST_FS_REALM, getMechanismRealmMapper(true, "DIGEST-MD5"));
        getNames(Util.CONSTANT_REALM_MAPPER).contains(TEST_FS_REALM);

        // check that domain has both realms.
        List<String> realms = getDomainRealms(TEST_DOMAIN);
        Assert.assertTrue(realms.contains("ManagementRealm"));
        Assert.assertTrue(realms.contains(TEST_FS_REALM));
    }

    @Test
    public void testOOBHTTPManagement() throws Exception {
        ctx.handle("security enable-http-auth-management --no-reload");
        Assert.assertEquals(ElytronUtil.OOTB_MANAGEMENT_HTTP_FACTORY, getManagementInterfaceAuthFactory(ctx, null, false));
        ctx.handle("security disable-http-auth-management --no-reload");
        Assert.assertNull(getManagementInterfaceAuthFactory(ctx, null, true));
    }

    @Test
    public void testOOBHTTPManagement2() throws Exception {
        // New factory but reuse OOB ManagementRealm properties realm.
        // side effect is to create a constant realm mapper for ManagementRealm.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=BASIC "
                + "--user-properties-file=mgmt-users.properties --group-properties-file=mgmt-groups.properties  "
                + "--relative-to=jboss.server.config.dir --exposed-realm=ManagementRealm --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_HTTP_FACTORY);
        Assert.assertEquals(TEST_HTTP_FACTORY, getManagementInterfaceAuthFactory(ctx, null, false));

        // Check Realm.
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "BASIC"));
        Assert.assertEquals("ManagementRealm", getMechanismRealmMapper(false, "BASIC"));
        getNames(Util.CONSTANT_REALM_MAPPER).contains("ManagementRealm");

        // Replace with file system realm.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=BASIC "
                + "--file-system-realm-name=" + TEST_FS_REALM);

        Assert.assertEquals(TEST_FS_REALM, getMechanismRealmMapper(false, "BASIC"));
        getNames(Util.CONSTANT_REALM_MAPPER).contains(TEST_FS_REALM);

        // check that domain has both realms.
        List<String> realms = getDomainRealms(TEST_DOMAIN);
        Assert.assertTrue(realms.contains("ManagementRealm"));
        Assert.assertTrue(realms.contains(TEST_FS_REALM));
    }

    @Test
    public void testSASLManagement() throws Exception {
        String cmd = "security enable-sasl-management --no-reload --mechanism=DIGEST-MD5"
                + " --user-properties-file=mgmt-users.properties --group-properties-file=mgmt-groups.properties"
                + " --relative-to=jboss.server.config.dir --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_SASL_FACTORY + " --new-realm-name=" + TEST_USERS_REALM;
        {
            // Must use --exposed-realm-name=
            boolean failed = false;
            try {
                ctx.handle(cmd);
            } catch (Exception ex) {
                // XXX OK.
                failed = true;
            }
            if (!failed) {
                throw new Exception("--exposed-realm-name= must be set");
            }
        }
        cmd += " --exposed-realm=ManagementRealm";
        // Enable and add mechanisms.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=JBOSS-LOCAL-USER --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_SASL_FACTORY);

        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains("local"));

        Assert.assertTrue(getNames(Util.SASL_AUTHENTICATION_FACTORY).contains(TEST_SASL_FACTORY));
        Assert.assertTrue(getNames(Util.SECURITY_DOMAIN).contains(TEST_DOMAIN));

        Assert.assertEquals(Arrays.asList("JBOSS-LOCAL-USER"), getMechanisms(true));
        Assert.assertNull(getRoleMapper(true, "local"));
        ctx.handle("security enable-sasl-management --no-reload --mechanism=JBOSS-LOCAL-USER --super-user");
        Assert.assertEquals(Arrays.asList("JBOSS-LOCAL-USER"), getMechanisms(true));
        Assert.assertEquals("super-user-mapper", getRoleMapper(true, "local"));

        // Add DIGEST + properties file realm.
        ctx.handle(cmd);
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains("local"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(true, "DIGEST-MD5"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(true, "DIGEST-MD5"));

        // capture the state.
        List<ModelNode> factories = getSaslFactories();
        List<ModelNode> domains = getSecurityDomains();
        List<ModelNode> mappers = getConstantRealmMappers();
        List<ModelNode> userRealms = getPropertiesRealm();

        List<String> expected1 = Arrays.asList("JBOSS-LOCAL-USER", "DIGEST-MD5");
        Assert.assertEquals(expected1, getMechanisms(true));

        Assert.assertTrue(getNames(Util.PROPERTIES_REALM).contains(TEST_USERS_REALM));

        // Reverse order of mechanisms.
        ctx.handle("security reorder-sasl-management --mechanisms-order=DIGEST-MD5,JBOSS-LOCAL-USER, --no-reload");
        List<String> expected2 = Arrays.asList("DIGEST-MD5", "JBOSS-LOCAL-USER");
        Assert.assertEquals(expected2, getMechanisms(true));
        ctx.handle("security reorder-sasl-management --mechanisms-order=JBOSS-LOCAL-USER,DIGEST-MD5 --no-reload");
        Assert.assertEquals(expected1, getMechanisms(true));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(true, "DIGEST-MD5"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(true, "DIGEST-MD5"));

        // Disable local mechanism
        ctx.handle("security disable-sasl-management --no-reload --mechanism=JBOSS-LOCAL-USER");
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains("local"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
        //still secured
        Assert.assertEquals(TEST_SASL_FACTORY, getManagementInterfaceAuthFactory(ctx, null, true));
        Assert.assertEquals(Arrays.asList("DIGEST-MD5"), getMechanisms(true));

        // Re-order a single mechanism
        ctx.handle("security reorder-sasl-management --mechanisms-order=DIGEST-MD5 --no-reload");
        Assert.assertEquals(Arrays.asList("DIGEST-MD5"), getMechanisms(true));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(true, "DIGEST-MD5"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(true, "DIGEST-MD5"));
        {
            // Disable the last mechanism is forbidden
            boolean failed = false;
            try {
                ctx.handle("security disable-sasl-management --no-reload --mechanism=DIGEST-MD5");
            } catch (Exception ex) {
                // XXX OK.
                failed = true;
            }
            if (!failed) {
                throw new Exception("Disabling the last mechanism should have failed");
            }
        }

        // Re-enable the mechanism and re-order as original state.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=JBOSS-LOCAL-USER --super-user");
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(expected2, getMechanisms(true));
        Assert.assertEquals("super-user-mapper", getRoleMapper(true, "local"));
        ctx.handle("security reorder-sasl-management --mechanisms-order=JBOSS-LOCAL-USER,DIGEST-MD5 --no-reload");
        Assert.assertEquals(expected1, getMechanisms(true));
        Assert.assertEquals(factories, getSaslFactories());

        // Disable DIGEST-MD5 and re-enable it. No new resource created because re-using same realm.
        ctx.handle("security disable-sasl-management --no-reload --mechanism=DIGEST-MD5");
        Assert.assertEquals(Arrays.asList("JBOSS-LOCAL-USER"), getMechanisms(true));
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(userRealms, getPropertiesRealm());
        // Re-enable MD5 re-using the existing realm
        ctx.handle("security enable-sasl-management --no-reload --mechanism=DIGEST-MD5 --properties-realm-name="
                + TEST_USERS_REALM + " --exposed-realm=ManagementRealm");
        Assert.assertEquals("ManagementRealm", getExposedRealmName(true, "DIGEST-MD5"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(true, "DIGEST-MD5"));
        Assert.assertEquals(factories, getSaslFactories());
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(userRealms, getPropertiesRealm());
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains("local"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
    }

    @Test
    public void testHTTPManagement() throws Exception {

        // Enable and add mechanisms.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=BASIC"
                + " --user-properties-file=mgmt-users.properties --group-properties-file=mgmt-groups.properties"
                + " --relative-to=jboss.server.config.dir --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_HTTP_FACTORY + " --new-realm-name="
                + TEST_USERS_REALM + " --exposed-realm=ManagementRealm");

        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "BASIC"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(false, "BASIC"));
        Assert.assertTrue(getNames(Util.HTTP_AUTHENTICATION_FACTORY).contains(TEST_HTTP_FACTORY));
        Assert.assertTrue(getNames(Util.SECURITY_DOMAIN).contains(TEST_DOMAIN));

        Assert.assertEquals(Arrays.asList("BASIC"), getMechanisms(false));

        // Add DIGEST.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=DIGEST"
                + " --properties-realm-name=" + TEST_USERS_REALM + " --exposed-realm=ManagementRealm");
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "BASIC"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(false, "BASIC"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).size() == 1);
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
        // capture the state.
        List<ModelNode> factories = getHttpFactories();
        List<ModelNode> domains = getSecurityDomains();
        List<ModelNode> mappers = getConstantRealmMappers();
        List<ModelNode> userRealms = getPropertiesRealm();

        List<String> expected1 = Arrays.asList("BASIC", "DIGEST");
        Assert.assertEquals(expected1, getMechanisms(false));

        Assert.assertTrue(getNames(Util.PROPERTIES_REALM).contains(TEST_USERS_REALM));

        // Disable digest mechanism
        ctx.handle("security disable-http-auth-management --no-reload --mechanism=DIGEST");

        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).size() == 1);
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "BASIC"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(false, "BASIC"));
        Assert.assertEquals(domains, getSecurityDomains());
        //still secured
        Assert.assertEquals(TEST_HTTP_FACTORY, getManagementInterfaceAuthFactory(ctx, null, false));
        Assert.assertEquals(Arrays.asList("BASIC"), getMechanisms(false));

        {
            // Disable the last mechanism is forbidden
            boolean failed = false;
            try {
                ctx.handle("security disable-http-auth-management --no-reload --mechanism=BASIC");
            } catch (Exception ex) {
                // XXX OK.
                failed = true;
            }
            if (!failed) {
                throw new Exception("Disabling the last mechanism should have failed");
            }
        }

        // Re-enable the mechanism
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=DIGEST"
                + " --properties-realm-name=" + TEST_USERS_REALM + " --exposed-realm=ManagementRealm");
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "BASIC"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(false, "BASIC"));
        Assert.assertEquals("ManagementRealm", getExposedRealmName(false, "DIGEST"));
        Assert.assertEquals(TEST_USERS_REALM, getMechanismRealmMapper(false, "DIGEST"));
        Assert.assertEquals(expected1, getMechanisms(false));
        Assert.assertEquals(factories, getHttpFactories());
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(userRealms, getPropertiesRealm());
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).size() == 1);
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_USERS_REALM));
    }

    @Test
    public void testDisableAuthManagement() throws Exception {
        {
            boolean failed = false;
            try {
                ctx.handle("security disable-http-auth-management --no-reload");
            } catch (Exception ex) {
                // XXX OK.
                failed = true;
            }
            if (!failed) {
                throw new Exception("Should have fail");
            }
        }
        {
            boolean failed = false;
            try {
                ctx.handle("security disable-sasl-management --no-reload");
            } catch (Exception ex) {
                // XXX OK.
                failed = true;
            }
            if (!failed) {
                throw new Exception("Should have fail");
            }
        }
    }

    @Test
    public void testSASLEmbedded() throws Exception {
        testEmbedded("enable-sasl-management", "disable-sasl-management");
    }

    @Test
    public void testHTTPEmbedded() throws Exception {
        testEmbedded("enable-http-auth-management", "disable-http-auth-management");
    }

    @Test
    public void testSASLCertificate() throws Exception {
        ctx.handle("/subsystem=elytron/key-store=" + TEST_KS + ":add(type=JKS, credential-reference={clear-text=pass})");
        ctx.handle("security enable-sasl-management --no-reload --mechanism=EXTERNAL"
                + " --key-store-name=" + TEST_KS + " --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_SASL_FACTORY + " --new-realm-name=" + TEST_KS_REALM);
        Assert.assertEquals(TEST_KS_REALM, getMechanismRealmMapper(true, "EXTERNAL"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_KS_REALM));

        // capture the state.
        List<ModelNode> factories = getSaslFactories();
        List<ModelNode> domains = getSecurityDomains();
        List<ModelNode> mappers = getConstantRealmMappers();
        List<ModelNode> ksRealms = getKSRealms();

        // Re-enable simply by re-using same key-store-realm, no changes expected.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=EXTERNAL"
                + " --key-store-realm-name=" + TEST_KS_REALM);
        Assert.assertEquals(TEST_KS_REALM, getMechanismRealmMapper(true, "EXTERNAL"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_KS_REALM));

        Assert.assertEquals(factories, getSaslFactories());
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(ksRealms, getKSRealms());

        ctx.handle("security disable-sasl-management --no-reload");

        // Re-enable simply by re-using same key-store-realm with roles, no changes expected other than roles.
        ctx.handle("security enable-sasl-management --no-reload --mechanism=EXTERNAL"
                + " --key-store-realm-name=" + TEST_KS_REALM + " --roles=FOO,BAR");

        Assert.assertEquals(factories, getSaslFactories());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(ksRealms, getKSRealms());

        Assert.assertEquals(DefaultResourceNames.ROLE_MAPPER_NAME, getRoleMapper(TEST_KS_REALM, TEST_DOMAIN));
        Assert.assertTrue(getNames(Util.CONSTANT_ROLE_MAPPER).contains(DefaultResourceNames.ROLE_MAPPER_NAME));
        ModelNode roleMapper = getConstantRoleMapper(DefaultResourceNames.ROLE_MAPPER_NAME);
        List<ModelNode> lst = roleMapper.get(Util.ROLES).asList();
        Assert.assertTrue(lst.size() == 2);
        for (ModelNode r : lst) {
            if (!r.asString().equals("FOO") && !r.asString().equals("BAR")) {
                throw new Exception("Invalid roles in " + lst);
            }
        }
    }

    @Test
    public void testHTTPCertificate() throws Exception {
        ctx.handle("/subsystem=elytron/key-store=" + TEST_KS + ":add(type=JKS, credential-reference={clear-text=pass})");
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=CLIENT_CERT"
                + " --key-store-name=" + TEST_KS + " --new-security-domain-name=" + TEST_DOMAIN
                + " --new-auth-factory-name=" + TEST_HTTP_FACTORY + " --new-realm-name=" + TEST_KS_REALM);
        Assert.assertEquals(TEST_KS_REALM, getMechanismRealmMapper(false, "CLIENT_CERT"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_KS_REALM));

        // capture the state.
        List<ModelNode> factories = getHttpFactories();
        List<ModelNode> domains = getSecurityDomains();
        List<ModelNode> mappers = getConstantRealmMappers();
        List<ModelNode> ksRealms = getKSRealms();

        // Re-enable simply by re-using same key-store-realm, no changes expected.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=CLIENT_CERT"
                + " --key-store-realm-name=" + TEST_KS_REALM);
        Assert.assertEquals(TEST_KS_REALM, getMechanismRealmMapper(false, "CLIENT_CERT"));
        Assert.assertTrue(getDomainRealms(TEST_DOMAIN).contains(TEST_KS_REALM));

        Assert.assertEquals(factories, getHttpFactories());
        Assert.assertEquals(domains, getSecurityDomains());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(ksRealms, getKSRealms());

        ctx.handle("security disable-http-auth-management --no-reload");

        // Re-enable simply by re-using same key-store-realm with roles, no changes expected other than roles.
        ctx.handle("security enable-http-auth-management --no-reload --mechanism=CLIENT_CERT"
                + " --key-store-realm-name=" + TEST_KS_REALM + " --roles=FOO,BAR");

        Assert.assertEquals(factories, getHttpFactories());
        Assert.assertEquals(mappers, getConstantRealmMappers());
        Assert.assertEquals(ksRealms, getKSRealms());

        Assert.assertEquals(DefaultResourceNames.ROLE_MAPPER_NAME, getRoleMapper(TEST_KS_REALM, TEST_DOMAIN));
        List<String> names = getNames(Util.CONSTANT_ROLE_MAPPER);
        Assert.assertTrue(names.toString(), names.contains(DefaultResourceNames.ROLE_MAPPER_NAME));
        ModelNode roleMapper = getConstantRoleMapper(DefaultResourceNames.ROLE_MAPPER_NAME);
        List<ModelNode> lst = roleMapper.get(Util.ROLES).asList();
        Assert.assertTrue(lst.size() == 2);
        for (ModelNode r : lst) {
            if (!r.asString().equals("FOO") && !r.asString().equals("BAR")) {
                throw new Exception("Invalid roles in " + lst);
            }
        }
    }

    private static void testEmbedded(String enable, String disable) throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--no-color-output");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            String prompt = "[standalone@embedded /]";
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("embed-server --std-out=echo", prompt));
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security " + enable + " --no-reload", prompt));

            // Disable sasl
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security " + disable + " --no-reload", prompt));

            // Re-enable
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security " + enable + " --no-reload", prompt));

            //  Disable sasl
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security " + disable + " --no-reload", prompt));
        } finally {
            cli.destroyProcess();
        }
    }

    private static String getRoleMapper(boolean sasl, String realm) throws Exception {
        String factory = getManagementInterfaceAuthFactory(ctx, null, sasl);
        ModelNode secDomain = createOpNode("subsystem=elytron/" + (sasl ? "sasl-authentication-factory=" : "http-authentication-factory=") + factory,
                "read-attribute");
        secDomain.get("name").set("security-domain");
        String domain = client.executeForResult(secDomain).asString();

        ModelNode realms = createOpNode("subsystem=elytron/security-domain=" + domain,
                "read-attribute");
        realms.get("name").set("realms");
        for (ModelNode mn : client.executeForResult(realms).asList()) {
            if (mn.get("realm").asString().equals(realm)) {
                return mn.hasDefined("role-mapper") ? mn.get("role-mapper").asString() : null;
            }
        }
        return null;
    }

    private static List<String> getMechanisms(boolean sasl) throws Exception {
        String factory = getManagementInterfaceAuthFactory(ctx, null, sasl);
        ModelNode mecs = createOpNode("subsystem=elytron/" + (sasl ? "sasl-authentication-factory=" : "http-authentication-factory=") + factory,
                "read-attribute");
        mecs.get("name").set("mechanism-configurations");
        List<String> res = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(mecs).asList()) {
            res.add(mn.get("mechanism-name").asString());
        }
        return res;
    }

    private static String getExposedRealmName(boolean sasl, String mec) throws Exception {
        String factory = getManagementInterfaceAuthFactory(ctx, null, sasl);
        ModelNode mecs = createOpNode("subsystem=elytron/" + (sasl ? "sasl-authentication-factory=" : "http-authentication-factory=") + factory,
                "read-attribute");
        mecs.get("name").set("mechanism-configurations");
        for (ModelNode mn : client.executeForResult(mecs).asList()) {
            if (mn.get("mechanism-name").asString().equals(mec)) {
                return mn.get("mechanism-realm-configurations").asList().get(0).get("realm-name").asString();
            }
        }
        return null;
    }

    private static String getMechanismRealmMapper(boolean sasl, String mec) throws Exception {
        String factory = getManagementInterfaceAuthFactory(ctx, null, sasl);
        ModelNode mecs = createOpNode("subsystem=elytron/" + (sasl ? "sasl-authentication-factory=" : "http-authentication-factory=") + factory,
                "read-attribute");
        mecs.get("name").set("mechanism-configurations");
        for (ModelNode mn : client.executeForResult(mecs).asList()) {
            if (mn.get("mechanism-name").asString().equals(mec)) {
                return mn.get("realm-mapper").asString();
            }
        }
        return null;
    }

    private static String getRoleMapper(String realm, String securityDomain) throws Exception {
        ModelNode mecs = createOpNode("subsystem=elytron/security-domain=" + securityDomain,
                "read-attribute");
        mecs.get("name").set("realms");
        for (ModelNode mn : client.executeForResult(mecs).asList()) {
            if (mn.get("realm").asString().equals(realm)) {
                return mn.get("role-mapper").asString();
            }
        }
        return null;
    }

    private static List<String> getDomainRealms(String domain) throws Exception {
        ModelNode realms = createOpNode("subsystem=elytron/security-domain=" + domain,
                "read-attribute");
        realms.get("name").set("realms");
        List<String> lst = new ArrayList<>();
        for (ModelNode mn : client.executeForResult(realms).asList()) {
            lst.add(mn.get("realm").asString());
        }
        return lst;
    }

    private static String getManagementInterfaceAuthFactory(CommandContext ctx, String interfaceName, boolean sasl) throws Exception {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        String attribute = Util.HTTP_AUTHENTICATION_FACTORY;
        if (sasl) {
            if (interfaceName == null || interfaceName.equals(Util.HTTP_INTERFACE)) {
                attribute = Util.HTTP_UPGRADE + "." + Util.SASL_AUTHENTICATION_FACTORY;
            } else {
                attribute = Util.SASL_AUTHENTICATION_FACTORY;
            }
        }
        try {
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addNode(Util.MANAGEMENT_INTERFACE, interfaceName == null ? Util.HTTP_INTERFACE : interfaceName);
            builder.addProperty(Util.NAME, attribute);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        final ModelNode outcome = ctx.getModelControllerClient().execute(request);
        if (Util.isSuccess(outcome)) {
            boolean hasResult = outcome.has(Util.RESULT);
            if (hasResult) {
                if (outcome.get(Util.RESULT).isDefined()) {
                    return outcome.get(Util.RESULT).asString();
                } else {
                    return null;
                }
            }
        }

        throw new Exception("Error retrieving Auth factory " + outcome);
    }
}
