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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

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

    // Sink for embedding app (i.e. this class and the CLI) and embedded server writes to stdout
    private ByteArrayOutputStream logOut;

    private static StdioContext initialStdioContext;

    @BeforeClass
    public static void beforeClass() throws Exception {

        copyConfig("logging.properties", "logging.properties.backup", false);

        // Set up ability to manipulate stdout
        initialStdioContext = StdioContext.getStdioContext();
        try {
            StdioContext.install();
            uninstallStdio = true;
        } catch (IllegalStateException ignore) {
            //
        }

        serviceActivatorDeployment = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(SERVICE_ACTIVATOR_DEPLOYMENT_NAME, null);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        serviceActivatorDeploymentFile = new File(tmpDir, SERVICE_ACTIVATOR_DEPLOYMENT_NAME);
        serviceActivatorDeployment.as(ZipExporter.class).exportTo(serviceActivatorDeploymentFile, true);

        ExtensionUtils.createExtensionModule(BlockerExtension.MODULE_NAME, BlockerExtension.class, EmptySubsystemParser.class.getPackage());

        // Set jboss.bind.address so the embedded server uses the expected IP address
        System.setProperty("jboss.bind.address.management", TestSuiteEnvironment.getServerAddress());

        // Silly assertion just to stop IDEs complaining about field 'out' not being used.
        assertNotNull(out);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        copyConfig("logging.properties.backup", "logging.properties", false);
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
    }

    @Before
    public void setup() throws Exception {
        copyConfig("logging.properties.backup", "logging.properties", false);

        copyConfig("standalone.xml", "standalone-cli.xml", true);

        // Capture stdout
        logOut = new ByteArrayOutputStream();
        final StdioContext replacement = StdioContext.create(initialStdioContext.getIn(), logOut, initialStdioContext.getErr());
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(replacement));

        initCLI(false);
    }

    private static void copyConfig(String base, String newName, boolean requiresExists) throws IOException {
        File configDir = new File(ROOT, "standalone" + File.separatorChar + "configuration");
        File baseFile = new File(configDir, base);
        assertTrue(!requiresExists || baseFile.exists());
        File newFile = new File(configDir, newName);
        Files.copy(baseFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
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
        cli.sendLine("/extension=org.wildfly.extension.io:add");
        cli.sendLine("/extension=org.wildfly.extension.request-controller:add");
        cli.sendLine("run-batch");

        // Another batch for everything else
        cli.sendLine("batch");
        cli.sendLine("/core-service=management/security-realm=ManagementRealm:add(map-groups-to-roles=false)");
        cli.sendLine("/core-service=management/security-realm=ManagementRealm/authentication=local:add(default-user=\"$local\",skip-group-loading=true)");
        cli.sendLine("/core-service=management/security-realm=ManagementRealm/authentication=properties:add(path=mgmt-users.properties,relative-to=jboss.server.config.dir)");
        cli.sendLine("/core-service=management/security-realm=ManagementRealm/authorization=properties:add(path=mgmt-groups.properties,relative-to=jboss.server.config.dir)");
        cli.sendLine("/core-service=management/security-realm=ApplicationRealm:add");
        cli.sendLine("/core-service=management/security-realm=ApplicationRealm/authentication=local:add(default-user=\"$local\",allowed-users=*,skip-group-loading=true)");
        cli.sendLine("/core-service=management/security-realm=ApplicationRealm/authentication=properties:add(path=application-users.properties,relative-to=jboss.server.config.dir)");
        cli.sendLine("/core-service=management/security-realm=ApplicationRealm/authorization=properties:add(path=application-roles.properties,relative-to=jboss.server.config.dir)");
        cli.sendLine("/core-service=management/access=audit:add");
        cli.sendLine("/core-service=management/access=audit/json-formatter=json-formatter:add");
        cli.sendLine("/core-service=management/access=audit/file-handler=file:add(formatter=json-formatter,relative-to=jboss.server.data.dir,path=audit-log.log)");
        cli.sendLine("/core-service=management/access=audit/logger=audit-log:add(log-boot=true,log-read-only=false,enabled=true)");
        cli.sendLine("/core-service=management/access=audit/logger=audit-log/handler=file:add");
        cli.sendLine("/core-service=management/management-interface=http-interface:add(security-realm=ManagementRealm,http-upgrade-enabled=true,socket-binding=management-http)");
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
        cli.sendLine("/subsystem=remoting:add");
        cli.sendLine("/subsystem=remoting/configuration=endpoint:add(worker=default)");
        cli.sendLine("/subsystem=remoting/http-connector=http-remoting-connector:add(connector-ref=default,security-realm=ApplicationRealm)");
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
     * @throws IOException
     */
    @Test
    public void testDefaultServerConfig() throws IOException {
        validateServerConnectivity("");
    }

    /**
     * Test the -c shorthand for specifying a server config works.
     * @throws IOException
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
        copyConfig("standalone.xml", "standalone-cli.xml", true);
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
     * @throws IOException
     */
    @Test
    public void testHelp() throws IOException {
        cli.sendLine("embed-server --help");
        checkLogging("embed-server");

        String line = "embed-server --server-config=standalone-cli.xml " + JBOSS_HOME;
        cli.sendLine(line);
        assertTrue(cli.isConnected());

        cli.sendLine("stop-embedded-server --help");
        checkLogging("stop-embedded-server");

    }

    private void assertState(String expected, int timeout) throws IOException, InterruptedException {
        long done = timeout < 1 ? 0 : System.currentTimeMillis() + timeout;
        String history = "";
        String state = null;
        do {
            try {
                cli.sendLine(":read-attribute(name=server-state)", true);
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
        List<String> output = getOutputLines(logOutput);
        for (String s : output) {
            if (s.contains(line)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getOutputLines(String raw) throws IOException {
        if (raw == null) {
            return Collections.emptyList();
        }
        BufferedReader br = new BufferedReader(new StringReader(raw));
        List<String> result = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        return result;
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
}
