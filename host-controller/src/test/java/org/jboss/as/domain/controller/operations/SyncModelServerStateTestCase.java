package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.ControlledProcessState;
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
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
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
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.util.AbstractControllerTestBase;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Port of ApplyRemoteMasterDomainModelHandler to work with syncing using the operations.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SyncModelServerStateTestCase extends AbstractControllerTestBase  {

    static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    static final AttributeDefinition[] REQUEST_ATTRIBUTES = new AttributeDefinition[]{ATTR};
    static final OperationDefinition TRIGGER_SYNC = new SimpleOperationDefinitionBuilder("trigger-sync", new NonResolvingResourceDescriptionResolver())
            .addParameter(ATTR)
            .build();

    private final ExtensionRegistry extensionRegistry = new ExtensionRegistry(ProcessType.HOST_CONTROLLER, new RunningModeControl(RunningMode.NORMAL), null, null, RuntimeHostControllerInfoAccessor.SERVER);
    private volatile IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private volatile TestInitializer initializer;
    private volatile Resource rootResource;
    private final Map<String, MockServerProxy> serverProxies;


    public SyncModelServerStateTestCase() {
        super("slave", ProcessType.HOST_CONTROLLER, true);
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
        ReadMasterDomainModelUtil util = ReadMasterDomainModelUtil.readMasterDomainResourcesForInitialConnect(null, new NoopTransformers(), rootResource, null);
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

    private class TestInitializer implements DelegatingResourceDefinitionInitializer {
        private volatile HostResourceDefinition hostResourceDefinition;
        private volatile ManagementModel managementModel;
        private volatile ManagementResourceRegistration hostRegistration;

        @Override
        public void setDelegate() {
            final ContentRepository contentRepo = createContentRepository();

            final ExtensibleConfigurationPersister configurationPersister = new EmptyConfigurationPersister();
            final HostFileRepository fileRepository = createHostFileRepository();
            final boolean isMaster = false;
            final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry = new IgnoredDomainResourceRegistry(hostControllerInfo);
            final PathManagerService pathManager = new HostPathManagerService();
            final DelegatingConfigurableAuthorizer authorizer = new DelegatingConfigurableAuthorizer();
            final HostRegistrations hostRegistrations = null;
            final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider = new MutableRootResourceRegistrationProvider() {
                @Override
                public ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context) {
                    return managementModel.getRootResourceRegistration();
                }
            };
            DomainRootDefinition domain = new DomainRootDefinition(domainController, hostControllerEnvironment, configurationPersister,
                    contentRepo, fileRepository, isMaster, hostControllerInfo, extensionRegistry, ignoredDomainResourceRegistry,
                    pathManager, authorizer, hostRegistrations, rootResourceRegistrationProvider);
            getDelegatingResourceDefiniton().setDelegate(domain);

            final String hostName = hostControllerEnvironment.getHostName();
            final HostControllerConfigurationPersister hostControllerConfigurationPersister =
                    new HostControllerConfigurationPersister(hostControllerEnvironment,
                            hostControllerInfo, Executors.newCachedThreadPool(), extensionRegistry, extensionRegistry);
            final HostRunningModeControl runningModeControl = new HostRunningModeControl(RunningMode.NORMAL, RestartMode.SERVERS);
            final ServerInventory serverInventory = null;
            final HostFileRepository remoteFileRepository = createHostFileRepository();
            final AbstractVaultReader vaultReader = null;
            final ControlledProcessState processState = null;
            final ManagedAuditLogger auditLogger = null;
            final BootErrorCollector bootErrorCollector = null;
            //Save this for later since setDelegate() gets called before initModel....
            hostResourceDefinition = new HostResourceDefinition(hostName, hostControllerConfigurationPersister,
                    hostControllerEnvironment, runningModeControl, fileRepository, hostControllerInfo, serverInventory, remoteFileRepository,
                    contentRepo, domainController, extensionRegistry, vaultReader, ignoredDomainResourceRegistry, processState,
                    pathManager, authorizer, auditLogger, bootErrorCollector);
        }

        protected Resource initModel(final ManagementModel managementModel) {
            this.managementModel = managementModel;
            //Use the saved hostResourceDefinition
            hostRegistration = managementModel.getRootResourceRegistration().registerSubModel(hostResourceDefinition);

            managementModel.getRootResourceRegistration().registerOperationHandler(TRIGGER_SYNC, new TriggerSyncHandler());
            GlobalOperationHandlers.registerGlobalOperations(managementModel.getRootResourceRegistration(), processType);


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

            final Resource profile1 = Resource.Factory.create();
            profile1.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-one"), profile1);
            final Resource profile2 = Resource.Factory.create();
            profile2.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-two"), profile2);

            final Resource binding1 = Resource.Factory.create();
            binding1.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-one"), binding1);
            final Resource binding2 = Resource.Factory.create();
            binding2.getModel().setEmptyObject();
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-two"), binding2);

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
                            serverProxies);
            final SyncServerGroupOperationHandler handler =
                    new SyncServerGroupOperationHandler("slave", original, parameters);
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
                            OperationAttachments attachments) {
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
}