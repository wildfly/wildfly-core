/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.core.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.SlaveRegistrationException;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.HostModelUtil.HostModelRegistrar;
import org.jboss.as.host.controller.HostPathManagerService;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.DomainHostExcludeRegistry;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.operations.DomainControllerWriteAttributeHandler;
import org.jboss.as.host.controller.operations.LocalDomainControllerAddHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.RuntimeExpressionResolver;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironment.LaunchType;
import org.jboss.as.server.ServerEnvironmentResourceDescription;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.ServerPathManagerService;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.controller.resources.VersionModelInitializer;
import org.jboss.as.version.ProductConfig;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class TestModelControllerService extends ModelTestModelControllerService {

    private final InjectedValue<ContentRepository> injectedContentRepository = new InjectedValue<>();
    private final TestModelType type;
    private final RunningModeControl runningModeControl;
    private final PathManagerService pathManagerService;
    private final ModelInitializer modelInitializer;
    private final TestDelegatingResourceDefinition rootResourceDefinition;
    private final ControlledProcessState processState;
    private final ExtensionRegistry extensionRegistry;
    private final CapabilityRegistry capabilityRegistry;
    private volatile Initializer initializer;

    TestModelControllerService(ProcessType processType, RunningModeControl runningModeControl, StringConfigurationPersister persister, ModelTestOperationValidatorFilter validateOpsFilter,
            TestModelType type, ModelInitializer modelInitializer, TestDelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState, ExtensionRegistry extensionRegistry,
            CapabilityRegistry capabilityRegistry, RuntimeExpressionResolver expressionResolver) {
        super(processType, runningModeControl, null, persister, validateOpsFilter, rootResourceDefinition, processState,
                expressionResolver, capabilityRegistry);
        this.type = type;
        this.runningModeControl = runningModeControl;
        this.pathManagerService = type == TestModelType.STANDALONE ? new ServerPathManagerService(capabilityRegistry) : new HostPathManagerService(capabilityRegistry);
        this.modelInitializer = modelInitializer;
        this.rootResourceDefinition = rootResourceDefinition;
        this.processState = processState;
        this.extensionRegistry = extensionRegistry;
        this.capabilityRegistry = capabilityRegistry;

        if (type == TestModelType.STANDALONE) {
            initializer = new ServerInitializer();
        } else if (type == TestModelType.HOST) {
            initializer = new HostInitializer();
        } else if (type == TestModelType.DOMAIN) {
            initializer = new DomainInitializer();
        }
    }

    static TestModelControllerService create(ProcessType processType, RunningModeControl runningModeControl, StringConfigurationPersister persister, ModelTestOperationValidatorFilter validateOpsFilter,
            TestModelType type, ModelInitializer modelInitializer, ExtensionRegistry extensionRegistry, CapabilityRegistry capabilityRegistry) {
        RuntimeExpressionResolver expressionResolver = new RuntimeExpressionResolver();
        extensionRegistry.setResolverExtensionRegistry(expressionResolver);
        return new TestModelControllerService(processType, runningModeControl, persister, validateOpsFilter, type, modelInitializer,
                new TestDelegatingResourceDefinition(type), new ControlledProcessState(true), extensionRegistry, capabilityRegistry, expressionResolver);
    }

    InjectedValue<ContentRepository> getContentRepositoryInjector(){
        return injectedContentRepository;
    }


    @Override
    public void start(StartContext context) throws StartException {
        if (initializer != null) {
            initializer.setRootResourceDefinitionDelegate();
        }
        super.start(context);
        //todo super hack
        if (type == TestModelType.STANDALONE){
            ServerEnvironmentService.addService(((ServerInitializer)initializer).environment, context.getChildTarget());
        }

    }

    @Override
    protected void initCoreModel(ManagementModel managementModel, Resource modelControllerResource) {
        if (modelInitializer != null) {
            modelInitializer.populateModel(managementModel);
        }
        System.setProperty("jboss.as.test.disable.runtime", "1");
        initializer.initCoreModel(managementModel.getRootResource(), managementModel.getRootResourceRegistration(), modelControllerResource);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        System.clearProperty("jboss.as.test.disable.runtime");
    }

    private ServerEnvironment createStandaloneServerEnvironment() {
        Properties props = new Properties();
        File home = new File("target/jbossas");
        delete(home);
        home.mkdir();
        delay(10);
        props.put(ServerEnvironment.HOME_DIR, home.getAbsolutePath());

        File standalone = new File(home, "standalone");
        standalone.mkdir();
        props.put(ServerEnvironment.SERVER_BASE_DIR, standalone.getAbsolutePath());

        File configuration = new File(standalone, "configuration");
        configuration.mkdir();
        props.put(ServerEnvironment.SERVER_CONFIG_DIR, configuration.getAbsolutePath());

        File xml = new File(configuration, "standalone.xml");
        try {
            xml.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        props.put(ServerEnvironment.JBOSS_SERVER_DEFAULT_CONFIG, "standalone.xml");
        ProductConfig pc =  new ProductConfig("Test", Version.AS_VERSION, "main");
        return new ServerEnvironment(null, props, new HashMap<String, String>(), "standalone.xml", null, LaunchType.STANDALONE, runningModeControl.getRunningMode(), pc, false);
    }

    private HostControllerEnvironment createHostControllerEnvironment() {
        try {
            Map<String, String> props = new HashMap<String, String>();
            File home = new File("target/jbossas");
            delete(home);
            home.mkdir();
            int sleep = 10;
            delay(sleep);
            props.put(HostControllerEnvironment.HOME_DIR, home.getAbsolutePath());

            File domain = new File(home, "domain");
            domain.mkdir();
            delay(sleep);
            props.put(HostControllerEnvironment.DOMAIN_BASE_DIR, domain.getAbsolutePath());

            File configuration = new File(domain, "configuration");
            configuration.mkdir();
            delay(sleep);
            props.put(HostControllerEnvironment.DOMAIN_CONFIG_DIR, configuration.getAbsolutePath());


            boolean isRestart = false;
            String modulePath = "";
            InetAddress processControllerAddress = InetAddress.getLocalHost();
            Integer processControllerPort = 9999;
            InetAddress hostControllerAddress = InetAddress.getLocalHost();
            Integer hostControllerPort = 1234;
            String defaultJVM = null;
            String domainConfig = null;
            String initialDomainConfig = null;
            String hostConfig = null;
            String initialHostConfig = null;
            RunningMode initialRunningMode = runningModeControl.getRunningMode();
            boolean backupDomainFiles = false;
            boolean useCachedDc = false;
            ProductConfig productConfig = ProductConfig.fromFilesystemSlot(null, "",  props);
            return new HostControllerEnvironment(props, isRestart, modulePath, processControllerAddress, processControllerPort,
                    hostControllerAddress, hostControllerPort, defaultJVM, domainConfig, initialDomainConfig, hostConfig, initialHostConfig,
                    initialRunningMode, backupDomainFiles, useCachedDc, productConfig);
        } catch (UnknownHostException e) {
            // AutoGenerated
            throw new RuntimeException(e);
        }
    }

    private void delay(int sleep) {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private LocalHostControllerInfoImpl createLocalHostControllerInfo(HostControllerEnvironment env) {
        return new LocalHostControllerInfoImpl(null, env);
    }

    private HostFileRepository createHostFileRepository() {
        return new HostFileRepository() {

            @Override
            public File getDeploymentRoot(ContentReference reference) {
                return null;
            }

            @Override
            public File[] getDeploymentFiles(ContentReference reference) {
                return null;
            }

            @Override
            public void deleteDeployment(ContentReference reference) {
            }

            @Override
            public File getFile(String relativePath) {
                return null;
            }

            @Override
            public File getConfigurationFile(String relativePath) {
                return null;
            }
        };
    }

    private DomainController createDomainController(final HostControllerEnvironment env, final LocalHostControllerInfoImpl info) {
        return new DomainController() {

            @Override
            public void unregisterRunningServer(String serverName) {
            }

            @Override
            public void unregisterRemoteHost(String id, Long remoteConnectionId, boolean cleanShutdown) {
            }

            @Override
            public void stopLocalHost(int exitCode) {
            }

            @Override
            public void stopLocalHost() {
            }

            @Override
            public void registerRunningServer(ProxyController serverControllerClient) {
            }

            @Override
            public void registerRemoteHost(String hostName, ManagementChannelHandler handler, Transformers transformers,
                    Long remoteConnectionId, boolean registerProxyController) throws SlaveRegistrationException {
            }

            @Override
            public void pingRemoteHost(String hostName) {
            }

            @Override
            public boolean isHostRegistered(String id) {
                return false;
            }

            @Override
            public HostFileRepository getRemoteFileRepository() {
                return null;
            }

            @Override
            public ModelNode getProfileOperations(String profileName) {
                return null;
            }

            @Override
            public LocalHostControllerInfo getLocalHostInfo() {
                return info;
            }

            @Override
            public HostFileRepository getLocalFileRepository() {
                return null;
            }

            @Override
            public ExtensionRegistry getExtensionRegistry() {
                return null;
            }

            @Override
            public ImmutableCapabilityRegistry getCapabilityRegistry() {
                return capabilityRegistry;
            }

            @Override
            public RunningMode getCurrentRunningMode() {
                return null;
            }

            @Override
            public ExpressionResolver getExpressionResolver() {
                return null;
            }

            @Override
            public void initializeMasterDomainRegistry(ManagementResourceRegistration root,
                    ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository,
                    HostFileRepository fileRepository, ExtensionRegistry extensionRegistry, PathManagerService pathManager) {
            }

            @Override
            public void initializeSlaveDomainRegistry(ManagementResourceRegistration root,
                    ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository,
                    HostFileRepository fileRepository, LocalHostControllerInfo hostControllerInfo,
                    ExtensionRegistry extensionRegistry, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                    PathManagerService pathManager) {
            }
        };
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        file.delete();
    }

    private interface Initializer {
        void setRootResourceDefinitionDelegate();
        void initCoreModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource);
    }

    private class ServerInitializer implements Initializer {
        final ExtensibleConfigurationPersister persister = new NullConfigurationPersister();
        final ServerEnvironment environment = createStandaloneServerEnvironment();
        final boolean parallelBoot = false;

        public void setRootResourceDefinitionDelegate() {
            rootResourceDefinition.setDelegate(new ServerRootResourceDefinition(
                    injectedContentRepository.getValue(),
                    persister,
                    environment,
                    processState,
                    runningModeControl,
                    extensionRegistry,
                    parallelBoot,
                    pathManagerService,
                    null,
                    authorizer,
                    securityIdentitySupplier,
                    AuditLogger.NO_OP_LOGGER,
                    getMutableRootResourceRegistrationProvider(),
                    getBootErrorCollector(), capabilityRegistry));
        }

        @Override
        public void initCoreModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource) {
            VersionModelInitializer.registerRootResource(rootResource, null);
            Resource managementResource = Resource.Factory.create();
            rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MANAGEMENT), managementResource);
            rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.SERVICE_CONTAINER), Resource.Factory.create());
            managementResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                    AccessAuthorizationResourceDefinition.createResource(authorizer.getWritableAuthorizerConfiguration()));
            rootResource.registerChild(ServerEnvironmentResourceDescription.RESOURCE_PATH, Resource.Factory.create());
            pathManagerService.addPathManagerResources(rootResource);
        }
    }

    private class HostInitializer implements Initializer {
        final String hostName = "master";
        final HostControllerEnvironment env = createHostControllerEnvironment();
        final LocalHostControllerInfoImpl info = createLocalHostControllerInfo(env);
        final IgnoredDomainResourceRegistry ignoredRegistry = new IgnoredDomainResourceRegistry(info);
        final ExtensionRegistry hostExtensionRegistry = extensionRegistry; //Just use the same for the host as for the domain
        final HostControllerConfigurationPersister persister = new HostControllerConfigurationPersister(env, info, Executors.newCachedThreadPool(), hostExtensionRegistry, extensionRegistry);
        final HostFileRepository hostFileRepository = createHostFileRepository();
        final DomainController domainController = createDomainController(env, info);

        @Override
        public void setRootResourceDefinitionDelegate() {
            rootResourceDefinition.setDelegate(
                    new HostResourceDefinition(
                            hostName,
                            persister,
                            env,
                            (HostRunningModeControl) runningModeControl,
                            hostFileRepository,
                            info,
                            null /*serverInventory*/,
                            null /*remoteFileRepository*/,
                            injectedContentRepository.getValue(),
                            domainController,
                            extensionRegistry,
                            ignoredRegistry,
                            processState,
                            pathManagerService,
                            authorizer,
                            securityIdentitySupplier,
                            AuditLogger.NO_OP_LOGGER,
                            getBootErrorCollector()));
        }

        @Override
        public void initCoreModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource) {
            HostModelUtil.createRootRegistry(
                    rootRegistration,
                    env,
                    ignoredRegistry,
                    new HostModelRegistrar() {
                        @Override
                        public void registerHostModel(String hostName, ManagementResourceRegistration rootRegistration) {
                        }
                    },ProcessType.HOST_CONTROLLER, authorizer, modelControllerResource, info, capabilityRegistry);

            ManagementResourceRegistration hostReg = HostModelUtil.createHostRegistry(
                    hostName,
                    rootRegistration,
                    persister,
                    env,
                    (HostRunningModeControl)runningModeControl,
                    hostFileRepository,
                    info,
                    null /*serverInventory*/,
                    null /*remoteFileRepository*/,
                    injectedContentRepository.getValue(),
                    domainController,
                    extensionRegistry, //Just use the same for the host as for the domain
                    extensionRegistry,
                    ignoredRegistry,
                    processState,
                    pathManagerService,
                    authorizer,
                    securityIdentitySupplier,
                    AuditLogger.NO_OP_LOGGER,
                    getBootErrorCollector());

            //Swap out the write-local-domain-controller operation with one which only does the model part
            hostReg.unregisterOperationHandler(LocalDomainControllerAddHandler.OPERATION_NAME);
            hostReg.registerOperationHandler(LocalDomainControllerAddHandler.DEFINITION, LocalDomainControllerAddHandler.getTestInstance());

            hostReg.unregisterAttribute(HostResourceDefinition.DOMAIN_CONTROLLER.getName());
            hostReg.registerReadWriteAttribute(HostResourceDefinition.DOMAIN_CONTROLLER, null, DomainControllerWriteAttributeHandler.getTestInstance());

            //Register the ops to initialise the schemalocations and namespaces
            hostReg.registerOperationHandler(AddMissingHostNamespacesAttributeForValidationHandler.DEF, AddMissingHostNamespacesAttributeForValidationHandler.INSTANCE);
            hostReg.registerOperationHandler(AddMissingHostSchemaLocationsAttributeForValidationHandler.DEF, AddMissingHostSchemaLocationsAttributeForValidationHandler.INSTANCE);
        }
    }

    private class DomainInitializer implements Initializer {

        @Override
        public void setRootResourceDefinitionDelegate() {
        }

        @Override
        public void initCoreModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource) {
            VersionModelInitializer.registerRootResource(rootResource, null);
            final HostControllerEnvironment env = createHostControllerEnvironment();
            final LocalHostControllerInfoImpl info = createLocalHostControllerInfo(env);
            final IgnoredDomainResourceRegistry ignoredRegistry = new IgnoredDomainResourceRegistry(info);
            final DomainHostExcludeRegistry domainHostExcludeRegistry = new DomainHostExcludeRegistry();
            final ExtensibleConfigurationPersister persister = new NullConfigurationPersister();
            final HostFileRepository hostFileRepository = createHostFileRepository();
            final DomainController domainController = new MockDomainController();
            DomainRootDefinition domainDefinition = null;
            Constructor<?>[] constructors = DomainRootDefinition.class.getConstructors();
            if(constructors.length == 1 && constructors[0].getParameterCount() == 10) { // For EAP 6.2 compatibility
                try {
                    domainDefinition = (DomainRootDefinition) constructors[0].newInstance(domainController, env, persister, injectedContentRepository.getValue(),
                            hostFileRepository, true, info, extensionRegistry, null, pathManagerService);
                } catch (InstantiationException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalArgumentException ex) {
                    throw new RuntimeException(ex);
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException(ex);
                }
            } else {

            domainDefinition = new DomainRootDefinition(domainController, env, persister, injectedContentRepository.getValue(),
                    hostFileRepository, true, info, extensionRegistry, null, pathManagerService, authorizer, securityIdentitySupplier, null,
                    domainHostExcludeRegistry, getMutableRootResourceRegistrationProvider());
            }
            domainDefinition.initialize(rootRegistration);
            rootResourceDefinition.setDelegate(domainDefinition);

            HostModelUtil.createRootRegistry(
                    rootRegistration,
                    env,
                    ignoredRegistry,
                    new HostModelRegistrar() {

                        @Override
                        public void registerHostModel(String hostName, ManagementResourceRegistration root) {
                        }
                    },processType, authorizer, modelControllerResource, info, capabilityRegistry);

            CoreManagementResourceDefinition.registerDomainResource(rootResource, null);

        }

    }

    private static class TestDelegatingResourceDefinition extends DelegatingResourceDefinition {
        private final TestModelType type;

        public TestDelegatingResourceDefinition(TestModelType type) {
            this.type = type;
        }

        @Override
        public void setDelegate(ResourceDefinition delegate) {
            super.setDelegate(delegate);
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            if (type == TestModelType.DOMAIN) {
                return;
            }
            super.registerOperations(resourceRegistration);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            if (type == TestModelType.DOMAIN) {
                return;
            }
            super.registerChildren(resourceRegistration);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            if (type == TestModelType.DOMAIN) {
                return;
            }
            super.registerAttributes(resourceRegistration);
        }

        @Override
        public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            if (type == TestModelType.DOMAIN) {
                return;
            }
            super.registerNotifications(resourceRegistration);
        }

        @Override
        public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
            if (type == TestModelType.DOMAIN) {
                return;
            }
            super.registerCapabilities(resourceRegistration);
        }

        @Override
        public boolean isRuntime() {
            return false;
        }
    }


    private static class MockDomainController implements DomainController {

        @Override
        public RunningMode getCurrentRunningMode() {
            return null;
        }

        @Override
        public LocalHostControllerInfo getLocalHostInfo() {
            return null;
        }

        @Override
        public void registerRemoteHost(String hostName, ManagementChannelHandler handler, Transformers transformers,
                Long remoteConnectionId, boolean registerProxyController) throws SlaveRegistrationException {
        }

        @Override
        public boolean isHostRegistered(String id) {
            return false;
        }

        @Override
        public void unregisterRemoteHost(String id, Long remoteConnectionId, boolean cleanShutdown) {
        }

        @Override
        public void pingRemoteHost(String hostName) {
        }

        @Override
        public void registerRunningServer(ProxyController serverControllerClient) {
        }

        @Override
        public void unregisterRunningServer(String serverName) {
        }

        @Override
        public ModelNode getProfileOperations(String profileName) {
            return null;
        }

        @Override
        public HostFileRepository getLocalFileRepository() {
            return null;
        }

        @Override
        public HostFileRepository getRemoteFileRepository() {
            return null;
        }

        @Override
        public void stopLocalHost() {
        }

        @Override
        public void stopLocalHost(int exitCode) {
        }

        @Override
        public ExtensionRegistry getExtensionRegistry() {
            return null;
        }

        @Override
        public ImmutableCapabilityRegistry getCapabilityRegistry() {
            return null;
        }

        @Override
        public ExpressionResolver getExpressionResolver() {
            return null;
        }

        @Override
        public void initializeMasterDomainRegistry(ManagementResourceRegistration root,
                ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository,
                HostFileRepository fileRepository, ExtensionRegistry extensionRegistry, PathManagerService pathManager) {
        }

        @Override
        public void initializeSlaveDomainRegistry(ManagementResourceRegistration root,
                ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository,
                HostFileRepository fileRepository, LocalHostControllerInfo hostControllerInfo,
                ExtensionRegistry extensionRegistry, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                PathManagerService pathManager) {
        }
    }

    private static class AddMissingHostSchemaLocationsAttributeForValidationHandler implements OperationStepHandler {
        static final OperationStepHandler INSTANCE = new AddMissingHostSchemaLocationsAttributeForValidationHandler();
        static final String NAME = "add-missing-schema-locations-attribute-for-validation-handler";
        static final OperationDefinition DEF = new SimpleOperationDefinitionBuilder(NAME, new NonResolvingResourceDescriptionResolver()).build();
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(SCHEMA_LOCATIONS).setEmptyList();
        }
    }

    private static class AddMissingHostNamespacesAttributeForValidationHandler implements OperationStepHandler {
        static final OperationStepHandler INSTANCE = new AddMissingHostNamespacesAttributeForValidationHandler();
        static final String NAME = "add-missing-namespaces-attribute-for-validation-handler";
        static final OperationDefinition DEF = new SimpleOperationDefinitionBuilder(NAME, new NonResolvingResourceDescriptionResolver()).build();
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(NAMESPACES).setEmptyList();
        }
    }
}
