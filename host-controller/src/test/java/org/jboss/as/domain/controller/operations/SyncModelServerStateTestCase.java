/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.controller.HostRegistrations;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.HostPathManagerService;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.DomainHostExcludeRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.util.AbstractControllerTestBase;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Port of ApplyRemotePrimaryDomainModelHandler to work with syncing using the operations.
 * SecondaryReconnectTestCase contains other tests relevant to this. If maintaining all the mocks
 * for this test becomes too cumbersome, they should be ported to SecondaryReconnectTestCase in the
 * domain testsuite.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SyncModelServerStateTestCase extends AbstractControllerTestBase  {

    static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    static final OperationDefinition TRIGGER_SYNC = new SimpleOperationDefinitionBuilder("trigger-sync", NonResolvingResourceDescriptionResolver.INSTANCE)
            .addParameter(ATTR)
            .build();

    private final ExtensionRegistry extensionRegistry = ExtensionRegistry.builder(ProcessType.HOST_CONTROLLER).build();
    private volatile IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private volatile TestInitializer initializer;
    private volatile Resource rootResource;
    private final Map<String, MockServerProxy> serverProxies;
    private volatile TestSyncRepository repository = new TestSyncRepository();

    public SyncModelServerStateTestCase() {
        super("secondary", ProcessType.HOST_CONTROLLER, true);
        ignoredDomainResourceRegistry = new IgnoredDomainResourceRegistry(hostControllerInfo);
        serverProxies = new HashMap<>();
        serverProxies.put("server-one", new MockServerProxy("server-one"));
        serverProxies.put("server-two", new MockServerProxy("server-two"));
        serverProxies.put("server-three", new MockServerProxy("server-three"));
    }

    @Override
    protected DelegatingResourceDefinitionInitializer createInitializer() {
        initializer = new TestInitializer();
        return initializer;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        rootResource = initializer.initModel(managementModel);
    }

    private void executeTriggerSyncOperation(Resource rootResource) throws Exception {
        ReadMasterDomainModelUtil util = ReadMasterDomainModelUtil.readMasterDomainResourcesForInitialConnect(new NoopTransformers(), null, null, rootResource);
        ModelNode op = Util.createEmptyOperation(TRIGGER_SYNC.getName(), PathAddress.EMPTY_ADDRESS);
        op.get(DOMAIN_MODEL).set(util.getDescribedResources());
        executeForResult(op);
    }

    @Test
    public void testSameModelSync() throws Exception {
        executeTriggerSyncOperation(rootResource.clone());
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals("running", proxy.state);
        }
    }

    @Test
    public void testLegacyModelSync() throws Exception {
        Resource primaryRootResource = rootResource.clone();
        primaryRootResource.getModel().get(PRODUCT_NAME).set("WildFly Core Test");
        primaryRootResource.getModel().get(PRODUCT_VERSION).set("test 2.0");
        Assert.assertFalse(rootResource.getModel().hasDefined(PRODUCT_NAME));
        Assert.assertFalse(rootResource.getModel().hasDefined(PRODUCT_VERSION));
        executeTriggerSyncOperation(primaryRootResource);
        Assert.assertTrue(rootResource.getModel().hasDefined(PRODUCT_NAME));
        Assert.assertEquals("WildFly Core Test", rootResource.getModel().get(PRODUCT_NAME).asString());
        Assert.assertTrue(rootResource.getModel().hasDefined(PRODUCT_VERSION));
        Assert.assertEquals("test 2.0",rootResource.getModel().get(PRODUCT_VERSION).asString());
    }

    @Test
    public void testAddDefaultBoottimeSystemProperty() throws Exception {
        Resource root = rootResource.clone();
        Resource prop = Resource.Factory.create();
        prop.getModel().get(VALUE).set("123");
        //Default is boot-time = true, which indicates a restart is needed
        root.registerChild(PathElement.pathElement(SYSTEM_PROPERTY, "test"), prop);
        executeTriggerSyncOperation(root);
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals(RESTART_REQUIRED, proxy.state);
        }
    }

    @Test
    public void testAddBoottimeFalseSystemProperty() throws Exception {
        Resource root = rootResource.clone();
        Resource prop = Resource.Factory.create();
        prop.getModel().get(VALUE).set("123");
        prop.getModel().get(BOOT_TIME).set(false);
        root.registerChild(PathElement.pathElement(SYSTEM_PROPERTY, "test"), prop);

        executeTriggerSyncOperation(root);
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals(RELOAD_REQUIRED, proxy.state);
        }
    }

    @Test
    public void testAddSocketBinding() throws Exception {
        Resource root = rootResource.clone();
        Resource socketBinding = Resource.Factory.create();
        socketBinding.getModel().get(PORT).set(1000);
        Resource socketBindingGroup = root.requireChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-one"));
        socketBindingGroup.registerChild(PathElement.pathElement(SOCKET_BINDING, "testing"), socketBinding);

        executeTriggerSyncOperation(root);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        Assert.assertEquals("running", serverProxies.get("server-three").state);
    }

    @Test
    public void testAddDeploymentNoInitialGroups() throws Exception {
        Resource root = rootResource.clone();
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeployment(root, "test.jar", bytes);

        executeTriggerSyncOperation(root);
        //No servers should be affected
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals("running", proxy.state);
        }
        Assert.assertTrue(repository.isEmpty());

        //Check deployment exists in model, and that nothing exists in the server group
        ModelNode model = readResourceRecursive();
        checkDeploymentBytes(model, "test.jar", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT));

        //Add a reference and sync again
        registerServerGroupDeployment(root, "group-two", "test.jar");
        executeTriggerSyncOperation(root);
        Assert.assertEquals("running", serverProxies.get("server-one").state);
        Assert.assertEquals("running", serverProxies.get("server-two").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-three").state);
        repository.checkAddedReferences(bytes,
                PathAddress.pathAddress(DEPLOYMENT, "test.jar"),
                PathAddress.pathAddress(SERVER_GROUP, "group-two").append(DEPLOYMENT, "test.jar"));

        model = readResourceRecursive();
        checkDeploymentBytes(model, "test.jar", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT));
        Assert.assertEquals("test.jar", model.get(SERVER_GROUP, "group-two", DEPLOYMENT, "test.jar", RUNTIME_NAME).asString());
    }

    @Test
    public void testAddDeploymentWithGroups() throws Exception {
        final Resource root = rootResource.clone();
        final byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeployment(root, "test.jar", bytes);
        registerServerGroupDeployment(root, "group-one", "test.jar");

        executeTriggerSyncOperation(root);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        Assert.assertEquals("running", serverProxies.get("server-three").state);
        repository.checkAddedReferences(bytes,
                PathAddress.pathAddress(DEPLOYMENT, "test.jar"),
                PathAddress.pathAddress(SERVER_GROUP, "group-one").append(DEPLOYMENT, "test.jar"));
        repository.checkRemovedReferences(null);

        ModelNode model = readResourceRecursive();
        checkDeploymentBytes(model, "test.jar", bytes);
        Assert.assertEquals("test.jar", model.get(SERVER_GROUP, "group-one", DEPLOYMENT, "test.jar", RUNTIME_NAME).asString());
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT));
    }

    @Test
    public void testUpdateDeployment() throws Exception {
        final Resource root = rootResource.clone();
        byte[] oldBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeployment(root, "test.jar", oldBytes);
        registerServerGroupDeployment(root, "group-one", "test.jar");

        executeTriggerSyncOperation(root);
        //Don't bother checking here since it is the same as testAddDeploymentWithGroups()

        reloadServers();

        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        replaceRootDeployment(root, "test.jar", bytes);
        repository.clear(oldBytes);
        executeTriggerSyncOperation(root);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        Assert.assertEquals("running", serverProxies.get("server-three").state);
        repository.checkAddedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT, "test.jar"));
        repository.checkRemovedReferences(oldBytes, PathAddress.pathAddress(DEPLOYMENT, "test.jar"));

        ModelNode model = readResourceRecursive();
        checkDeploymentBytes(model, "test.jar", bytes);
        Assert.assertEquals("test.jar", model.get(SERVER_GROUP, "group-one", DEPLOYMENT, "test.jar", RUNTIME_NAME).asString());
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT));
    }

    @Test
    public void testUpdateDeploymentNotOnHostsServers() throws Exception {
        final Resource root = rootResource.clone();
        byte[] oldBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeployment(root, "test.jar", oldBytes);
        // register deployment in a group that doesn't have any server on this host
        registerServerGroupDeployment(root, "group-three", "test.jar");

        executeTriggerSyncOperation(root);
        reloadServers();

        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        replaceRootDeployment(root, "test.jar", bytes);
        repository.clear(oldBytes);
        executeTriggerSyncOperation(root);
        Assert.assertEquals("running", serverProxies.get("server-one").state);
        Assert.assertEquals("running", serverProxies.get("server-two").state);
        Assert.assertEquals("running", serverProxies.get("server-three").state);
        repository.checkAddedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT, "test.jar"));
        repository.checkRemovedReferences(oldBytes, PathAddress.pathAddress(DEPLOYMENT, "test.jar"));

        ModelNode model = readResourceRecursive();
        checkDeploymentBytes(model, "test.jar", bytes);
        Assert.assertEquals("test.jar", model.get(SERVER_GROUP, "group-three", DEPLOYMENT, "test.jar", RUNTIME_NAME).asString());
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT));
    }

    @Test
    public void testRemoveDeployment() throws Exception {
        final Resource root = rootResource.clone();
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeployment(root, "test.jar", bytes);
        registerServerGroupDeployment(root, "group-one", "test.jar");

        executeTriggerSyncOperation(root);
        //Don't bother checking here since it is the same as testAddDeploymentWithGroups()

        //Reset the server proxies, simulating a reload
        for (MockServerProxy proxy : serverProxies.values()) {
            proxy.state = "running";
        }

        root.removeChild(PathElement.pathElement(DEPLOYMENT, "test.jar"));
        root.getChild(
                PathElement.pathElement(SERVER_GROUP, "group-one")).removeChild(PathElement.pathElement(DEPLOYMENT, "test.jar"));
        repository.clear(bytes);
        executeTriggerSyncOperation(root);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        Assert.assertEquals("running", serverProxies.get("server-three").state);

        Assert.assertEquals(2, repository.removedReferences.size());
        repository.checkRemovedReferences(bytes,
                PathAddress.pathAddress(DEPLOYMENT, "test.jar"),
                PathAddress.pathAddress(SERVER_GROUP, "group-one").append(DEPLOYMENT, "test.jar"));
        repository.checkAddedReferences(null);

        //Reset the server proxies, simulating a reload
        for (MockServerProxy proxy : serverProxies.values()) {
            proxy.state = "running";
        }
        repository.clear();

        ModelNode model = readResourceRecursive();
        Assert.assertFalse(model.hasDefined(DEPLOYMENT, "test.jar"));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT, "test.jar"));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT));

        root.removeChild(PathElement.pathElement(DEPLOYMENT, "test.jar"));
    }

    @Test
    public void testRolloutPlans() throws Exception {
        final Resource root = rootResource.clone();
        byte[] originalHash = new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Resource plans = Resource.Factory.create();
        plans.getModel().get(HASH).set(originalHash);
        root.registerChild(PathElement.pathElement(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS), plans);

        //Add the first plan
        ModelNode group1 = new ModelNode();
        group1.get(SERVER_GROUP, "group-one");
        ModelNode plan = new ModelNode();
        plan.get("test", ROLLOUT_PLAN, IN_SERIES).add(group1);
        repository.addRolloutPlan(plan);
        try {
            executeTriggerSyncOperation(root);
            for (MockServerProxy proxy : serverProxies.values()) {
                Assert.assertEquals("running", proxy.state);
            }

            ModelNode rolloutPlan = readRolloutPlan("test", originalHash);
            List<ModelNode> list = rolloutPlan.get(CONTENT, ROLLOUT_PLAN, IN_SERIES).asList();
            Assert.assertEquals(1, list.size());
            ModelNode entry = list.get(0);
            Assert.assertTrue(entry.has(SERVER_GROUP, "group-one"));

            repository.checkAddedReferences(originalHash, PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS));
            repository.checkRemovedReferences(null);
        } finally {
            repository.deleteVirtualFileAndParent();
        }
        repository.clear();

        //Add another plan
        byte[] newHash = new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        plans.getModel().get(HASH).set(newHash);
        ModelNode group2 = new ModelNode();
        group2.get(SERVER_GROUP, "group-two");
        plan.get("test2", ROLLOUT_PLAN, IN_SERIES).add(group2);
        repository.addRolloutPlan(plan);
        try {
            executeTriggerSyncOperation(root);
            for (MockServerProxy proxy : serverProxies.values()) {
                Assert.assertEquals("running", proxy.state);
            }

            ModelNode rolloutPlan = readRolloutPlan("test", newHash);
            List<ModelNode> list = rolloutPlan.get(CONTENT, ROLLOUT_PLAN, IN_SERIES).asList();
            Assert.assertEquals(1, list.size());
            ModelNode entry = list.get(0);
            Assert.assertTrue(entry.has(SERVER_GROUP, "group-one"));
            rolloutPlan = readRolloutPlan("test2", newHash);
            list = rolloutPlan.get(CONTENT, ROLLOUT_PLAN, IN_SERIES).asList();
            Assert.assertEquals(1, list.size());
            entry = list.get(0);
            Assert.assertTrue(entry.has(SERVER_GROUP, "group-two"));
            repository.checkAddedReferences(newHash, PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS));
            repository.checkRemovedReferences(originalHash, PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS));
        } finally {
            repository.deleteVirtualFileAndParent();
        }
        repository.clear();

        //Remove all plans
        plans.getModel().get(HASH).set(new ModelNode());
        executeTriggerSyncOperation(root);
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals("running", proxy.state);
        }
        ModelNode rolloutPlans = readResourceRecursive().get(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        Assert.assertFalse(rolloutPlans.hasDefined(HASH));
        Assert.assertFalse(rolloutPlans.hasDefined(CONTENT));
    }

    @Test
    public void testDeploymentOverlayNoGroups() throws Exception {
        Resource root = rootResource.clone();
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeploymentOverlay(root, "test", "/some/path", bytes);
        //No servers should be affected
        for (MockServerProxy proxy : serverProxies.values()) {
            Assert.assertEquals("running", proxy.state);
        }

        executeTriggerSyncOperation(root);

        for (MockServerProxy proxy : serverProxies.values()) {
            //All servers will be affected https://issues.jboss.org/browse/WFCORE-710
            //Assert.assertEquals("running", proxy.state);
            Assert.assertEquals("reload-required", proxy.state);
        }
        repository.checkAddedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/some/path"));

        //Check deployment-overlay exists in model, and that nothing exists in the server group
        ModelNode model = readResourceRecursive();
        checkDeploymentOverlayBytes(model, "test", "/some/path", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY));

        //Add a reference and sync again
        registerServerGroupDeploymentOverlay(root, "group-two", "test", "test.jar");
        repository.clear();
        reloadServers();
        executeTriggerSyncOperation(root);

        Assert.assertEquals("running", serverProxies.get("server-one").state);
        Assert.assertEquals("running", serverProxies.get("server-two").state);
        Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-three").state);

        Assert.assertTrue(repository.isEmpty());

        model = readResourceRecursive();
        checkDeploymentOverlayBytes(model, "test", "/some/path", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertTrue(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY, "test", DEPLOYMENT, "test.jar"));
    }

    @Test
    public void testAddDeploymentOverlayWithGroups() throws Exception {
        final Resource root = rootResource.clone();
        final byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeploymentOverlay(root, "test", "/some/path", bytes);
        registerServerGroupDeploymentOverlay(root, "group-two", "test", "test.jar");

        executeTriggerSyncOperation(root);
        //All servers will be affected https://issues.jboss.org/browse/WFCORE-710
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        //Assert.assertEquals("running", serverProxies.get("server-three").state);
        for (MockServerProxy proxy : serverProxies.values()) {
            //Temp check instead of the above https://issues.jboss.org/browse/WFCORE-710
            Assert.assertEquals("reload-required", proxy.state);
        }

        repository.checkAddedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/some/path"));
        repository.checkRemovedReferences(null);

        ModelNode model = readResourceRecursive();
        checkDeploymentOverlayBytes(model, "test", "/some/path", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertTrue(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY, "test", DEPLOYMENT, "test.jar"));
    }

    @Test
    public void testUpdateDeploymentOverlay() throws Exception {
        final Resource root = rootResource.clone();
        byte[] oldBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeploymentOverlay(root, "test", "/some/path", oldBytes);
        registerServerGroupDeploymentOverlay(root, "group-two", "test", "test.jar");

        executeTriggerSyncOperation(root);
        //Don't bother checking here since it is the same as testAddDeploymentOverlayWithGroups()

        reloadServers();

        //Do a change replacing the bytes in a content item
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        Resource overlay = root.getChild(PathElement.pathElement(DEPLOYMENT_OVERLAY, "test"));
        Resource content = overlay.getChild(PathElement.pathElement(CONTENT, "/some/path"));
        content.getModel().get(CONTENT).set(bytes);

        repository.clear();
        executeTriggerSyncOperation(root);
        //All servers will be affected https://issues.jboss.org/browse/WFCORE-710
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        //Assert.assertEquals("running", serverProxies.get("server-three").state);
        for (MockServerProxy proxy : serverProxies.values()) {
            //Temp check instead of the above https://issues.jboss.org/browse/WFCORE-710
            Assert.assertEquals("reload-required", proxy.state);
        }
        repository.checkAddedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/some/path"));
        repository.checkRemovedReferences(oldBytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/some/path"));

        ModelNode model = readResourceRecursive();
        checkDeploymentOverlayBytes(model, "test", "/some/path", bytes);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertTrue(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY, "test", DEPLOYMENT, "test.jar"));

        //Do a change adding another content item
        Resource newContent = Resource.Factory.create();
        newContent.getModel().get(CONTENT).set(oldBytes);
        overlay.registerChild(PathElement.pathElement(CONTENT, "/other/path"), newContent);
        repository.clear();
        executeTriggerSyncOperation(root);
        //All servers will be affected https://issues.jboss.org/browse/WFCORE-710
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        //Assert.assertEquals("running", serverProxies.get("server-three").state);
        for (MockServerProxy proxy : serverProxies.values()) {
            //Temp check instead of the above https://issues.jboss.org/browse/WFCORE-710
            Assert.assertEquals("reload-required", proxy.state);
        }
        repository.checkAddedReferences(oldBytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/other/path"));
        model = readResourceRecursive();
        Map<String, byte[]> contents = new HashMap<>();
        contents.put("/some/path", bytes);
        contents.put("/other/path", oldBytes);
        checkDeploymentOverlayBytes(model, "test", contents);
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertTrue(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY, "test", DEPLOYMENT, "test.jar"));
    }

    @Test
    public void testRemoveDeploymentOverlay() throws Exception {
        final Resource root = rootResource.clone();
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        registerRootDeploymentOverlay(root, "test", "/some/path", bytes);
        registerServerGroupDeploymentOverlay(root, "group-two", "test", "test.jar");

        executeTriggerSyncOperation(root);
        //Don't bother checking here since it is the same as testAddDeploymentOverlayWithGroups()

        reloadServers();
        repository.clear();

        //Remove the resources
        root.removeChild(PathElement.pathElement(DEPLOYMENT_OVERLAY, "test"));
        root.getChild(PathElement.pathElement(SERVER_GROUP, "group-two"))
                .removeChild(PathElement.pathElement(DEPLOYMENT_OVERLAY, "test"));
        executeTriggerSyncOperation(root);
        //All servers will be affected https://issues.jboss.org/browse/WFCORE-710
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-one").state);
        //Assert.assertEquals(RELOAD_REQUIRED, serverProxies.get("server-two").state);
        //Assert.assertEquals("running", serverProxies.get("server-three").state);
        for (MockServerProxy proxy : serverProxies.values()) {
            //Temp check instead of the above https://issues.jboss.org/browse/WFCORE-710
            Assert.assertEquals("reload-required", proxy.state);
        }
        repository.checkAddedReferences(null);
        repository.checkRemovedReferences(bytes, PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test").append(CONTENT, "/some/path"));
        ModelNode model = readResourceRecursive();
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-one", DEPLOYMENT_OVERLAY));
        Assert.assertFalse(model.hasDefined(SERVER_GROUP, "group-two", DEPLOYMENT_OVERLAY));
        Assert.assertFalse(model.hasDefined(DEPLOYMENT_OVERLAY));
    }

    private void registerRootDeploymentOverlay(Resource root, String name, String path, byte[] bytes) {
        Resource overlay = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(DEPLOYMENT_OVERLAY, name), overlay);
        Resource content = Resource.Factory.create();
        content.getModel().get(CONTENT).set(bytes);
        overlay.registerChild(PathElement.pathElement(CONTENT, path), content);
    }

    private void registerRootDeployment(Resource root, String deploymentName, byte[] bytes) {
        Resource deployment = Resource.Factory.create();
        deployment.getModel().get(NAME).set(deploymentName);
        deployment.getModel().get(RUNTIME_NAME).set(deploymentName);
        setDeploymentBytes(deployment, bytes);
        root.registerChild(PathElement.pathElement(DEPLOYMENT, deploymentName), deployment);
    }

    private void replaceRootDeployment(Resource root, String deploymentName, byte[] bytes) {
        Resource deployment = root.getChild(PathElement.pathElement(DEPLOYMENT, deploymentName));
        deployment.getModel().remove(CONTENT);
        setDeploymentBytes(deployment, bytes);
    }

    private void setDeploymentBytes(Resource deployment, byte[] bytes) {
        ModelNode content = deployment.getModel().get(CONTENT);
        ModelNode hash = new ModelNode();
        hash.get(HASH).set(bytes);
        content.add(hash);
    }

    private void registerServerGroupDeploymentOverlay(Resource root, String groupName, String overlayName, String deploymentName) {
        Resource group = root.requireChild(PathElement.pathElement(SERVER_GROUP, groupName));
        Resource overlay = Resource.Factory.create();
        group.registerChild(PathElement.pathElement(DEPLOYMENT_OVERLAY, overlayName), overlay);
        Resource deployment = Resource.Factory.create();
        deployment.getModel().setEmptyObject();
        overlay.registerChild(PathElement.pathElement(DEPLOYMENT, deploymentName), deployment);
    }

    private void registerServerGroupDeployment(Resource root, String groupName, String deploymentName) {
        Resource group = root.requireChild(PathElement.pathElement(SERVER_GROUP, groupName));
        Resource deployment = Resource.Factory.create();
        deployment.getModel().get(ENABLED).set(true);
        deployment.getModel().get(RUNTIME_NAME).set(deploymentName);
        group.registerChild(PathElement.pathElement(DEPLOYMENT, deploymentName), deployment);
    }

    private void checkDeploymentBytes(ModelNode model, String name, byte[] bytes) {
        Assert.assertEquals(1, model.get(DEPLOYMENT).keys().size());
        ModelNode content = model.get(DEPLOYMENT, name, CONTENT);
        Assert.assertEquals(1, content.asList().size());
        Assert.assertArrayEquals(bytes, content.asList().get(0).get(HASH).asBytes());
    }

    private void checkDeploymentOverlayBytes(ModelNode model, String name, String path, byte[] bytes) {
        checkDeploymentOverlayBytes(model, name, Collections.singletonMap(path, bytes));
    }

    private void checkDeploymentOverlayBytes(ModelNode model, String name, Map<String, byte[]> contents) {
        Assert.assertEquals(1, model.get(DEPLOYMENT_OVERLAY).keys().size());
        ModelNode overlay = model.get(DEPLOYMENT_OVERLAY, name);
        Assert.assertEquals(contents.size(), overlay.get(CONTENT).keys().size());
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            ModelNode content = overlay.get(CONTENT, entry.getKey());
            Assert.assertArrayEquals(entry.getValue(), content.get(CONTENT).asBytes());
        }
    }

    private void reloadServers() {
        //Reset the server proxies, simulating a reload
        for (MockServerProxy proxy : serverProxies.values()) {
            proxy.state = "running";
        }
    }

    private ModelNode readRolloutPlan(String planName, byte[] expectedRootHash) throws OperationFailedException {
        PathAddress plansAddr = PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode hashRead = Util.getReadAttributeOperation(plansAddr, HASH);
        ModelNode result = executeForResult(hashRead);
        Assert.assertArrayEquals(expectedRootHash, result.asBytes());

        ModelNode readPlanOp = Util.createOperation(READ_RESOURCE_OPERATION, plansAddr.append(ROLLOUT_PLAN, planName));
        readPlanOp.get(INCLUDE_RUNTIME).set(true);
        return executeForResult(readPlanOp);
    }

    private class TestInitializer implements DelegatingResourceDefinitionInitializer {
        private volatile HostResourceDefinition hostResourceDefinition;
        private volatile ManagementModel managementModel;
        private volatile ManagementResourceRegistration hostRegistration;

        @Override
        public void setDelegate() {
            final ExtensibleConfigurationPersister configurationPersister = new EmptyConfigurationPersister();
            final boolean isPrimary = false;
            final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry = new IgnoredDomainResourceRegistry(hostControllerInfo);
            final PathManagerService pathManager = new HostPathManagerService(capabilityRegistry);
            final DelegatingConfigurableAuthorizer authorizer = new DelegatingConfigurableAuthorizer();
            final ManagementSecurityIdentitySupplier securityIdentitySupplier = new ManagementSecurityIdentitySupplier();
            final HostRegistrations hostRegistrations = null;
            final DomainHostExcludeRegistry domainHostExcludeRegistry = new DomainHostExcludeRegistry();
            final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider = new MutableRootResourceRegistrationProvider() {
                @Override
                public ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context) {
                    return managementModel.getRootResourceRegistration();
                }
            };
            DomainRootDefinition domain = new DomainRootDefinition(domainController, hostControllerEnvironment, configurationPersister,
                    repository, repository, isPrimary, hostControllerInfo, extensionRegistry, ignoredDomainResourceRegistry,
                    pathManager, authorizer, securityIdentitySupplier, hostRegistrations, domainHostExcludeRegistry, rootResourceRegistrationProvider);
            getDelegatingResourceDefiniton().setDelegate(domain);

            final String hostName = hostControllerEnvironment.getHostName();
            final HostControllerConfigurationPersister hostControllerConfigurationPersister =
                    new HostControllerConfigurationPersister(hostControllerEnvironment,
                            hostControllerInfo, Executors.newCachedThreadPool(), extensionRegistry, extensionRegistry);
            final HostRunningModeControl runningModeControl = new HostRunningModeControl(RunningMode.NORMAL, RestartMode.SERVERS);
            final ServerInventory serverInventory = null;
            final HostFileRepository remoteFileRepository = repository;
            final ControlledProcessState processState = null;
            final ManagedAuditLogger auditLogger = null;
            final BootErrorCollector bootErrorCollector = null;
            //Save this for later since setDelegate() gets called before initModel....
            hostResourceDefinition = new HostResourceDefinition(hostName, hostControllerConfigurationPersister,
                    hostControllerEnvironment, runningModeControl, repository, hostControllerInfo, serverInventory, remoteFileRepository,
                    repository, domainController, extensionRegistry, ignoredDomainResourceRegistry, processState,
                    pathManager, authorizer, securityIdentitySupplier, auditLogger, bootErrorCollector);
        }

        protected Resource initModel(final ManagementModel managementModel) {
            this.managementModel = managementModel;
            ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            //Use the saved hostResourceDefinition
            hostRegistration = rootRegistration.registerSubModel(hostResourceDefinition);

            rootRegistration.registerOperationHandler(TRIGGER_SYNC, new TriggerSyncHandler());
            GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
            rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);


            Resource rootResource = managementModel.getRootResource();
            CoreManagementResourceDefinition.registerDomainResource(rootResource, null);

            final Resource host = Resource.Factory.create();
            final Resource serverOneConfig = Resource.Factory.create();
            final ModelNode serverOneModel = new ModelNode();
            serverOneModel.get(GROUP).set("group-one");
            serverOneModel.get(SOCKET_BINDING_GROUP).set("binding-one");
            serverOneConfig.writeModel(serverOneModel);
            host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-one"), serverOneConfig);

            final Resource serverTwoConfig = Resource.Factory.create();
            final ModelNode serverTwoModel = new ModelNode();
            serverTwoModel.get(GROUP).set("group-one");
            serverTwoConfig.writeModel(serverTwoModel);
            host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-two"), serverTwoConfig);

            final Resource serverThreeConfig = Resource.Factory.create();
            final ModelNode serverThreeModel = new ModelNode();
            serverThreeModel.get(GROUP).set("group-two");
            serverThreeConfig.writeModel(serverThreeModel);
            host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-three"), serverThreeConfig);

            rootResource.registerChild(PathElement.pathElement(HOST, hostName), host);
            final Resource serverGroup1 = Resource.Factory.create();
            serverGroup1.getModel().get(PROFILE).set("profile-one");
            serverGroup1.getModel().get(SOCKET_BINDING_GROUP).set("binding-one");
            rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "group-one"), serverGroup1);

            final Resource serverGroup2 = Resource.Factory.create();
            serverGroup2.getModel().get(PROFILE).set("profile-two");
            serverGroup2.getModel().get(SOCKET_BINDING_GROUP).set("binding-two");
            rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "group-two"), serverGroup2);

            // group three has no servers assigned
            final Resource serverGroup3 = Resource.Factory.create();
            serverGroup3.getModel().get(PROFILE).set("profile-three");
            serverGroup3.getModel().get(SOCKET_BINDING_GROUP).set("binding-three");
            rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "group-three"), serverGroup3);

            final Resource profile1 = Resource.Factory.create();
            profile1.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-one"), profile1);
            final Resource profile2 = Resource.Factory.create();
            profile2.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-two"), profile2);
            final Resource profile3 = Resource.Factory.create();
            profile3.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-three"), profile3);

            final Resource binding1 = Resource.Factory.create();
            binding1.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-one"), binding1);
            final Resource binding2 = Resource.Factory.create();
            binding2.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-two"), binding2);
            final Resource binding3 = Resource.Factory.create();
            binding3.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"), binding3);

            registerServer("server-one");
            registerServer("server-two");
            registerServer("server-three");
            return rootResource;
        }

        private void registerServer(String serverName) {
            PathElement pe = PathElement.pathElement(RUNNING_SERVER, serverName);
            hostRegistration.registerProxyController(pe, serverProxies.get(serverName));
            final ManagementResourceRegistration serverRegistration = hostRegistration.getSubModel(PathAddress.EMPTY_ADDRESS.append(pe));
            serverRegistration.registerOperationHandler(ServerProcessStateHandler.RELOAD_DEFINITION,  serverProxies.get(serverName));
            serverRegistration.registerOperationHandler(ServerProcessStateHandler.RESTART_DEFINITION,  serverProxies.get(serverName));

        }

    }

    class TriggerSyncHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            final ModelNode syncOperation = new ModelNode();
            syncOperation.get(OP).set("calculate-diff-and-sync");
            syncOperation.get(OP_ADDR).setEmptyList();
            syncOperation.get(DOMAIN_MODEL).set(operation.get(DOMAIN_MODEL));

            Resource original = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

            final HostControllerRegistrationHandler.OperationExecutor internalExecutor = getControllerService().getInternalExecutor();

            Map<String, ProxyController> serverProxies = new HashMap<>();
            for (Map.Entry<String, MockServerProxy> entry : SyncModelServerStateTestCase.this.serverProxies.entrySet()) {
                serverProxies.put(entry.getKey(), entry.getValue());
            }
            SyncModelParameters parameters =
                    new SyncModelParameters(new MockDomainController(), ignoredDomainResourceRegistry,
                            hostControllerEnvironment, extensionRegistry, internalExecutor, true,
                            serverProxies, repository, repository);
            final Resource hostResource =
                    context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS).getChildren(HOST).iterator().next();
            final ProductConfig cfg = new ProductConfig("product", "version", "main");
            final HostInfo hostInfo =
                    HostInfo.fromModelNode(
                            HostInfo.createLocalHostHostInfo(hostControllerInfo,
                                    cfg,
                                    ignoredDomainResourceRegistry,
                                    hostResource));
            final SyncDomainModelOperationHandler handler =
                    new SyncDomainModelOperationHandler(hostInfo, parameters);
            context.addStep(syncOperation, handler, OperationContext.Stage.MODEL, true);
        }
    }

    private class MockServerProxy implements ProxyController, OperationStepHandler {
        private final String serverName;
        private volatile String state = "running";

        public MockServerProxy(String serverName) {
            this.serverName = serverName;
        }

        @Override
        public PathAddress getProxyNodeAddress() {
            return PathAddress.pathAddress(RUNNING_SERVER, serverName);
        }

        @Override
        public void execute(ModelNode operation, OperationMessageHandler handler, ProxyOperationControl control,
                            OperationAttachments attachments, BlockingTimeout blockingTimeout) {
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
            Assert.assertEquals(serverName, addr.getLastElement().getValue());
            String opName = operation.require(OP).asString();
            if (opName.equals(ServerProcessStateHandler.REQUIRE_RESTART_OPERATION)) {
                state = RESTART_REQUIRED;
            } else if (opName.equals(ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION)) {
                state = RELOAD_REQUIRED;
            } else {
                throw new IllegalStateException("Unknown state for my intents and purposes");
            }
        }
    }

    private static class TestSyncRepository extends TestRepository {
        //hasContent() gets called as a check on remove so add deployments here to make sure they get removed
        //from the repository
        private final Set<String> expectedContent = new HashSet<>();

        //Rollout plans need actual content
        private volatile VirtualFile vf;

        private final Map<String, ContentReference> addedContentReferences = new HashMap<>();
        private final Map<String, ContentReference> fetchedFiles = new HashMap<>();
        private final Map<String, ContentReference> removedReferences = new HashMap<>();
        @Override
        public void addContentReference(ContentReference reference) {
            addedContentReferences.put(reference.getContentIdentifier(),reference);
        }

        @Override
        public File[] getDeploymentFiles(ContentReference reference) {
            fetchedFiles.put(reference.getContentIdentifier(), reference);
            return null;
        }

        @Override
        public void removeContent(ContentReference reference) {
            removedReferences.put(reference.getContentIdentifier(), reference);
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return expectedContent.contains(HashUtil.bytesToHexString(hash));
        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            if (hash[0] == 1) {
                return vf;

            }
            return null;
        }

        boolean isEmpty() {
            return addedContentReferences.isEmpty() && fetchedFiles.isEmpty();
        }

        void checkAddedReferences(byte[] bytes, PathAddress... addresses) {
            Assert.assertEquals(addresses.length, addedContentReferences.size());
            Assert.assertEquals(addresses.length, fetchedFiles.size());
            for (PathAddress address : addresses) {
                ContentReference ref = addedContentReferences.get(address.toCLIStyleString());
                Assert.assertNotNull(ref);
                Assert.assertEquals(HashUtil.bytesToHexString(bytes), ref.getHexHash());

                ref = fetchedFiles.get(address.toCLIStyleString());
                Assert.assertNotNull(ref);
                Assert.assertEquals(HashUtil.bytesToHexString(bytes), ref.getHexHash());
            }
        }

        void checkRemovedReferences(byte[] bytes, PathAddress...addresses) {
            Assert.assertEquals(addresses.length, removedReferences.size());
            for (PathAddress address : addresses) {
                ContentReference ref = removedReferences.get(address.toCLIStyleString());
                Assert.assertNotNull(ref);
                Assert.assertEquals(HashUtil.bytesToHexString(bytes), ref.getHexHash());
            }
        }

        void clear(byte[]...currentContent) {
            expectedContent.clear();
            addedContentReferences.clear();
            fetchedFiles.clear();
            removedReferences.clear();
            for (byte[] content : currentContent) {
                expectedContent.add(HashUtil.bytesToHexString(content));
            }
        }

        void addRolloutPlan(ModelNode dmr) throws Exception {
            File systemTmpDir = new File(System.getProperty("java.io.tmpdir"));
            File tempDir = new File(systemTmpDir, "test" + System.currentTimeMillis());
            tempDir.mkdir();
            File file = new File(tempDir, "content");
            this.vf = VFS.getChild(file.toURI());

            try (final PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))){
                dmr.writeString(out, true);
            }
        }

        void deleteVirtualFileAndParent() {
            if (vf != null) {
                vf.delete();
                vf.getParent().delete();
            }
        }
    }
}
