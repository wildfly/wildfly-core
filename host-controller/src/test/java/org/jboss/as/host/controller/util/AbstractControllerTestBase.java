/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.util;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.SlaveRegistrationException;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.vfs.VirtualFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractControllerTestBase {


    private final boolean useDelegateRootResourceDefinition;
    private ServiceContainer container;
    private ModelController controller;
    private TestModelControllerService controllerService;
    protected final String hostName;
    protected final ProcessType processType;
    protected final LocalHostControllerInfoImpl hostControllerInfo;
    protected final HostControllerEnvironment hostControllerEnvironment;
    protected final DomainController domainController;
    protected volatile DelegatingResourceDefinitionInitializer initializer;
    private final TestDelegatingResourceDefiniton rootResourceDefinition;
    protected final CapabilityRegistry capabilityRegistry;

    protected AbstractControllerTestBase() {
        this(ProcessType.EMBEDDED_SERVER);
    }

    protected AbstractControllerTestBase(ProcessType processType) {
        this("slave", processType, false);
    }

    protected AbstractControllerTestBase(String hostName, ProcessType processType, boolean useDelegateRootResourceDefinition) {
        this.hostName = hostName;
        this.processType = processType;
        this.useDelegateRootResourceDefinition = useDelegateRootResourceDefinition;
        hostControllerEnvironment = createHostControllerEnvironment(hostName);
        hostControllerInfo = new LocalHostControllerInfoImpl(new ControlledProcessState(false), hostControllerEnvironment);
        domainController = new MockDomainController();
        rootResourceDefinition = useDelegateRootResourceDefinition ?  new TestDelegatingResourceDefiniton() : null;
        capabilityRegistry = new CapabilityRegistry(processType.isServer());
    }


    public ModelController getController() {
        return controller;
    }

    public ServiceContainer getContainer() {
        return container;
    }

    public TestModelControllerService getControllerService() {
        return controllerService;
    }

    protected ModelNode createOperation(String operationName, String... address) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(operationName);
        if (address.length > 0) {
            for (String addr : address) {
                operation.get(OP_ADDR).add(addr);
            }
        } else {
            operation.get(OP_ADDR).setEmptyList();
        }

        return operation;
    }

    public ModelNode executeForResult(ModelNode operation) throws OperationFailedException {
        ModelNode rsp = getController().execute(operation, null, null, null);
        if (FAILED.equals(rsp.get(OUTCOME).asString())) {
            ModelNode fd = rsp.get(FAILURE_DESCRIPTION);
            throw new OperationFailedException(fd.toString(), fd);
        }
        return rsp.get(RESULT);
    }

    public void executeForFailure(ModelNode operation) throws OperationFailedException {
        try {
            executeForResult(operation);
            Assert.fail("Should have given error");
        } catch (OperationFailedException expected) {
            // ignore
        }
    }

    @Before
    public void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        if (useDelegateRootResourceDefinition) {
            initializer = createInitializer();
            controllerService = new ModelControllerService(getAuditLogger(), rootResourceDefinition);
        } else {
            controllerService = new ModelControllerService(getAuditLogger());
        }
        ServiceBuilder<ModelController> builder = target.addService(ServiceName.of("ModelController"), controllerService);
        builder.install();
        controllerService.awaitStartup(30, TimeUnit.SECONDS);
        controller = controllerService.getValue();
        //ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        //controller.execute(setup, null, null, null);
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                container = null;
            }
        }
    }

    protected void addBootOperations(List<ModelNode> bootOperations) throws Exception {

    }

    protected abstract void initModel(ManagementModel managementModel);

    protected DelegatingResourceDefinitionInitializer createInitializer() {
        throw new IllegalStateException(this.getClass().getName() + " created with useDelegateRootResourceDefinition=false, needs to override createInitializer()");
    }

    protected TestDelegatingResourceDefiniton getDelegatingResourceDefiniton() {
        if (!useDelegateRootResourceDefinition) {
            throw new IllegalStateException("Test is not set up to use a delegating resource definition");
        }
        return rootResourceDefinition;
    }

    protected ManagedAuditLogger getAuditLogger(){
        return AuditLogger.NO_OP_LOGGER;
    }

    protected ModelNode readResourceRecursive() throws Exception {
        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(RECURSIVE).set(true);
        return executeForResult(op);
    }

    public static class NoopTransformers implements Transformers {

        @Override
        public TransformationTarget getTarget() {
            return null;
        }

        @Override
        public OperationTransformer.TransformedOperation transformOperation(TransformationContext context, ModelNode operation)
                throws OperationFailedException {
            return new OperationTransformer.TransformedOperation(operation, OperationTransformer.TransformedOperation.ORIGINAL_RESULT);
        }

        @Override
        public OperationTransformer.TransformedOperation transformOperation(TransformationInputs transformationInputs, ModelNode operation)
                throws OperationFailedException {
            return new OperationTransformer.TransformedOperation(operation, OperationTransformer.TransformedOperation.ORIGINAL_RESULT);
        }

        @Override
        public Resource transformResource(ResourceTransformationContext context, Resource resource)
                throws OperationFailedException {
            return resource;
        }

        @Override
        public Resource transformRootResource(TransformationInputs transformationInputs, Resource resource)
                throws OperationFailedException {
            return resource;
        }

        @Override
        public Resource transformRootResource(TransformationInputs transformationInputs, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {
            return resource;
        }
    }

    protected class ModelControllerService extends TestModelControllerService {

        public ModelControllerService(final ManagedAuditLogger auditLogger) {
            super(AbstractControllerTestBase.this.processType, new EmptyConfigurationPersister(), new ControlledProcessState(true),
                    ResourceBuilder.Factory.create(PathElement.pathElement("root"), new NonResolvingResourceDescriptionResolver()).build(),
                    auditLogger, initializer, capabilityRegistry);
        }

        public ModelControllerService(final ManagedAuditLogger auditLogger,
                               DelegatingResourceDefinition rootResourceDefinition) {
            super(AbstractControllerTestBase.this.processType, new EmptyConfigurationPersister(), new ControlledProcessState(true),
                    rootResourceDefinition, auditLogger, initializer, capabilityRegistry);
        }

        @Override
        protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure)
                throws ConfigurationPersistenceException {
            try {
                addBootOperations(bootOperations);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.boot(bootOperations, rollbackOnRuntimeFailure);
        }

        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            try {
                AbstractControllerTestBase.this.initModel(managementModel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class EmptyConfigurationPersister extends AbstractConfigurationPersister {

        public EmptyConfigurationPersister() {
            super(null);
        }

        public EmptyConfigurationPersister(XMLElementWriter<ModelMarshallingContext> rootDeparser) {
            super(rootDeparser);
        }

        @Override
        public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) {
            return NullPersistenceResource.INSTANCE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ModelNode> load() {
            return new ArrayList<ModelNode>();
        }

        private static class NullPersistenceResource implements PersistenceResource {

            private static final NullPersistenceResource INSTANCE = new NullPersistenceResource();

            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }
        }
    }


//
//
//    public class TestHostControllerInfo implements LocalHostControllerInfo {
//        public String getLocalHostName() {
//            return hostName;
//        }
//
//        public boolean isMasterDomainController() {
//            return false;
//        }
//
//        public String getNativeManagementInterface() {
//            return null;
//        }
//
//        public int getNativeManagementPort() {
//            return 0;
//        }
//
//        public String getNativeManagementSecurityRealm() {
//            return null;
//        }
//
//        public String getHttpManagementInterface() {
//            return null;
//        }
//
//        public int getHttpManagementPort() {
//            return 0;
//        }
//
//        public String getHttpManagementSecureInterface() {
//            return null;
//        }
//
//        public int getHttpManagementSecurePort() {
//            return 0;
//        }
//
//        public String getHttpManagementSecurityRealm() {
//            return null;
//        }
//
//        public String getRemoteDomainControllerUsername() {
//            return null;
//        }
//
//        public List<DiscoveryOption> getRemoteDomainControllerDiscoveryOptions() {
//            return null;
//        }
//
//        public ControlledProcessState.State getProcessState() {
//            return null;
//        }
//
//        @Override
//        public boolean isRemoteDomainControllerIgnoreUnaffectedConfiguration() {
//            return false;
//        }
//
//        @Override
//        public Collection<String> getAllowedOrigins() {
//            return Collections.EMPTY_LIST;
//        }
//    };


    private static HostControllerEnvironment createHostControllerEnvironment(String hostName) {
        //Copied from core-model-test
        try {
            Map<String, String> props = new HashMap<String, String>();
            File home = new File("target/wildfly");
            delete(home);
            home.mkdir();
            props.put(HostControllerEnvironment.HOME_DIR, home.getAbsolutePath());

            File domain = new File(home, "domain");
            domain.mkdir();
            props.put(HostControllerEnvironment.DOMAIN_BASE_DIR, domain.getAbsolutePath());

            File configuration = new File(domain, "configuration");
            configuration.mkdir();
            props.put(HostControllerEnvironment.DOMAIN_CONFIG_DIR, configuration.getAbsolutePath());

            props.put(HostControllerEnvironment.HOST_NAME, hostName);

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
            RunningMode initialRunningMode = RunningMode.NORMAL;
            boolean backupDomainFiles = false;
            boolean useCachedDc = false;
            ProductConfig productConfig = ProductConfig.fromFilesystemSlot(null, "", props);
            return new HostControllerEnvironment(props, isRestart, modulePath, processControllerAddress, processControllerPort,
                    hostControllerAddress, hostControllerPort, defaultJVM, domainConfig, initialDomainConfig, hostConfig, initialHostConfig,
                    initialRunningMode, backupDomainFiles, useCachedDc, productConfig);
        } catch (UnknownHostException e) {
            // AutoGenerated
            throw new RuntimeException(e);
        }
    }

    public class MockDomainController implements DomainController {

        @Override
        public RunningMode getCurrentRunningMode() {
            return null;
        }

        @Override
        public LocalHostControllerInfo getLocalHostInfo() {
            return hostControllerInfo;
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
            return new ModelNode().setEmptyList();
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
            return capabilityRegistry;
        }

        @Override
        public ExpressionResolver getExpressionResolver() {
            return ExpressionResolver.TEST_RESOLVER;
        }

        @Override
        public void initializeMasterDomainRegistry(ManagementResourceRegistration root, ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository, HostFileRepository fileRepository, ExtensionRegistry extensionRegistry, PathManagerService pathManager) {
        }

        @Override
        public void initializeSlaveDomainRegistry(ManagementResourceRegistration root, ExtensibleConfigurationPersister configurationPersister, ContentRepository contentRepository, HostFileRepository fileRepository, LocalHostControllerInfo hostControllerInfo, ExtensionRegistry extensionRegistry, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry, PathManagerService pathManager) {
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        file.delete();
    }

    public interface DelegatingResourceDefinitionInitializer {
        void setDelegate();
    }


    protected class TestDelegatingResourceDefiniton extends DelegatingResourceDefinition {
        @Override
        public void setDelegate(ResourceDefinition delegate) {
            super.setDelegate(delegate);
        }
    }

    public static class TestRepository implements ContentRepository, HostFileRepository {
        @Override
        public byte[] addContent(InputStream stream) throws IOException {
            return new byte[0];
        }

        @Override
        public void addContentReference(ContentReference reference) {

        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            return null;
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return false;
        }

        @Override
        public boolean syncContent(ContentReference reference) {
            return true;
        }

        @Override
        public void removeContent(ContentReference reference) {

        }

        @Override
        public Map<String, Set<String>> cleanObsoleteContent() {
            return null;
        }

        @Override
        public File getFile(String relativePath) {
            return null;
        }

        @Override
        public File getConfigurationFile(String relativePath) {
            return null;
        }

        @Override
        public File[] getDeploymentFiles(ContentReference reference) {
            return new File[0];
        }

        @Override
        public File getDeploymentRoot(ContentReference reference) {
            return null;
        }

        @Override
        public void deleteDeployment(ContentReference reference) {

        }
    }
}
