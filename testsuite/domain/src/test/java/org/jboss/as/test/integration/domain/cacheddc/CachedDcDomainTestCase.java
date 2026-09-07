/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.cacheddc;

import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.impl.DomainClientImpl;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Testing <code>--cached-dc</code> configuration option.<br>
 * Tests shows that <code>domain.cached-remote.xml</code> is created on secondary HC
 * and that secondary HC is capable to start when that file exists even DC is down.
 *
 * @author <a href="ochaloup@redhat.com">Ondra Chaloupka</a>
 */
public class CachedDcDomainTestCase {

    private static Logger log = Logger.getLogger(CachedDcDomainTestCase.class);

    private static final long TIMEOUT_S = TimeoutUtil.adjust(30);
    private static final long NOT_STARTED_TIMEOUT_S = TimeoutUtil.adjust(7);
    private static final int TIMEOUT_SLEEP_MILLIS = 50;

    private static final String TEST_LOGGER_NAME = "org.jboss.test";
    private static final String DOMAIN_CACHED_REMOTE_XML_FILE_NAME = "domain.cached-remote.xml";

    private DomainTestSupport.Configuration domainConfig;
    private DomainTestSupport domainManager;

    @Before
    public void specifyDomainConfig() throws Exception {
        domainConfig = DomainTestSupport.Configuration.create(CachedDcDomainTestCase.class.getSimpleName(),
            "domain-configs/domain-minimal.xml", "host-configs/host-primary-cacheddc.xml", "host-configs/host-secondary-cacheddc.xml");

        // removing domain.cached-remote.xml if exists
        getDomainCachedRemoteXmlFile(domainConfig.getSecondaryConfiguration()).delete();
    }

    @After
    public void stopDomain() {
        if(domainManager != null) {
            domainManager.close();
        }
    }

    /**
     * DC is down, HC successfully starts if parameter <code>--cached-dc</code> is used
     * and cached configuration is available
     */
    @Test
    public void hcStartsWhenCachedConfigAvailable_CachedDCParamIsSet() throws Exception {
        domainConfig.getSecondaryConfiguration()
            .setCachedDC(true)
            .setBackupDC(false);

        test_hcStartsWhenCachedConfigAvailable(domainConfig);
    }

    /**
     * DC is down, HC successfully starts if parameters <code>--backup --cached-dc</code> is used together
     * and cached configuration is available (see WFCORE-317)
     */
    @Test
    public void hcStartsWhenCachedConfigAvailable_CachedAndBackupDCParamIsSet() throws Exception {
        domainConfig.getSecondaryConfiguration()
            .setCachedDC(true)
            .setBackupDC(true);

        test_hcStartsWhenCachedConfigAvailable(domainConfig);
    }

    /**
     * DC is up, HC successfully starts if parameter --backup, both HC and DC is put down
     * and when HC is subsequently started with --cached-dc it uses cached file created during start-up
     * with --backup param and HC is succesfully started
     */
    @Test
    public void hcStartsWhenCachedConfigAvailable_BackupDCParamIsSet() throws Exception {
        domainConfig.getSecondaryConfiguration()
            .setCachedDC(false)
            .setBackupDC(true);

        test_hcStartsWhenCachedConfigAvailable(domainConfig);
    }

    /**
     * DC is down, HC fails to start if parameter <code>--cached-dc</code> is used but
     * no cached configuration is available
     */
    @Test
    public void hcNotStartsWhenCachedConfigNotAvailable_CachedDCParamIsSet() throws Exception {
        domainConfig.getSecondaryConfiguration()
            .setCachedDC(true)
            .setBackupDC(false);

        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.getDomainSecondaryLifecycleUtil().startAsync();

        // expecting that HC is not started
        try (final DomainClient client = getDomainClient(domainConfig.getSecondaryConfiguration())) {
            waitForHostControllerBeingStarted(NOT_STARTED_TIMEOUT_S, client);
            Assert.fail("DC not started, domain.cached-remote.xml does not exist but "
                + "secondary host controller was started ");
        } catch (IllegalStateException ise) {
            // as HC should not be started IllegalStateException is expected
        }
    }

    /**
     * DC is down, HC waits for DC to be started when <code>--backup</code> parameter is used
     */
    @Test
    public void hcWaitsForDCBeingStarted_BackupDCParamIsSet() throws Exception {
        domainConfig.getSecondaryConfiguration()
            .setCachedDC(false)
            .setBackupDC(true);

        test_hcWaitsForDcBeingStarted(domainConfig);
    }

    /**
     * DC is up, HC is started with <code>--cached-dc</code> then domain configuration is passed from DC to HC
     * (cached configuration is created for HC)
     * a configuration change is done on DC then such change is propagated to HC cached configuration
     */
    @Test
    public void dcConfigChangePropagated() throws Exception {
        domainConfig.getSecondaryConfiguration().setCachedDC(true);
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();

        // adding logger to profile "other" connected to DC
        domainManager.getDomainPrimaryLifecycleUtil()
            .executeForResult(getCreateOperationTestLoggingCategory());

        domainManager.stopHosts();

        domainManager.getDomainSecondaryLifecycleUtil().start();
        checkTestLoggerFromSecondaryHost(domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
    }

    /**
     * DC is up, HC is started with <code>--cached-dc</code> then domain configuration is passed from DC to HC
     * (cached configuration is created for HC).
     * HC is stopped and configuration change is done at DC. DC is stopped.
     * DC is down, HC is started with --cached-dc and as cached configuration file exists it's used.
     * HC is started and configuration change should be propagated and applied to HC.
     */
    @Test
    public void dcConfigChangePropagatedAfterRestart() throws Exception {
        domainConfig.getSecondaryConfiguration().setCachedDC(true);
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();

        stopSecondaryAndWaitForUnregistration();

        assertEquals(false, domainManager.getDomainSecondaryLifecycleUtil().isHostControllerStarted());
        domainManager.getDomainPrimaryLifecycleUtil()
            .executeForResult(getCreateOperationTestLoggingCategory());
        domainManager.stopHosts();

        // starting domain once again - reusing already changed config files
        domainConfig.getPrimaryConfiguration().setRewriteConfigFiles(false);
        domainConfig.getSecondaryConfiguration().setRewriteConfigFiles(false);

        // HC started with old domain.cached-remote.xml
        domainManager.getDomainSecondaryLifecycleUtil().start();

        // DC started where domain config contains a configuration change (a new logger was created)
        domainManager.getDomainPrimaryLifecycleUtil().start();

        // timeout waits to get changes from DC propagated to HC
        // by WFCORE-2331 the host controller log file should advert need of configuration change
        runWithTimeout(TIMEOUT_S, () -> checkHostControllerLogFile(domainConfig.getSecondaryConfiguration(), "WFLYHC0202"));
    }

    /**
     * <p>
     * If configuration change (new configuration coming from DC) requires reload then reload needs
     * to be advertised at HC’s domain model.
     * <p>
     * DC is up, HC is up. HC is started <b>without any special flag</b>.
     * When DC provides change in settings the HC should show info that reload is needed.
     */
    @Test
    public void reloadAdvertisedAfterDcModelChange() throws Exception {
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();

        final boolean isAddLoggingApiDependencies = setLoggingApiDependencies(domainManager.getDomainPrimaryLifecycleUtil().getDomainClient());

        // property was changed - checking if change is visible at profile level
        ModelNode profileRead = readLoggingApiDependencies(domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
        assertEquals("Expecting value of attribute 'add-logging-api-dependencies' was changed" + profileRead,
                isAddLoggingApiDependencies, profileRead.get("result").asBoolean());
        // property was changed - checking if change is visible at HC
        ModelNode hostRead = readLoggingApiDependenciesAtServerOtherTwo(domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
        assertEquals("Read operation should suceed", SUCCESS, hostRead.get(OUTCOME).asString());
        assertEquals("Expecting value of attribute 'add-logging-api-dependencies' was changed" + hostRead,
                isAddLoggingApiDependencies, hostRead.get("result").asBoolean());
        assertEquals("Expecting change of attribute 'add-logging-api-dependencies' requests a reload: " + hostRead,
                "reload-required", hostRead.get("response-headers").get("process-state").asString());
    }

    /**
     * <p>
     * If configuration change (new configuration coming from DC) requires reload then reload needs
     * to be advertised at HC’s domain model.
     * <p>
     * <ol>
     *   <li>DC is up, HC is started with <code>--backup</code> to get created a <code>domain.cached-remote.xml</code>.</li>
     *   <li>HC is stopped and configuration change which requires reload is done at DC and DC is stopped.</li>
     *   <li>HC is started with <code>--cached-dc</code> and boot-up with config from <code>domain.cached-remote.xml</code>.</li>
     *   <li>DC is started and configuration change should be propagated to HC with announcement that reload is needed</li>
     * </ol>
     */
    @Test
    public void reloadAdvertisedAfterDcModelChangeWithShutdown() throws Exception {
        // secondary is started with backup parameter but not with cached dc
        domainConfig.getSecondaryConfiguration().setBackupDC(true).setCachedDC(false);
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();
        // domain.cached-remote.xml was created now stop secondary HC
        stopSecondaryAndWaitForUnregistration();

        // changing parameter which needs reload
        final DomainClient client = domainManager.getDomainPrimaryLifecycleUtil().getDomainClient();
        final boolean isAddLoggingApiDependencies = setLoggingApiDependencies(client);
        final ModelNode profileRead = readLoggingApiDependencies(client);
        assertEquals("Expecting value of attribute 'add-logging-api-dependencies' was changed" + profileRead,
            isAddLoggingApiDependencies, profileRead.get("result").asBoolean());
        domainManager.stopHosts();

        // starting domain once again - reusing already changed config files
        // secondary will be started with cached dc parameter but not with backup parameter
        domainConfig.getPrimaryConfiguration().setRewriteConfigFiles(false);
        domainConfig.getSecondaryConfiguration()
            .setRewriteConfigFiles(false).setBackupDC(false).setCachedDC(true);

        // starting HC with old domain.cached-remote.xml
        domainManager.getDomainSecondaryLifecycleUtil().start();

        // starting DC where domain config contains a configuration change
        domainManager.getDomainPrimaryLifecycleUtil().start();

        runWithTimeout(TIMEOUT_S, () -> {
            ModelNode hostRead = readLoggingApiDependenciesAtServerOtherTwo(domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
            assertEquals("Read operation should suceed", SUCCESS, hostRead.get(OUTCOME).asString());
            assertEquals("Expecting change of attribute 'add-logging-api-dependencies' requests a reload: " + hostRead,
                "reload-required", hostRead.get("response-headers").get("process-state").asString());
        });
        reloadServers(domainManager.getDomainPrimaryLifecycleUtil().getDomainClient());
        runWithTimeout(TIMEOUT_S, () -> {
            ModelNode hostRead = readLoggingApiDependenciesAtServerOtherTwo(domainManager.getDomainPrimaryLifecycleUtil().getDomainClient());
            assertEquals("Expecting value of attribute 'add-logging-api-dependencies' changed: " + hostRead,
                    isAddLoggingApiDependencies, hostRead.get("result").asBoolean());
        });
        // by WFCORE-2331 the host controller log file should advert need of configuration change
        runWithTimeout(TIMEOUT_S, () -> checkHostControllerLogFile(domainConfig.getSecondaryConfiguration(), "WFLYHC0202"));
    }

    private void test_hcStartsWhenCachedConfigAvailable(DomainTestSupport.Configuration domainConfig) throws Exception {
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();
        DomainLifecycleUtil primaryHost = domainManager.getDomainPrimaryLifecycleUtil();
        DomainLifecycleUtil secondaryHost = domainManager.getDomainSecondaryLifecycleUtil();

        assertTrue("Primary should be started", primaryHost.areServersStarted());
        assertTrue("Secondary should be started", secondaryHost.areServersStarted());

        domainManager.stopHosts();

        domainConfig.getSecondaryConfiguration().setCachedDC(true);
        secondaryHost.start();
    }

    /**
     * Checking that HC waits for DC being started when HC is started first.
     */
    private void test_hcWaitsForDcBeingStarted(DomainTestSupport.Configuration domainConfig) throws Exception {
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.getDomainSecondaryLifecycleUtil().startAsync();

        try (final DomainClient client = getDomainClient(domainConfig.getSecondaryConfiguration())) {
            // getting statuses of servers from secondary HC as waiting for HC being ready
            runWithTimeout(TimeoutUtil.adjust(5), () -> client.getServerStatuses());
            Assert.fail("DC started with no param, it's waiting for DC but it's not possible to connect to it");
        } catch (IllegalStateException ise) {
            // as HC should not be started IllegalStateException is expected
        }

        // after starting DC, HC is ready to use with all its servers
        domainManager.getDomainPrimaryLifecycleUtil().start();
        waitForHostControllerBeingStarted(TIMEOUT_S, domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
        waitForServersBeingStarted(TIMEOUT_S, domainManager.getDomainSecondaryLifecycleUtil().getDomainClient());
    }

    private DomainClient getDomainClient(WildFlyManagedConfiguration config) throws UnknownHostException {
        final InetAddress address = InetAddress.getByName(config.getHostControllerManagementAddress());
        final int port = config.getHostControllerManagementPort();
        final String protocol = config.getHostControllerManagementProtocol();
        return new DomainClientImpl(protocol, address, port);
    }

    private void waitForHostControllerBeingStarted(long timeoutSeconds, DomainClient client) {
        runWithTimeout(timeoutSeconds, () -> client.getServerStatuses());
    }

    private void waitForServersBeingStarted(long timeoutSeconds, DomainClient client) {
        // checking that all serves are started
        runWithTimeout(timeoutSeconds, () ->  {
            client.getServerStatuses().entrySet().forEach(entry -> {
                switch(entry.getValue()) {
                    case DISABLED:
                        log.tracef("Server '%s' status check skipped as status is %s", entry.getKey(), entry.getValue());
                        break;
                    case STARTED:
                        log.tracef("Server '%s' is started", entry.getKey());
                        break;
                    default:
                        log.tracef("Assert fail: server '%s' with status '%s'", entry.getKey(), entry.getValue());
                        Assert.fail(String.format("Server '%s' is not started, is in status '%s'",
                                entry.getKey(), entry.getValue()));
                }
            });
            return null;
        });
    }

    private void runWithTimeout(long timeoutSeconds, Runnable voidFunctionInterface) {
        runWithTimeout(timeoutSeconds, () -> {
            voidFunctionInterface.run();
            return null;
        });
    }

    private <T> T runWithTimeout(long timeoutSeconds, Supplier<T> function) {
        final long timeoutTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while(true) {
            try {
                return function.get();
            } catch (Throwable t) {
                if(timeoutTime < System.currentTimeMillis()) {
                    throw new IllegalStateException("Function '" + function
                        + "' failed to process in " + timeoutSeconds + " s, caused: " + t.getMessage() , t);
                }
                try {
                    Thread.sleep(TIMEOUT_SLEEP_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private File getDomainCachedRemoteXmlFile(WildFlyManagedConfiguration appConfiguration) {
        final File rootDir = new File(appConfiguration.getDomainDirectory());
        return new File(rootDir, "configuration" + File.separator + DOMAIN_CACHED_REMOTE_XML_FILE_NAME);
    }

    private void checkHostControllerLogFile(WildFlyManagedConfiguration appConfiguration, String containString) {
        final File logFile = new File(appConfiguration.getDomainDirectory(), "log" + File.separator + "host-controller.log");
        assertTrue("Log file '" + logFile + "' does not exist", logFile.exists());
        try {
            final String content = com.google.common.io.Files.toString(logFile, Charset.forName("UTF-8"));
            assertTrue("Expecting log file '" + logFile + " contains string '" + containString + "'",
                content.contains(containString));
        } catch (IOException ioe) {
            throw new RuntimeException("Can't read content of file " + logFile, ioe);
        }
    }

    private ModelNode getProfileDefaultLoggingAddr() {
        return new ModelNode()
            .add(PROFILE, "default")
            .add(SUBSYSTEM, "logging");
    }

    private ModelNode getCreateOperationTestLoggingCategory() {
        final ModelNode addrLogger = getProfileDefaultLoggingAddr();
        addrLogger.add(LOGGER, TEST_LOGGER_NAME);

        final ModelNode createOp = Operations.createAddOperation(addrLogger);
        createOp.get("level").set("TRACE");
        createOp.get("category").set(TEST_LOGGER_NAME);
        return createOp;
    }

    private ModelNode readResourceTestLoggerFromSecondaryHost(DomainClient client) throws IOException {
        final ModelNode hostLoggerAddress = new ModelNode()
                .add(HOST, "secondary")
                .add(SERVER, "other-two")
                .add(SUBSYSTEM, "logging")
                .add(LOGGER, TEST_LOGGER_NAME);
        final ModelNode readResourceOp = Operations.createReadResourceOperation(hostLoggerAddress);
        final ModelNode result = client.execute(readResourceOp);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return result;
    }

    /**
     * ls /host=secondary/server=other-two/subsystem=logging/logger
     */
    private void checkTestLoggerFromSecondaryHost(DomainClient client) throws IOException {
        final ModelNode result = readResourceTestLoggerFromSecondaryHost(client);
        assertEquals("Reading logger '"  + TEST_LOGGER_NAME + "' resource does not finish with success: " + result,
            SUCCESS, result.get(OUTCOME).asString());
    }

    private ModelNode readLoggingApiDependenciesAtServerOtherTwo(DomainClient client) {
        final ModelNode hostLoggingAddress = new ModelNode()
                .add(HOST, "secondary")
                .add(SERVER, "other-two")
                .add(SUBSYSTEM, "logging");

        final ModelNode readAttributeOp = Operations
                .createReadAttributeOperation(hostLoggingAddress, "add-logging-api-dependencies");
        try {
            final ModelNode result = client.execute(readAttributeOp);
            assertEquals(SUCCESS, result.get(OUTCOME).asString());
            return result;
        } catch (IOException ioe) {
            throw new RuntimeException("Can't read attribute 'add-logging-api-dependencies' from address "
                + hostLoggingAddress, ioe);
        }
    }

    private ModelNode readLoggingApiDependencies(DomainClient client) throws IOException {
        final ModelNode readAttributeOp = Operations
                .createReadAttributeOperation(getProfileDefaultLoggingAddr(), "add-logging-api-dependencies");
        final ModelNode result = client.execute(readAttributeOp);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return result;
    }

    private boolean setLoggingApiDependencies(DomainClient client) throws IOException {
        boolean isAddLoggingApiDependencies = readLoggingApiDependencies(client).get("result").asBoolean();

        final ModelNode writeAttributeOp = Operations
            .createWriteAttributeOperation(getProfileDefaultLoggingAddr(), "add-logging-api-dependencies", !isAddLoggingApiDependencies);
        final ModelNode result = client.execute(writeAttributeOp);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return !isAddLoggingApiDependencies;
    }

    private void reloadServers(DomainClient clientPrimary) throws IOException {
        final ModelNode op = Operations.createOperation("reload-servers");
        op.get("blocking").set("true");
        final ModelNode result = clientPrimary.execute(op);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    private void stopSecondaryAndWaitForUnregistration() throws InterruptedException, IOException  {
        domainManager.getDomainSecondaryLifecycleUtil().stop();
        // WFCORE-2836
        // this is a bit flaky in CI. It seems that that operation (example adding a logger)
        // occasionally executre before the secondary host unregisters completely, triggering rollback
        // when the secondary shuts down.
        final long deadline = System.currentTimeMillis() + 1000;
        while (true) {
            if (! isHostPresentInModel(domainManager.getDomainSecondaryConfiguration().getHostName(), null)) {
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(TIMEOUT_SLEEP_MILLIS);
        }
    }

    /**
     * @param hostname the hostname to query
     * @param requiredState the {@link ControlledProcessState.State} expected, or null for any state}
     * @return true if the host is present in the queried hosts's model (in the requiredState, if present), false otherwise.
     */
    private boolean isHostPresentInModel(final String hostname, final ControlledProcessState.State requiredState) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, hostname);
        operation.get(NAME).set(HOST_STATE);

        ModelNode result;

        try (DomainClient client = getDomainClient(domainConfig.getPrimaryConfiguration())) {
            result = client.execute(operation);
            if (result.get(ModelDescriptionConstants.OUTCOME).asString().equals(ModelDescriptionConstants.SUCCESS)) {
                final ModelNode model = result.require(RESULT);
                if (requiredState == null) {
                    return true;
                }
                return model.asString().equalsIgnoreCase(requiredState.toString());
            } else if (result.get(ModelDescriptionConstants.OUTCOME).asString().equals(FAILED)) {
                // make sure we get WFLYCTL0030: No resource definition is registered for address so we don't mistakenly hide other problems.
                if (result.require(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0030")) {
                    return false;
                }
                // otherwise we got a failure, but the host is present (perhaps still shutting down?), so return true
                return true;
            }
            // otherwise, something bad happened
            throw new RuntimeException(result != null ? result.toJSONString(false) : "Unknown error in determining host state.");
        }
    }

}
