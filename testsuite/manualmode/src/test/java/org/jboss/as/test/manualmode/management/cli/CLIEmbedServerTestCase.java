/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
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
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
 * Tests embedding a server in the CLI.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class CLIEmbedServerTestCase extends AbstractCliTestBase {

    /**
     * Use this if you are debugging and want to look at the original System.out output,
     * as the tests play tricks with it later.
     */
    private static final PrintStream out = System.out;
    private static final File ROOT = new File(System.getProperty("jboss.home"));
    private static final String JBOSS_HOME = " --jboss-home=" + ROOT.getAbsolutePath();
    private static final String STOP = "stop-embedded-server";
    private static final String SERVICE_ACTIVATOR_DEPLOYMENT_NAME = "service-activated.jar";
    private static JavaArchive serviceActivatorDeployment;
    private static File serviceActivatorDeploymentFile;
    private static boolean uninstallStdio;

    private static final String JBOSS_SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String JBOSS_SERVER_DEPLOY_DIR = "jboss.server.deploy.dir";
    private static final String JBOSS_SERVER_TEMP_DIR = "jboss.server.temp.dir";
    private static final String JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";
    private static final String JBOSS_SERVER_DATA_DIR = "jboss.server.data.dir";
    private static final String JBOSS_CONTROLLER_TEMP_DIR = "jboss.controller.temp.dir";

    private static final String[] SERVER_PROPS = {
        JBOSS_SERVER_BASE_DIR,  JBOSS_SERVER_CONFIG_DIR,  JBOSS_SERVER_DEPLOY_DIR,
        JBOSS_SERVER_TEMP_DIR,  JBOSS_SERVER_LOG_DIR, JBOSS_SERVER_DATA_DIR
    };

    // Sink for embedding app (i.e. this class and the CLI) and embedded server writes to stdout
    private ByteArrayOutputStream logOut;

    private static StdioContext initialStdioContext;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Assume.assumeFalse("This test does not work with the IBM J9 JVM. There seems to be an issue with stdout" +
                " logging.", TestSuiteEnvironment.isIbmJvm());

        // Initialize the log manager before the STDIO context is initialized. This ensures that any capturing of the
        // standard output streams in the log manager is done before they are replaced by the stdio context.
        Class.forName("org.jboss.logmanager.LogManager", true, CLIEmbedServerTestCase.class.getClassLoader());

        CLIEmbedUtil.copyConfig(ROOT, "standalone", "logging.properties", "logging.properties.backup", false);

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
        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class, EmptySubsystemParser.class.getPackage());

        // Set jboss.bind.address so the embedded server uses the expected IP address
        System.setProperty("jboss.bind.address.management", TestSuiteEnvironment.getServerAddress());

        // Silly assertion just to stop IDEs complaining about field 'out' not being used.
        assertNotNull(out);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        if (!TestSuiteEnvironment.isIbmJvm()) {
            CLIEmbedUtil.copyConfig(ROOT, "standalone", "logging.properties.backup", "logging.properties", false);
            try {
                StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(initialStdioContext));
            } finally {
                if (uninstallStdio) {
                    StdioContext.uninstall();
                }
            }

            if (serviceActivatorDeploymentFile != null) {
                Files.deleteIfExists(serviceActivatorDeploymentFile.toPath());
            }

            ExtensionUtils.deleteExtensionModule(BlockerExtension.MODULE_NAME);
            ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);
        }
    }

    @Before
    public void setup() throws Exception {
        CLIEmbedUtil.copyConfig(ROOT, "standalone", "logging.properties.backup", "logging.properties", false);

        CLIEmbedUtil.copyConfig(ROOT, "standalone", "standalone.xml", "standalone-cli.xml", true);

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

        // Use --admin-only=false or the logging subsystem won't log anyway
        String line = "embed-server --admin-only=false --server-config=standalone-cli.xml " + stdoutParam + JBOSS_HOME;
        cli.sendLine(line);
        if (expectServerLogging) {
            checkLogging("WFLYSRV0025", TimeoutUtil.adjust(30000));
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

    private void checkClientSideLogging() throws IOException, InterruptedException {
        String text = "test." + System.nanoTime();
        Logger.getLogger(text).error(text);
        checkLogging(text);
    }

    /** Confirms that low level and high level reloads work */
    @Test
    public void testReload() throws Exception {

        // Don't use admin-only or reload-required won't be triggered
        String line = "embed-server --admin-only=false --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/subsystem=request-controller:remove");
        assertState("reload-required", 0);
        // Low level
        cli.sendLine(":reload");
        assertState("running", TimeoutUtil.adjust(30000));
        cli.sendLine("/subsystem=request-controller:add");
        assertState("reload-required", 0);
        // High level
        cli.sendLine("reload");
        assertState("running", TimeoutUtil.adjust(30000));
    }

    /** Confirms that the low and high level shutdown commands are not available */
    @Test
    public void testShutdownNotAvailable() throws Exception {

        String line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
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
        String line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("stop-embedded-server");
    }

    /** Tests the standard cli 'deploy' command */
    @Test
    public void testFileDeploy() throws Exception {
        validateServerConnectivity();

        cli.sendLine("deploy " + serviceActivatorDeploymentFile.getAbsolutePath());

        // We could use the CLI to do this, but since the code using MCC already exists, re-use it
        ModelControllerClient mcc = cli.getCommandContext().getModelControllerClient();
        ServiceActivatorDeploymentUtil.validateProperties(mcc);
    }

    /**
     * Tests accessing a ModelControllerClient from the cli, and using it to deploy with the de
     * deployment content attached as a stream.
     */
    @Test
    public void testModelControllerDeploy() throws Exception {
        validateServerConnectivity();

        ModelNode opNode = new ModelNode();
        opNode.get(OP).set(ADD);
        opNode.get(OP_ADDR).add(DEPLOYMENT, SERVICE_ACTIVATOR_DEPLOYMENT_NAME);
        opNode.get(ENABLED).set(true);
        ModelNode content = opNode.get(CONTENT).add();
        content.get(INPUT_STREAM_INDEX).set(0);

        InputStream is = serviceActivatorDeployment.as(ZipExporter.class).exportAsInputStream();
        Operation op = Operation.Factory.create(opNode, Collections.singletonList(is), true);

        ModelControllerClient mcc = cli.getCommandContext().getModelControllerClient();
        ModelNode response = mcc.execute(op);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());

        // We could use the CLI to do this, but since we have the MCC and the code already exists, re-use it
        ServiceActivatorDeploymentUtil.validateProperties(mcc);
    }

    /** Tests building an entire server config from scratch */
    @Test
    public void testBuildServerConfig() throws Exception {

        String line = "embed-server --server-config=standalone-cli.xml --empty-config --remove-existing " + JBOSS_HOME;
        cli.sendLine(line);

        // One batch for extensions so their API is available for the ops in the 2nd batch
        cli.sendLine("batch");
        cli.sendLine("/extension=org.jboss.as.deployment-scanner:add");
        cli.sendLine("/extension=org.jboss.as.jmx:add");
        cli.sendLine("/extension=org.jboss.as.logging:add");
        cli.sendLine("/extension=org.jboss.as.remoting:add");
        cli.sendLine("/extension=org.wildfly.extension.elytron:add");
        cli.sendLine("/extension=org.wildfly.extension.io:add");
        cli.sendLine("/extension=org.wildfly.extension.request-controller:add");
        cli.sendLine("run-batch");

        // Another batch for everything else
        cli.sendLine("batch");
        cli.sendLine("/core-service=management/access=audit:add");
        cli.sendLine("/core-service=management/access=audit/json-formatter=json-formatter:add");
        cli.sendLine("/core-service=management/access=audit/file-handler=file:add(formatter=json-formatter,relative-to=jboss.server.data.dir,path=audit-log.log)");
        cli.sendLine("/core-service=management/access=audit/logger=audit-log:add(log-boot=true,log-read-only=false,enabled=true)");
        cli.sendLine("/core-service=management/access=audit/logger=audit-log/handler=file:add");
        cli.sendLine("/core-service=management/management-interface=http-interface:add(http-authentication-factory=management-http-authentication,http-upgrade={sasl-authentication-factory=management-sasl-authentication,enabled=true},socket-binding=management-http)");
        cli.sendLine("/core-service=management/access=authorization:write-attribute(name=provider,value=simple)");
        cli.sendLine("/core-service=management/access=authorization/role-mapping=SuperUser:add");
        cli.sendLine("/core-service=management/access=authorization/role-mapping=SuperUser/include=\"user-$local\":add(type=user,name=\"$local\")");
        cli.sendLine("/interface=management:add(inet-address=${jboss.bind.address.management:127.0.0.1})");
        cli.sendLine("/interface=public:add(inet-address=${jboss.bind.address:127.0.0.1})");
        cli.sendLine("/interface=unsecure:add(inet-address=${jboss.bind.address.unsecure:127.0.0.1})");
        cli.sendLine("/socket-binding-group=standard-sockets:add(default-interface=public,port-offset=${jboss.socket.binding.port-offset:0})");
        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=management-http:add(interface=management,port=${jboss.management.http.port:9990})");
        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=management-https:add(interface=management,port=${jboss.management.https.port:9993})");
        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=http:add(port=${jboss.http.port:8080})");
        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=https:add(port=${jboss.https.port:8443})");
        cli.sendLine("/subsystem=logging:add");
        cli.sendLine("/subsystem=logging/pattern-formatter=PATTERN:add(pattern=\"%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n\")");
        cli.sendLine("/subsystem=logging/pattern-formatter=COLOR-PATTERN:add(pattern=\"%K{level}%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n\")");
        cli.sendLine("/subsystem=logging/console-handler=CONSOLE:add(level=INFO,named-formatter=COLOR-PATTERN)");
        cli.sendLine("/subsystem=logging/periodic-rotating-file-handler=FILE:add(autoflush=true,named-formatter=PATTERN,file={relative-to=jboss.server.log.dir,path=server.log},suffix=.yyyy-MM-dd,append=true)");
        cli.sendLine("/subsystem=logging/logger=com.arjuna:add(level=WARN)");
        cli.sendLine("/subsystem=logging/logger=org.jboss.as.config:add(level=DEBUG)");
        cli.sendLine("/subsystem=logging/logger=sun.rmi:add(level=WARN)");
        cli.sendLine("/subsystem=logging/logger=jacorb:add(level=WARN)");
        cli.sendLine("/subsystem=logging/logger=jacorb.config:add(level=ERROR)");
        cli.sendLine("/subsystem=logging/root-logger=ROOT:add(level=INFO,handlers=[CONSOLE,FILE])");
        cli.sendLine("/subsystem=deployment-scanner:add");
        cli.sendLine("/subsystem=deployment-scanner/scanner=default:add(path=deployments,relative-to=jboss.server.base.dir,scan-interval=5000,runtime-failure-causes-rollback=\"${jboss.deployment.scanner.rollback.on.failure:false}\")");
        cli.sendLine("/subsystem=io:add");
        cli.sendLine("/subsystem=io/worker=default:add");
        cli.sendLine("/subsystem=io/buffer-pool=default:add");
        cli.sendLine("/subsystem=jmx:add");
        cli.sendLine("/subsystem=jmx/expose-model=resolved:add");
        cli.sendLine("/subsystem=jmx/expose-model=expression:add");
        cli.sendLine("/subsystem=jmx/remoting-connector=jmx:add");
        cli.sendLine("/subsystem=remoting:add(worker=default)");
        cli.sendLine("/subsystem=remoting/http-connector=http-remoting-connector:add(connector-ref=default,security-realm=ApplicationRealm)");
        CLIEmbedUtil.configureElytronManagement(cli, null);
        cli.sendLine("/subsystem=elytron/security-domain=ApplicationDomain:add(permission-mapper=default-permission-mapper,default-realm=ApplicationRealm,realms=[{realm=ApplicationRealm,role-decoder=groups-to-roles},{realm=local}])");
        cli.sendLine("/subsystem=elytron/properties-realm=ApplicationRealm:add(users-properties={path=application-users.properties,relative-to=jboss.server.config.dir,digest-realm-name=ApplicationRealm},groups-properties={path=application-roles.properties,relative-to=jboss.server.config.dir})");
        cli.sendLine("/subsystem=elytron/sasl-authentication-factory=application-sasl-authentication:add(security-domain=ApplicationDomain,sasl-server-factory=configured,mechanism-configurations=[{mechanism-name=JBOSS-LOCAL-USER,realm-mapper=local},{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=ApplicationRealm}]}])");
        cli.sendLine("/subsystem=elytron/key-store=applicationKS:add(type=JKS,credential-reference={clear-text=password},path=application.keystore,relative-to=jboss.server.config.dir)");
        cli.sendLine("/subsystem=elytron/key-manager=applicationKM:add(key-store=applicationKS,credential-reference={clear-text=password},generate-self-signed-certificate-host=localhost)");
        cli.sendLine("/subsystem=elytron/server-ssl-context=applicationSSC:add(key-manager=applicationKM)");
        cli.sendLine("deploy " + serviceActivatorDeploymentFile.getAbsolutePath());
        cli.sendLine("run-batch");

        assertState("reload-required", 0);

        cli.sendLine("reload --admin-only=false");
        assertState("running", TimeoutUtil.adjust(30000));

        // We could use the CLI to do this, but since the code using MCC already exists, re-use it
        ModelControllerClient mcc = cli.getCommandContext().getModelControllerClient();
        ServiceActivatorDeploymentUtil.validateProperties(mcc);
    }

    /**
     * Test not specifying a server config works.
     */
    @Test
    public void testDefaultServerConfig() throws IOException {
        validateServerConnectivity("");
    }

    /**
     * Test the -c shorthand for specifying a server config works.
     */
    @Test
    public void testDashC() throws IOException {
        validateServerConnectivity("-c=standalone-cli.xml");
    }

    /** Confirms that the --empty-config param will trigger failure if the specified config exists */
    @Test
    public void testRejectEmptyExistingConfig() throws Exception {

        assertFalse(cli.isConnected());

        String line = "embed-server --server-config=standalone-cli.xml --empty-config " + JBOSS_HOME;
        cli.sendLine(line, true);
        assertFalse(cli.isConnected());
    }

    /**
     * Tests using and empty config and also that the --remove-existing param will allow use of the
     * --empty-config param regardless of whether the specified config exists.
     */
    @Test
    public void testAllowEmptyExistingConfig() throws Exception {

        assertFalse(cli.isConnected());
        // Confirm starting with --empty-config and --remove-config succeeds even if the file doesn't exist
        File f = new File(ROOT, "standalone" + File.separatorChar + "configuration" + File.separatorChar + "standalone-cli.xml");
        if (f.exists()) {
            Files.delete(f.toPath());
        }
        String line = "embed-server --server-config=standalone-cli.xml --empty-config --remove-existing " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        assertTrue(f.exists());
        assertEquals(0, countExtensions());

        // Config file is still empty. Confirm we can reload w/ an empty config
        cli.sendLine("reload");
        assertEquals(0, countExtensions());

        cli.sendLine("stop-embedded-server");
        assertFalse(cli.isConnected());

        // Validate file was created and that we can boot from it even though it is empty
        line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertEquals(0, countExtensions());

        // Config file is still empty. Confirm we can reload w/ an empty config
        cli.sendLine("reload");
        assertEquals(0, countExtensions());

        cli.sendLine("stop-embedded-server");
        assertFalse(cli.isConnected());

        // Confirm starting with --empty-config and --remove-existing succeeds even if the file exists
        CLIEmbedUtil.copyConfig(ROOT, "standalone", "standalone.xml", "standalone-cli.xml", true);
        line = "embed-server --server-config=standalone-cli.xml --empty-config --remove-existing " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        assertEquals(0, countExtensions());

        cli.sendLine("stop-embedded-server");
        assertFalse(cli.isConnected());

        // Validate file was created and that we can boot from it even though it is empty
        line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertEquals(0, countExtensions());
    }

    /** Tests the stop-embedded-server command */
    @Test
    public void testStopEmbeddedServer() throws IOException {
        validateServerConnectivity();
        cli.sendLine("stop-embedded-server");
        assertFalse(cli.isConnected());
        validateRemoteConnection(false);
    }

    /** Tests that the quit command stops any embedded server */
    @Test
    public void testStopServerOnQuit() throws IOException {
        validateServerConnectivity();
        cli.sendLine("quit");
        assertFalse(cli.isConnected());
        validateRemoteConnection(false);
    }

    /** Tests the the CommandContext terminateSession method stops any embedded server */
    @Test
    public void testStopServerOnTerminateSession() throws IOException {
        validateServerConnectivity();
        cli.getCommandContext().terminateSession();
        assertFalse(cli.isConnected());
        validateRemoteConnection(false);
    }

    /**
     * Tests the --help param works.
     */
    @Test
    public void testHelp() throws IOException, InterruptedException {
        cli.sendLine("embed-server --help");
        checkLogging("embed-server");

        String line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());

        cli.sendLine("stop-embedded-server --help");
        checkLogging("stop-embedded-server");

    }

    @Test
    public void testBaseDir() throws IOException, InterruptedException {
        String currBaseDir = null;
        final String newStandalone = "CLIEmbedServerTestCaseStandaloneTmp";

        assertFalse(cli.isConnected());

        try {
            // save the current value
            currBaseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_BASE_DIR, null);
            CLIEmbedUtil.copyServerBaseDir(ROOT, "standalone", newStandalone, true);
            //CLIEmbedUtil.copyConfig(ROOT, newStandalone, "logging.properties.backup", "logging.properties", false);

            String newBaseDir = ROOT + File.separator + newStandalone;
            setProperties(newBaseDir);

            String line = "embed-server --std-out=echo " + JBOSS_HOME;
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            assertPath(JBOSS_SERVER_BASE_DIR, ROOT + File.separator + newStandalone);
            assertPath(JBOSS_SERVER_CONFIG_DIR, ROOT + File.separator + newStandalone + File.separator + "configuration");
            assertPath(JBOSS_SERVER_DATA_DIR, ROOT + File.separator + newStandalone + File.separator + "data");
            assertPath(JBOSS_SERVER_LOG_DIR, ROOT + File.separator + newStandalone + File.separator + "log");
            assertPath(JBOSS_SERVER_TEMP_DIR, ROOT + File.separator + newStandalone + File.separator + "tmp");
            assertPath(JBOSS_CONTROLLER_TEMP_DIR, ROOT + File.separator + newStandalone + File.separator + "tmp");

            cli.sendLine("/system-property=" + newStandalone + ":add(value=" + newStandalone +")");
            assertProperty(newStandalone, newStandalone, false);

            // WFCORE-1187, when this overrides logging.properties correctly, we can check this
            // for now it will refer to the previously persisted log file in standalone/configuration/logging.properties
            //File f = new File(ROOT + File.separator + newStandalone + File.separator + "log" + File.separator + "server.log");
            //assertTrue(f.exists());
            //assertTrue(f.length() > 0);

            // stop the hc, and restart it with default properties
            cli.sendLine("stop-embedded-server");

            setProperties(null);
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            // shouldn't be set
            assertProperty(newStandalone, null, true);
            cli.sendLine("stop-embedded-server");

            setProperties(newBaseDir);
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            assertProperty(newStandalone, newStandalone, false);

        } finally {
            cli.sendLine("stop-embedded-server");
            // restore the original
            setProperties(currBaseDir);
            FileUtils.deleteDirectory(new File(ROOT + File.separator + newStandalone));
        }
    }

    @Test
    public void testLogDir() throws IOException, InterruptedException {
        testPathDir(JBOSS_SERVER_LOG_DIR, "log");
    }

    @Test
    public void testTempDir() throws IOException, InterruptedException {
        testPathDir(JBOSS_SERVER_TEMP_DIR, "temp");
    }

    @Test
    public void testDataDir() throws IOException, InterruptedException {
        testPathDir(JBOSS_SERVER_DATA_DIR, "data");
    }

    @Test
    public void testConfigDir() throws IOException, InterruptedException {
        testPathDir(JBOSS_SERVER_CONFIG_DIR, "configuration");
    }

    @Test
    public void testLogDirProperty() throws IOException, InterruptedException {
        String currBaseDir = null;
        final String newStandalone = "CLIEmbedServerTestCaseStandaloneTmp";

        assertFalse(cli.isConnected());

        try {
            // save the current value
            currBaseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_BASE_DIR, null);
            CLIEmbedUtil.copyServerBaseDir(ROOT, "standalone", newStandalone, true);

            String newBaseDir = ROOT + File.separator + newStandalone;
            setProperties(newBaseDir);

            String line = "embed-server --std-out=echo " + JBOSS_HOME;
            cli.sendLine(line);
            assertTrue(cli.isConnected());
            assertPath(JBOSS_SERVER_BASE_DIR, ROOT + File.separator + newStandalone);
            assertPath(JBOSS_SERVER_CONFIG_DIR, ROOT + File.separator + newStandalone + File.separator + "configuration");
            assertPath(JBOSS_SERVER_DATA_DIR, ROOT + File.separator + newStandalone + File.separator + "data");
            assertPath(JBOSS_SERVER_LOG_DIR, ROOT + File.separator + newStandalone + File.separator + "log");
            assertPath(JBOSS_SERVER_TEMP_DIR, ROOT + File.separator + newStandalone + File.separator + "tmp");
            assertPath(JBOSS_CONTROLLER_TEMP_DIR, ROOT + File.separator + newStandalone + File.separator + "tmp");

        } finally {
            cli.sendLine("stop-embedded-server");
            // restore the original
            setProperties(currBaseDir);
            FileUtils.deleteDirectory(new File(ROOT + File.separator + newStandalone));
        }
    }

    @Test
    public void testRBACEnabled() throws IOException, InterruptedException {
        String line = "embed-server --admin-only=false --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        cli.sendLine("/core-service=management/access=authorization:write-attribute(name=provider,value=rbac");
        assertState("reload-required", 0);
        cli.sendLine("reload --admin-only=true");
        assertState("running", TimeoutUtil.adjust(30000));
    }

    @Test
    public void testPrivateHiddenOperations() throws IOException {
        validateServerConnectivity();

        try {
            // Setup
            cli.sendLine("/extension=" + OpTypesExtension.EXTENSION_NAME +":add", true);
            cli.readAllAsOpResult();
            cli.sendLine("/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":add", true);
            cli.readAllAsOpResult();

            cli.sendLine("/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":hidden", true);
            CLIOpResult result = cli.readAllAsOpResult();
            assertTrue(result.getResponseNode().toString(), result.isIsOutcomeSuccess());
            cli.sendLine("/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":private", true);
            result = cli.readAllAsOpResult();
            assertFalse(result.getResponseNode().toString(), result.isIsOutcomeSuccess());
        } finally {
            try {
                cli.sendLine("/subsystem=" + OpTypesExtension.SUBSYSTEM_NAME +":remove", true);
                cli.readAllAsOpResult();
            } finally {
                cli.sendLine("/extension=" + OpTypesExtension.EXTENSION_NAME +":remove", true);
                cli.readAllAsOpResult();
            }
        }
    }

    private void assertPath(final String path, final String expected) throws IOException, InterruptedException {
            cli.sendLine("/path=" + path + " :read-attribute(name=path)", true);
            CLIOpResult result = cli.readAllAsOpResult();
            ModelNode resp = result.getResponseNode();
            ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
            assertEquals(expected, stateNode.asString());
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
        assertState(expected,timeout, ":read-attribute(name=server-state)");
    }

    private void checkNoLogging(String line) throws IOException {
        String output = readLogOut();
        assertFalse(output, checkLogging(output, line));
    }

    private String readLogOut() {
        if (logOut.size() > 0) {
            String output = new String(logOut.toByteArray(), StandardCharsets.UTF_8).trim();
            logOut.reset();
            return output;
        }
        return null;
    }

    private void checkLogging(String line) throws IOException, InterruptedException {
        checkLogging(line, 0);
    }

    private void checkLogging(String line, int timeout) throws IOException, InterruptedException {
        String logOutput = readLogOut();
        long done = System.currentTimeMillis() + timeout;
        while (timeout > 0 && System.currentTimeMillis() < done) {
            if (checkLogging(logOutput, line)) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
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

    private int countExtensions() throws IOException {
        cli.sendLine(":read-children-names(child-type=extension");
        CLIOpResult result = cli.readAllAsOpResult();
        return ((List) result.getFromResponse(RESULT)).size();
    }

    private void validateRemoteConnection(boolean expectSuccess) {
        ModelControllerClient mcc = null;
        try {
            mcc = TestSuiteEnvironment.getModelControllerClient();
            ModelNode op = new ModelNode();
            op.get(OP).set(READ_RESOURCE_OPERATION);
            ModelNode response = mcc.execute(op);
            assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());

        } catch (Exception e) {
            if (expectSuccess) {
                e.printStackTrace();
                fail("Cannot connect remotely: " + e.toString());
            }
        } finally {
            StreamUtils.safeClose(mcc);
        }
    }

    private void validateServerConnectivity() throws IOException {
        validateServerConnectivity("--server-config=standalone-cli.xml");
    }

    private void validateServerConnectivity(String serverConfigParam) throws IOException {
        assertFalse(cli.isConnected());
        validateRemoteConnection(false);
        // Don't use admin-only or reload-required won't be triggered
        String line = "embed-server --admin-only=false " + serverConfigParam + " " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());
        validateRemoteConnection(true);
    }

    private void testPathDir(final String propName, final String value) throws IOException, InterruptedException {
        String currBaseDir = null;
        final String newStandalone = "CLIEmbedServerTestCaseStandaloneTmp";
        assertFalse(cli.isConnected());
        try {
            // save the current value
            currBaseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_BASE_DIR, null);
            // The current directory isn't set until the embedded server is started, just use the root directory if the
            // property was not previously set.
            if (currBaseDir == null) {
                currBaseDir = ROOT + File.separator + "standalone";
            }
            CLIEmbedUtil.copyServerBaseDir(ROOT, "standalone", newStandalone, true);
            String newBaseDir = ROOT + File.separator + newStandalone;
            WildFlySecurityManager.setPropertyPrivileged(propName, newBaseDir + File.separator + value);
            String line = "embed-server --std-out=echo " + JBOSS_HOME;
            cli.sendLine(line);
            assertTrue(cli.isConnected());

            for(String prop : SERVER_PROPS) {
                String overriddenDir = ROOT + File.separator + newStandalone + File.separator + value;
                if (prop.equals(propName)) {
                    assertPath(propName, overriddenDir);
                } else {
                    // just make sure the unchanged property has the default basedir
                    // if the changed property is jboss.server.data.dir, property jboss.server.deploy.dir will be changed as well
                    if (prop.equals(JBOSS_SERVER_DEPLOY_DIR) && propName.equals(JBOSS_SERVER_DATA_DIR)) {
                        assertTrue(WildFlySecurityManager.getPropertyPrivileged(prop, "").contains(overriddenDir));
                    } else {
                        assertTrue(WildFlySecurityManager.getPropertyPrivileged(prop, "").contains(currBaseDir));
                    }
                }
            }
        } finally {
            // stop the server
            cli.sendLine("stop-embedded-server");
            // restore the original
            setProperties(currBaseDir);
            FileUtils.deleteDirectory(new File(ROOT + File.separator + newStandalone));
        }
    }

    private void setProperties(final String newBaseDir) {
        if (newBaseDir == null) {
            for (String prop : SERVER_PROPS) {
                WildFlySecurityManager.clearPropertyPrivileged(prop);
            }
            return;
        }
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_BASE_DIR, newBaseDir);
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_CONFIG_DIR, newBaseDir + File.separator + "configuration");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_DATA_DIR, newBaseDir + File.separator + "data");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_LOG_DIR, newBaseDir + File.separator + "log");
        WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_TEMP_DIR, newBaseDir + File.separator + "tmp");
    }

}
