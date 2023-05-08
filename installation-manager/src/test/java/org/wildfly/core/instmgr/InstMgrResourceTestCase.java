/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

/**
 * Installation Manager unit tests.
 */
public class InstMgrResourceTestCase extends AbstractControllerTestBase {
    private static final ServiceName PATH_MANAGER_SVC = AbstractControllerService.PATH_MANAGER_CAPABILITY.getCapabilityServiceName();
    private static final ServiceName MANAGEMENT_EXECUTOR_SVC = AbstractControllerService.EXECUTOR_CAPABILITY.getCapabilityServiceName();
    PathManagerService pathManagerService;
    static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");
    static final Path JBOSS_HOME = TARGET_DIR.resolve("InstMgrResourceTestCase").normalize().toAbsolutePath();
    static final Path JBOSS_CONTROLLER_TEMP_DIR = JBOSS_HOME.resolve("temp");
    static final Path INSTALLATION_MANAGER_PROPERTIES = JBOSS_HOME.resolve("bin").resolve("installation-manager.properties");

    @Before
    public void setupController() throws InterruptedException, IOException {
        TestInstallationManager.initialized = false;
        JBOSS_HOME.resolve("bin").toFile().mkdirs();
        Files.deleteIfExists(INSTALLATION_MANAGER_PROPERTIES);
        Files.createFile(INSTALLATION_MANAGER_PROPERTIES);
        try (FileOutputStream out = new FileOutputStream(INSTALLATION_MANAGER_PROPERTIES.toString())) {
            final Properties prop = new Properties();
            prop.setProperty(InstMgrCandidateStatus.INST_MGR_STATUS_KEY, InstMgrCandidateStatus.Status.CLEAN.name());
            prop.store(out, null);
        }
        super.setupController();
    }

    @After
    public void shutdownServiceContainer() throws IOException {

        super.shutdownServiceContainer();
        if (JBOSS_HOME.toFile().exists()) {
            try (Stream<Path> walk = Files.walk(JBOSS_HOME)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.deleteIfExists(INSTALLATION_MANAGER_PROPERTIES);
        for(File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
            Files.deleteIfExists(testZip.toPath());
        }
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        pathManagerService = new PathManagerService(managementModel.getCapabilityRegistry()) {
            {
                super.addHardcodedAbsolutePath(getContainer(), "jboss.home.dir", JBOSS_HOME.toString());
                super.addHardcodedAbsolutePath(getContainer(), "jboss.controller.temp.dir", JBOSS_CONTROLLER_TEMP_DIR.toString());
            }
        };

        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ExecutorService mgmtExecutor = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                5L, TimeUnit.SECONDS,
                new SynchronousQueue<>());

        ServiceBuilder<?> mgmtExecutorBuilder = getContainer().addService(MANAGEMENT_EXECUTOR_SVC);
        Consumer<Object> provides = mgmtExecutorBuilder.provides(MANAGEMENT_EXECUTOR_SVC);
        Service service = Service.newInstance(provides, mgmtExecutor);
        mgmtExecutorBuilder.setInstance(service);

        StabilityMonitor monitor = new StabilityMonitor();
        monitor.addController(mgmtExecutorBuilder.install());
        monitor.addController(getContainer().addService(PATH_MANAGER_SVC).setInstance(pathManagerService).install());
        recordService(monitor, InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName());

        try {
            monitor.awaitStability(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        registration.registerSubModel(PathResourceDefinition.createSpecified(pathManagerService));

        pathManagerService.addPathManagerResources(managementModel.getRootResource());
    }

    @Test
    public void testRootResource() throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, null);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.isDefined());

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, pathElements);
        op.get(RECURSIVE).set(true);

        result = executeForResult(op);
        Assert.assertTrue(result.isDefined());
    }

    @Test
    public void testReadChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.isDefined());

        // validate the result:
        List<ModelNode> channels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(3, channels.size());

        // First Channel
        ModelNode channel = channels.get(0);
        Assert.assertEquals("channel-test-0", channel.get(NAME).asString());

        List<ModelNode> repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(2, repositories.size());

        ModelNode repository = repositories.get(0);
        Assert.assertEquals("id0", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("http://localhost", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        repository = repositories.get(1);
        Assert.assertEquals("id1", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("file://dummy", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        ModelNode manifest = channel.get(InstMgrConstants.MANIFEST);
        Assert.assertTrue(manifest.hasDefined(InstMgrConstants.MANIFEST_GAV));
        Assert.assertFalse(manifest.hasDefined(InstMgrConstants.MANIFEST_URL));
        Assert.assertEquals("org.test.groupid:org.test.artifactid:1.0.0.Final", manifest.get(InstMgrConstants.MANIFEST_GAV).asString());


        // Second Channel
        channel = channels.get(1);
        Assert.assertEquals("channel-test-1", channel.get(NAME).asString());

        repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(1, repositories.size());

        repository = repositories.get(0);
        Assert.assertEquals("id1", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("file://dummy", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        manifest = channel.get(InstMgrConstants.MANIFEST);
        Assert.assertFalse(manifest.hasDefined(InstMgrConstants.MANIFEST_GAV));
        Assert.assertTrue(manifest.hasDefined(InstMgrConstants.MANIFEST_URL));
        Assert.assertEquals("file://dummy", manifest.get(InstMgrConstants.MANIFEST_URL).asString());


        // third Channel
        channel = channels.get(2);
        Assert.assertEquals("channel-test-2", channel.get(NAME).asString());

        repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(2, repositories.size());

        Assert.assertFalse(channel.hasDefined(InstMgrConstants.MANIFEST));
    }

    @Test
    public void testAddEditChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        // Initial
        ModelNode result = executeForResult(op);
        List<ModelNode> currentChannels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(3, currentChannels.size());

        // Add one Channel
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, pathElements);

        ModelNode channels = new ModelNode().addEmptyList();

        ModelNode channel = new ModelNode();
        channel.get(NAME).set("channel-test-added");

        ModelNode repositories = new ModelNode().addEmptyList();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost.com");
        repositories.add(repository);

        ModelNode manifest = new ModelNode();
        manifest.get(InstMgrConstants.MANIFEST_GAV).set("group:artifact:version");
        channel.get(InstMgrConstants.MANIFEST).set(manifest);

        channel.get(InstMgrConstants.REPOSITORIES).set(repositories);
        channels.add(channel);

        op.get(NAME).set(InstMgrConstants.CHANNELS);
        op.get(VALUE).set(channels);

        executeCheckNoFailure(op);


        // Read again
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        result = executeForResult(op);
        currentChannels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(4, currentChannels.size());

        boolean found = false;
        for (Channel storedChannel : TestInstallationManager.lstChannels) {
            if (storedChannel.getName().equals("channel-test-added")) {
                List<Repository> storedRepositories = storedChannel.getRepositories();
                for (Repository storedRepo : storedRepositories) {
                    if (storedRepo.getId().equals("id0") && storedRepo.getUrl().equals("https://localhost.com")) {
                        found = storedChannel.getManifestCoordinate().get().equals("group:artifact:version");
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(found);


        // Edit one channel
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, pathElements);

        channels = new ModelNode().addEmptyList();
        channel = new ModelNode();
        channel.get(NAME).set("channel-test-1");

        repositories = new ModelNode().addEmptyList();
        repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id-modified-0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://modified.com");
        repositories.add(repository);

        manifest = new ModelNode();
        manifest.get(InstMgrConstants.MANIFEST_GAV).set("group-modified:artifact-modified:version-modified");
        channel.get(InstMgrConstants.MANIFEST).set(manifest);

        channel.get(InstMgrConstants.REPOSITORIES).set(repositories);
        channels.add(channel);

        op.get(NAME).set(InstMgrConstants.CHANNELS);
        op.get(VALUE).set(channels);

        executeCheckNoFailure(op);

        found = false;
        for (Channel storedChannel : TestInstallationManager.lstChannels) {
            if (storedChannel.getName().equals("channel-test-1")) {
                List<Repository> storedRepositories = storedChannel.getRepositories();
                for (Repository storedRepo : storedRepositories) {
                    if (storedRepo.getId().equals("id-modified-0") && storedRepo.getUrl().equals("https://modified.com")) {
                        found = storedChannel.getManifestCoordinate().get().equals("group-modified:artifact-modified:version-modified");
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(found);
    }

    @Test
    public void testRemoveChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrRemoveChannelHandler.OPERATION_NAME, pathElements);
        op.get(NAME).set("channel-test-1");

        executeCheckNoFailure(op);

        op = Util.createEmptyOperation(InstMgrRemoveChannelHandler.OPERATION_NAME, pathElements);
        op.get(NAME).set("channel-test-1");

        ModelNode failed = executeCheckForFailure(op);

        String expectedCode = "WFLYIM0015:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void testHistoryChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrHistoryHandler.OPERATION_NAME, pathElements);

        ModelNode result = executeForResult(op);
        Assert.assertEquals(4, result.asListOrEmpty().size());
        Assert.assertTrue(result.getType() == ModelType.LIST);
        List<ModelNode> entries = result.asListOrEmpty();
        for (ModelNode entry : entries) {
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_HASH));
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_TIMESTAMP));
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_TYPE));
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_DESCRIPTION));
        }
    }

    @Test
    public void testRevisionDetails() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrHistoryRevisionHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("dummy");

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.getType() == ModelType.OBJECT);

        // Verify Artifact Changes
        List<ModelNode> resultLst = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_ARTIFACT_CHANGES).asList();
        Assert.assertEquals(3, resultLst.size());

        for (ModelNode change : resultLst) {
            String status = change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_STATUS).asString();
            if (status.equals(ArtifactChange.Status.UPDATED.name().toLowerCase())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.updated", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertEquals("1.0.0.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).asString());
                Assert.assertEquals("1.0.1.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).asString());

            } else if (status.equals(ArtifactChange.Status.REMOVED.name().toLowerCase())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.removed", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertEquals("1.0.0.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).isDefined());

            } else if (status.equals(ArtifactChange.Status.INSTALLED.name().toLowerCase())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.installed", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).isDefined());
                Assert.assertEquals("1.0.1.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).asString());

            } else {
                Assert.fail("Invalid status found");
            }
        }

        // Verify Channel Changes
        resultLst = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_CHANNEL_CHANGES).asList();
        Assert.assertEquals(3, resultLst.size());
        for (ModelNode change : resultLst) {
            String status = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_STATUS).asString();
            if (status.equals(ChannelChange.Status.MODIFIED.name().toLowerCase())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.1.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).asString());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(3, repositories.size());
                for (ModelNode repository : repositories) {
                    String oldRepo = repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).asString();
                    if (oldRepo.equals("id=id0::url=http://channelchange.com")) {
                        Assert.assertEquals("id=id0::url=http://channelchange-modified.com", repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).asString());
                    } else if (oldRepo.equals("id=id1::url=file://channelchange")) {
                        Assert.assertEquals("id=id1-modified::url=file://channelchange", repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).asString());
                    } else if (oldRepo.equals("id=id1-added::url=file://channelchange-added")) {
                        Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                    }
                }

            } else if (status.equals(ChannelChange.Status.REMOVED.name().toLowerCase())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).isDefined());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(2, repositories.size());
                for (ModelNode repository : repositories) {
                    Assert.assertTrue(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).isDefined());
                    Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                }

            } else if (status.equals(ChannelChange.Status.ADDED.name().toLowerCase())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertFalse(change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).isDefined());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).asString());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(2, repositories.size());
                for (ModelNode repository : repositories) {
                    Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).isDefined());
                    Assert.assertTrue(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                }

            } else {
                Assert.fail("Invalid status found");
            }
        }
    }

    @Test
    public void testCreateSnapShot() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCreateSnapshotHandler.OPERATION_NAME, pathElements);

        ModelNode response = executeCheckNoFailure(op);
        Assert.assertTrue("the response of the clone-export management operation did not return a stream in the response headers.", response.hasDefined(RESPONSE_HEADERS, ATTACHED_STREAMS));
    }

    @Test
    public void testListMavenOptions() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.OFFLINE).set(true);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(true);

        ModelNode response = executeForResult(op);
        verifyListUpdatesResult(response, false);

        Assert.assertTrue(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertNull(TestInstallationManagerFactory.mavenOptions.getLocalRepository());


        op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.OFFLINE).set(false);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(false);

        response = executeForResult(op);
        verifyListUpdatesResult(response, false);

        Assert.assertFalse(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(MavenOptions.LOCAL_MAVEN_REPO, TestInstallationManagerFactory.mavenOptions.getLocalRepository());


        Path localCache = Paths.get("one").resolve("two").toAbsolutePath();
        op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        response = executeForResult(op);
        verifyListUpdatesResult(response, false);

        Assert.assertFalse(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(localCache, TestInstallationManagerFactory.mavenOptions.getLocalRepository());


        op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toAbsolutePath().toString());
        op.get(InstMgrConstants.OFFLINE).set(true);

        response = executeForResult(op);
        verifyListUpdatesResult(response, false);

        Assert.assertTrue(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(localCache, TestInstallationManagerFactory.mavenOptions.getLocalRepository());
    }

    @Test
    public void listUpdatesCannotUseLocalCacheWithNoResolveLocalCache() throws OperationFailedException {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        Path localCache = Paths.get("dummy").resolve("something");
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(true);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        ModelNode failed = executeCheckForFailure(op);
        String expectedCode = "WFLYIM0011:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void listUpdatesCannotUseMavenRepoFileWithRepositories() {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);

        ModelNode repositories = new ModelNode();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost");
        repositories.add(repository);
        op.get(InstMgrConstants.REPOSITORIES).set(repositories);

        ModelNode failed = executeCheckForFailure(op);
        String expectedCode = "WFLYIM0012:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void listUpdatesWithRepositories() throws OperationFailedException, IOException, URISyntaxException {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);

        ModelNode repositories = new ModelNode();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id1");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost.one");
        repositories.add(repository);

        repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id2");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost.two");
        repositories.add(repository);

        op.get(InstMgrConstants.REPOSITORIES).set(repositories);


        ModelNode response = executeForResult(op);

        Assert.assertEquals(2, TestInstallationManager.findUpdatesRepositories.size());
        Repository repositoryOne = TestInstallationManager.findUpdatesRepositories.get(0);
        Assert.assertEquals("id1", repositoryOne.getId());
        Assert.assertTrue(repositoryOne.getUrl().toString().matches("https://localhost.one"));

        Repository repositoryTwo = TestInstallationManager.findUpdatesRepositories.get(1);
        Assert.assertEquals("id2", repositoryTwo.getId());
        Assert.assertTrue(repositoryTwo.getUrl().toString().matches("https://localhost.two"));

        verifyListUpdatesResult(response, false);
    }

    @Test
    public void listUpdatesUploadMavenZip() throws OperationFailedException, IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);
        Operation build = operationBuilder.build();

        ModelNode response = executeForResult(build);

        // verify we are using a repository pointing out to the maven zip file
        Assert.assertEquals(1, TestInstallationManager.findUpdatesRepositories.size());
        Repository mavenZipRepo = TestInstallationManager.findUpdatesRepositories.get(0);

        verifyListUpdatesUploadedZipRepository(mavenZipRepo, 0, "list-updates-", "artifact-one");
        verifyListUpdatesResult(response, true);

        // remove all temporal files
        op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        executeForResult(op);
        Assert.assertTrue(!Paths.get(new URL(mavenZipRepo.getUrl()).getFile()).toFile().exists());
    }


    @Test
    public void listUpdatesUploadMultipleMavenZip() throws OperationFailedException, IOException {
        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrListUpdatesHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0).add(1);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(targetOne);
        operationBuilder.addFileAsAttachment(targetTwo);
        Operation build = operationBuilder.build();

        ModelNode response = executeForResult(build);

        // verify we are using a repository pointing out to the maven zip file
        Assert.assertEquals(2, TestInstallationManager.findUpdatesRepositories.size());
        Repository mavenZipRepo = TestInstallationManager.findUpdatesRepositories.get(0);
        verifyListUpdatesUploadedZipRepository(mavenZipRepo, 0, "list-updates-", "artifact-one");

        mavenZipRepo = TestInstallationManager.findUpdatesRepositories.get(1);
        verifyListUpdatesUploadedZipRepository(mavenZipRepo, 1, "list-updates-", "artifact-two");

        verifyListUpdatesResult(response, true);

        // remove all temporal files
        op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        executeForResult(op);
        Assert.assertTrue(!Paths.get(new URL(mavenZipRepo.getUrl()).getFile()).toFile().exists());
    }

    /**
     * Verifies that we have created the expected structure for a repository created to supply the artifacts included in an Uploaded Maven Zip File.
     *
     * @param mavenZipRepo
     * @param streamIndex
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void verifyListUpdatesUploadedZipRepository(Repository mavenZipRepo, int streamIndex, String tempDirPrefix, String artifactName) throws MalformedURLException {
        Assert.assertEquals(InstMgrConstants.INTERNAL_REPO_PREFIX + streamIndex, mavenZipRepo.getId());
        Path repoUrlPath = Paths.get(new URL(mavenZipRepo.getUrl()).getFile());
        Assert.assertEquals(repoUrlPath.getFileName().toString(), "maven-repository");
        Assert.assertEquals(repoUrlPath.getParent().getFileName().toString(), InstMgrConstants.INTERNAL_REPO_PREFIX + streamIndex);
        Assert.assertTrue(repoUrlPath.getParent().getParent().getFileName().toString().startsWith(tempDirPrefix));
        Assert.assertTrue(repoUrlPath.toFile().exists());
        Assert.assertTrue(repoUrlPath.toFile().isDirectory());
        String[] files = repoUrlPath.toFile().list();
        Assert.assertEquals(files[0], artifactName);
    }

    @Test
    public void prepareUpdatesMavenOptions() throws OperationFailedException, IOException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();
        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.OFFLINE).set(true);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(true);

        ModelNode response = executeForResult(op);
        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertTrue(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertNull(TestInstallationManagerFactory.mavenOptions.getLocalRepository());

        // Clean it to be able to prepare another
        op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        executeForResult(op);


        op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.OFFLINE).set(false);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(false);

        response = executeForResult(op);
        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertFalse(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(MavenOptions.LOCAL_MAVEN_REPO, TestInstallationManagerFactory.mavenOptions.getLocalRepository());


        // Clean it to be able to prepare another
        op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        executeForResult(op);


        Path localCache = Paths.get("one").resolve("two").toAbsolutePath();
        op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        response = executeForResult(op);
        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertFalse(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(localCache, TestInstallationManagerFactory.mavenOptions.getLocalRepository());


        // Clean it to be able to prepare another
        op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        executeForResult(op);

        op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toAbsolutePath().toString());
        op.get(InstMgrConstants.OFFLINE).set(true);

        response = executeForResult(op);
        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertEquals(localCache, TestInstallationManagerFactory.mavenOptions.getLocalRepository());
        Assert.assertTrue(TestInstallationManagerFactory.mavenOptions.isOffline());
    }

    @Test
    public void prepareUpdatesCannotUseLocalCacheWithNoResolveLocalCache() throws OperationFailedException {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        Path localCache = Paths.get("dummy").resolve("something");
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(true);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        ModelNode failed = executeCheckForFailure(op);
        String expectedCode = "WFLYIM0011:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void prepareUpdatesCannotUseMavenRepoFileWithRepositories() {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);

        ModelNode repositories = new ModelNode();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost");
        repositories.add(repository);
        op.get(InstMgrConstants.REPOSITORIES).set(repositories);

        ModelNode failed = executeCheckForFailure(op);

        String expectedCode = "WFLYIM0012:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void prepareUpdatesCannotUseWorkDirWithMavenRepoFileOrRepositories() {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);
        op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set("/dummy");

        ModelNode failed = executeCheckForFailure(op);

        String expectedCode = "WFLYIM0014:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );


        op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        ModelNode repositories = new ModelNode();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost");
        repositories.add(repository);
        op.get(InstMgrConstants.REPOSITORIES).set(repositories);
        op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set("/dummy");

        failed = executeCheckForFailure(op);
        expectedCode = "WFLYIM0014:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );


        op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);
        op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set("/dummy");
        op.get(InstMgrConstants.REPOSITORIES).set(repositories);

        failed = executeCheckForFailure(op);
        expectedCode = "WFLYIM0014:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void prepareUpdatesUploadMavenZip() throws OperationFailedException, IOException, URISyntaxException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);
        Operation build = operationBuilder.build();
        executeForResult(build);

        Assert.assertEquals(1, TestInstallationManager.prepareUpdatesRepositories.size());
        Repository mavenZipRepo = TestInstallationManager.prepareUpdatesRepositories.get(0);

        verifyPrepareUploadedZipRepository(mavenZipRepo, 0, "prepare-updates-", "artifact-one");

        // verify the prepared server
        Assert.assertTrue(instMgrService.getPreparedServerDir().toFile().listFiles().length == 1);
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());
    }

    @Test
    public void prepareUpdatesMultipleUploadMavenZip() throws OperationFailedException, IOException, URISyntaxException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0).add(1);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(targetOne);
        operationBuilder.addFileAsAttachment(targetTwo);
        Operation build = operationBuilder.build();
        executeForResult(build);

        Assert.assertEquals(2, TestInstallationManager.prepareUpdatesRepositories.size());
        Repository mavenZipRepo = TestInstallationManager.prepareUpdatesRepositories.get(0);
        verifyPrepareUploadedZipRepository(mavenZipRepo, 0, "prepare-updates-", "artifact-one");

        mavenZipRepo = TestInstallationManager.prepareUpdatesRepositories.get(1);
        verifyPrepareUploadedZipRepository(mavenZipRepo, 1, "prepare-updates-", "artifact-two");

        // verify the prepared server
        Assert.assertTrue(instMgrService.getPreparedServerDir().toFile().listFiles().length == 1);
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());
    }

    /**
     * Verifies that we have created the expected structure for a repository created to supply the artifacts included in an Uploaded Maven Zip File.
     *
     * @param mavenZipRepo
     * @param streamIndex
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void verifyPrepareUploadedZipRepository(Repository mavenZipRepo, int streamIndex, String tempDirPrefix, String artifactName) throws MalformedURLException {
        Assert.assertEquals(InstMgrConstants.INTERNAL_REPO_PREFIX + streamIndex, mavenZipRepo.getId());
        Path repoUrlPath = Paths.get(new URL(mavenZipRepo.getUrl()).getFile());
        Assert.assertEquals(repoUrlPath.getFileName().toString(), "maven-repository");
        Assert.assertEquals(repoUrlPath.getParent().getFileName().toString(), InstMgrConstants.INTERNAL_REPO_PREFIX + streamIndex);
        Assert.assertTrue(repoUrlPath.getParent().getParent().getFileName().toString().startsWith(tempDirPrefix));
        // The temporal directory used to prepare the candidate server should have been deleted once the candidate server is prepared.
        Assert.assertFalse(repoUrlPath.toFile().exists());
    }

    @Test
    public void prepareUpdatesSimple() throws OperationFailedException, IOException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());


        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareUpdateHandler.OPERATION_NAME, pathElements);

        ModelNode response = executeForResult(op);

        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());

        Path scriptPropertiesFile = JBOSS_HOME.resolve("bin").resolve("installation-manager.properties");
        try (FileInputStream in = new FileInputStream(scriptPropertiesFile.toFile())) {
            final Properties prop = new Properties();
            prop.load(in);
            Assert.assertEquals(JBOSS_HOME.resolve("bin") + TestInstallationManager.APPLY_UPDATE_BASE_GENERATED_COMMAND+instMgrService.getPreparedServerDir(), prop.get(InstMgrCandidateStatus.INST_MGR_COMMAND_KEY));
        }
    }

    @Test
    public void prepareRevertCannotUseLocalCacheWithNoResolveLocalCache() throws OperationFailedException {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        Path localCache = Paths.get("dummy").resolve("something");
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("aaaabbbb");
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(true);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        ModelNode failed = executeCheckForFailure(op);
        String expectedCode = "WFLYIM0011:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );

        op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("aaaabbbb");
        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(false);
        op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toString());

        executeForResult(op);

        Assert.assertFalse(TestInstallationManagerFactory.mavenOptions.isOffline());
        Assert.assertEquals(localCache, TestInstallationManagerFactory.mavenOptions.getLocalRepository());
    }

    @Test
    public void prepareRevertCannotUseMavenRepoFileWithRepositories() {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("aaaabbbb");
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);

        ModelNode repositories = new ModelNode();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost");
        repositories.add(repository);
        op.get(InstMgrConstants.REPOSITORIES).set(repositories);

        ModelNode failed = executeCheckForFailure(op);
        String expectedCode = "WFLYIM0012:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void prepareRevertSimple() throws OperationFailedException, IOException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());


        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("hashcode");

        ModelNode response = executeForResult(op);

        Assert.assertEquals(instMgrService.getPreparedServerDir().toString(), response.asString());
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());

        Path scriptPropertiesFile = JBOSS_HOME.resolve("bin").resolve("installation-manager.properties");
        try (FileInputStream in = new FileInputStream(scriptPropertiesFile.toFile())) {
            final Properties prop = new Properties();
            prop.load(in);
            Assert.assertEquals(JBOSS_HOME.resolve("bin") + TestInstallationManager.APPLY_REVERT_BASE_GENERATED_COMMAND + instMgrService.getPreparedServerDir(), prop.get(InstMgrCandidateStatus.INST_MGR_COMMAND_KEY));
        }
    }

    @Test
    public void prepareRevertUploadMavenZip() throws OperationFailedException, IOException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0);
        op.get(InstMgrConstants.REVISION).set("dummy");
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);
        Operation build = operationBuilder.build();
        executeForResult(build);

        Assert.assertEquals(1, TestInstallationManager.prepareRevertRepositories.size());
        Repository mavenZipRepo = TestInstallationManager.prepareRevertRepositories.get(0);

        verifyPrepareUploadedZipRepository(mavenZipRepo, 0, "prepare-revert-", "artifact-one");

        // verify the prepared server
        Assert.assertTrue(instMgrService.getPreparedServerDir().toFile().listFiles().length == 1);
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());
    }

    @Test
    public void prepareRevertMultipleUploadMavenZip() throws OperationFailedException, IOException {
        InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        Assert.assertFalse(instMgrService.getPreparedServerDir().toFile().exists());
        Assert.assertEquals(InstMgrCandidateStatus.Status.CLEAN, instMgrService.getCandidateStatus());
        Assert.assertTrue(instMgrService.canPrepareServer());

        Path targetOne = TARGET_DIR.resolve("installation-manager-one.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetOne);

        Path targetTwo = TARGET_DIR.resolve("installation-manager-two.zip");
        source = new File(getClass().getResource("test-repo-two").getFile());
        zipDir(source.toPath().toAbsolutePath(), targetTwo);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrPrepareRevertHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.MAVEN_REPO_FILES).add(0).add(1);
        op.get(InstMgrConstants.REVISION).set("dummy");
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(targetOne);
        operationBuilder.addFileAsAttachment(targetTwo);
        Operation build = operationBuilder.build();
        executeForResult(build);

        Assert.assertEquals(2, TestInstallationManager.prepareRevertRepositories.size());

        Repository mavenZipRepo = TestInstallationManager.prepareRevertRepositories.get(0);
        verifyPrepareUploadedZipRepository(mavenZipRepo, 0, "prepare-revert-", "artifact-one");

        mavenZipRepo = TestInstallationManager.prepareRevertRepositories.get(1);
        verifyPrepareUploadedZipRepository(mavenZipRepo, 1, "prepare-revert-", "artifact-two");

        // verify the prepared server
        Assert.assertTrue(instMgrService.getPreparedServerDir().toFile().listFiles().length == 1);
        Assert.assertEquals(InstMgrCandidateStatus.Status.PREPARED, instMgrService.getCandidateStatus());
        Assert.assertFalse(instMgrService.canPrepareServer());
    }


    @Test
    public void uploadCustomPatchInvalidManifest() throws IOException {
        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCustomPatchHandler.OPERATION_NAME, pathElements);

        op.get(InstMgrConstants.MANIFEST).set("invalidgav");
        op.get(InstMgrConstants.CUSTOM_PATCH_FILE).set(0);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);

        Operation build = operationBuilder.build();

        ModelNode failed = executeCheckForFailure(build);
        String expectedCode = "WFLYIM0017:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );

        op.get(InstMgrConstants.MANIFEST).set("groupId:artifactId:version");
        op.get(InstMgrConstants.CUSTOM_PATCH_FILE).set(0);
        operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);

        build = operationBuilder.build();

        failed = executeCheckForFailure(build);
        expectedCode = "WFLYIM0017:";
        Assert.assertTrue(
                getCauseLogFailure(failed.get(FAILURE_DESCRIPTION).asString(), expectedCode),
                failed.get(FAILURE_DESCRIPTION).asString().startsWith(expectedCode)
        );
    }

    @Test
    public void uploadAndRemoveCustomPatch() throws IOException, OperationFailedException {
        TestInstallationManager.initialize();
        String customPatchManifest = "groupId-patch1:artifactId-patch01";
        createAndUploadCustomPatch(customPatchManifest);
        removeCustomPatch(customPatchManifest);
    }

    @Test
    public void uploadAndRemoveMultipleCustomPatches() throws IOException, OperationFailedException {
        final InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();
        TestInstallationManager.initialize();

        String customPatchManifest1 = "groupId-patch1:artifactId-patch1";
        String customPatchManifest2 = "groupId-patch2:artifactId-patch2";

        createAndUploadCustomPatch(customPatchManifest1);
        createAndUploadCustomPatch(customPatchManifest2);

        removeCustomPatch(customPatchManifest2);

        // check again customPatchManifest1 is still there
        List<Channel> lstChannels = TestInstallationManager.lstChannels;
        Path customPatchDir = instMgrService.getCustomPatchDir(customPatchManifest1);
        String customPatchChannelName = InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX + customPatchManifest1.replace(":", "_");
        boolean found = false;
        for (Channel channel : lstChannels) {
            if (channel.getName().equals(customPatchChannelName)) {
                List<Repository> repositories = channel.getRepositories();
                for (Repository repository : repositories) {
                    repository.getUrl().toString().equals(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toUri().toURL().toString());
                    found = true;
                }
            }
        }

        Assert.assertTrue("Expected channel created for the custom patch no found in " + lstChannels, found);

        removeCustomPatch("groupId-patch1:artifactId-patch01");
    }

    public void createAndUploadCustomPatch(String customPatchManifest) throws IOException, OperationFailedException {
        final InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();

        final String customPatchManifestGA = customPatchManifest;
        final String internalCustomPatchManifestGA = customPatchManifestGA.replace(":", "_");
        final String customPatchChannelName = InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX + internalCustomPatchManifestGA;

        //The patch doesn't exist yet
        Path customPatchDir = instMgrService.getCustomPatchDir(internalCustomPatchManifestGA);
        Assert.assertFalse(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toFile().exists());

        Path target = TARGET_DIR.resolve("installation-manager.zip");
        File source = new File(getClass().getResource("test-repo-one").getFile());
        zipDir(source.toPath().toAbsolutePath(), target);

        // The Channel for the custom patch doesn't exist yet
        List<Channel> lstChannels = TestInstallationManager.lstChannels;
        Assert.assertFalse(lstChannels.stream().anyMatch(c -> c.getName().equals(customPatchChannelName)));

        // Upload a single custom patch
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCustomPatchHandler.OPERATION_NAME, pathElements);

        op.get(InstMgrConstants.MANIFEST).set(customPatchManifestGA);
        op.get(InstMgrConstants.CUSTOM_PATCH_FILE).set(0);
        OperationBuilder operationBuilder = OperationBuilder.create(op, true);
        operationBuilder.addFileAsAttachment(target);

        Operation build = operationBuilder.build();

        ModelNode result = executeForResult(build);
        Assert.assertEquals(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toString(), result.asString());
        Assert.assertTrue(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toFile().exists());
        Assert.assertTrue(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).resolve("artifact-one").toFile().exists());

        boolean found = false;
        for (Channel channel : lstChannels) {
            if (channel.getName().equals(customPatchChannelName)) {
                List<Repository> repositories = channel.getRepositories();
                for (Repository repository : repositories) {
                    repository.getUrl().toString().equals(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toUri().toURL().toString());
                    found = true;
                }
            }
        }

        Assert.assertTrue("Expected channel created for the custom patch no found in " + lstChannels, found);
    }

    public void removeCustomPatch(String customPatchManifest) throws OperationFailedException {
        final InstMgrService instMgrService = (InstMgrService) this.recordedServices.get(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName()).get();
        final String customPatchManifestGA = customPatchManifest;
        final String customPatchManifestGAOperationAttr = customPatchManifestGA.replace(":", "_");
        final String customPatchChannelName = InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX + customPatchManifestGAOperationAttr;

        Path customPatchDir = instMgrService.getCustomPatchDir(customPatchManifestGAOperationAttr);

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCleanHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.CLEAN_CUSTOM_PATCH_MANIFEST).set(customPatchManifestGA);
        executeForResult(op);
        Assert.assertFalse(customPatchDir.resolve(InstMgrConstants.MAVEN_REPO_DIR_NAME_IN_ZIP_FILES).toFile().exists());

        List<Channel> lstChannels = TestInstallationManager.lstChannels;
        boolean found = false;
        for (Channel channel : lstChannels) {
            if (channel.getName().equals(customPatchChannelName)) {
                found = true;
                break;
            }
        }
        Assert.assertFalse("Expected channel was not removed " + lstChannels, found);
    }

    private static void verifyListUpdatesResult(ModelNode response, boolean hasWorkDir) {
        List<ModelNode> results = response.get(InstMgrConstants.LIST_UPDATES_RESULT).asList();
        Assert.assertEquals(3, results.size());
        for (ModelNode result : results) {
            String status = result.get(InstMgrConstants.LIST_UPDATES_STATUS).asString();
            String name = result.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).asString();
            String oldVersion = result.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).asStringOrNull();
            String newVersion = result.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).asStringOrNull();

            if (status.equals(ArtifactChange.Status.INSTALLED.name().toLowerCase())) {
                Assert.assertEquals("org.findupdates:findupdates.installed", name);
                Assert.assertNull(oldVersion);
                Assert.assertEquals("5.0.0.Final", newVersion);
            } else if (status.equals(ArtifactChange.Status.REMOVED.name().toLowerCase())) {
                Assert.assertEquals("org.findupdates:findupdates.removed", name);
                Assert.assertEquals("3.0.0.Final", oldVersion);
                Assert.assertNull(newVersion);
            } else if (status.equals(ArtifactChange.Status.UPDATED.name().toLowerCase())) {
                Assert.assertEquals("org.findupdates:findupdates.updated", name);
                Assert.assertEquals("1.0.0.Final", oldVersion);
                Assert.assertEquals("1.0.1.Final", newVersion);
            } else {
                Assert.fail("Unexpected status for an artifact change: " + status);
            }
        }

        Assert.assertEquals(hasWorkDir, response.hasDefined(InstMgrConstants.LIST_UPDATES_WORK_DIR));
    }

    public static void zipDir(Path sourcePath, Path target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target.toFile()); ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    if (!sourcePath.equals(dir)) {
                        zos.putNextEntry(new ZipEntry(sourcePath.relativize(dir) + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(sourcePath.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public String getCauseLogFailure(String description, String expectedLogCode) {
        return "Unexpected Error Code. Got " + description + " It was expected: " + expectedLogCode;
    }
}
