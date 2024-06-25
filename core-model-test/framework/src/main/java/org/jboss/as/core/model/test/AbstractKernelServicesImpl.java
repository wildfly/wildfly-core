/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.io.File;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.model.test.ModelTestKernelServicesImpl;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.model.test.ModelTestParser;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.EnhancedQueueExecutor;
import org.wildfly.legacy.test.spi.core.TestModelControllerFactory;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractKernelServicesImpl extends ModelTestKernelServicesImpl<KernelServices> implements KernelServices {

    static final AtomicInteger counter = new AtomicInteger();

    protected AbstractKernelServicesImpl(ServiceContainer container, ModelTestModelControllerService controllerService, StringConfigurationPersister persister,
            ManagementResourceRegistration rootRegistration, OperationValidator operationValidator,
            ModelVersion legacyModelVersion, boolean successfulBoot, Throwable bootError, ExtensionRegistry extensionRegistry) {
        super(container, controllerService, persister, rootRegistration, operationValidator, legacyModelVersion, successfulBoot, bootError);
    }

    public static AbstractKernelServicesImpl create(ProcessType processType, RunningModeControl runningModeControl, ModelTestOperationValidatorFilter validateOpsFilter,
            List<ModelNode> bootOperations, ModelTestParser testParser, ModelVersion legacyModelVersion, TestModelType type, ModelInitializer modelInitializer, ExtensionRegistry extensionRegistry, List<String> contentRepositoryHashes) throws Exception {

        //TODO initialize the path manager service like we do for subsystems?

        //Create the controller
        ServiceContainer container = ServiceContainer.Factory.create("core-test" + counter.incrementAndGet());
        ServiceTarget target = container.subTarget();

        //Initialize the content repository
        File repositoryFile = new File("target/deployment-repository");
        if (contentRepositoryHashes != null) {
            deleteFile(repositoryFile);
            repositoryFile.mkdir();
            for (String hash : contentRepositoryHashes) {
                File file = new File(repositoryFile, hash.substring(0, 2));
                file.mkdir();
                file = new File(file, hash.substring(2, hash.length()));
                file.mkdir();
                file = new File(file, "content");
                file.createNewFile();
            }
        }
        ContentRepository.Factory.addService(target, repositoryFile);

        //Initialize the controller
        StringConfigurationPersister persister = new StringConfigurationPersister(bootOperations, testParser, true);

        //Use the default implementation of test controller for the main controller, and for tests that don't have another one set up on the classpath
        TestModelControllerFactory testModelControllerFactory = StandardTestModelControllerServiceFactory.INSTANCE;
        if (legacyModelVersion != null) {
            ServiceLoader<TestModelControllerFactory> factoryLoader = ServiceLoader.load(TestModelControllerFactory.class, AbstractKernelServicesImpl.class.getClassLoader());
            for (TestModelControllerFactory factory : factoryLoader) {
                testModelControllerFactory = factory;
                break;
            }
        }

        ModelTestModelControllerService svc = testModelControllerFactory.create(processType, runningModeControl, persister, validateOpsFilter, type, modelInitializer, extensionRegistry);

        ServiceBuilder<ModelController> builder = target.addService(Services.JBOSS_SERVER_CONTROLLER, svc);
        builder.addDependency(ContentRepository.SERVICE_NAME, ContentRepository.class, testModelControllerFactory.getContentRepositoryInjector(svc));
        builder.install();
        if (legacyModelVersion == null) {
            ExecutorService mgmtExecutor = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(1)
                .setMaximumPoolSize(256)
                .setKeepAliveTime(20L, TimeUnit.SECONDS)
                .build();
            ServiceName sn = ServiceNameFactory.resolveServiceName(Capabilities.MANAGEMENT_EXECUTOR);
            ServiceBuilder sb = target.addService(sn);
            Consumer<Executor> c = sb.provides(sn);
            sb.setInstance(Service.newInstance(c, mgmtExecutor));
            sb.install();
        }

        //sharedState = svc.state;
        svc.waitForSetup();
        //processState.setRunning();

        AbstractKernelServicesImpl kernelServices = legacyModelVersion == null ?
                new MainKernelServicesImpl(container, svc, persister, svc.getRootRegistration(),
                        new OperationValidator(svc.getRootRegistration()), legacyModelVersion, svc.isSuccessfulBoot(), svc.getBootError(), extensionRegistry) :
                            new LegacyKernelServicesImpl(container, svc, persister, svc.getRootRegistration(),
                                    new OperationValidator(svc.getRootRegistration()), legacyModelVersion, svc.isSuccessfulBoot(), svc.getBootError(), extensionRegistry, ContentRepository.Factory.create(repositoryFile));

        return kernelServices;
    }

    private static void deleteFile(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteFile(child);
            }
        }
        file.delete();
    }


    public abstract TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation) throws OperationFailedException;

    public abstract ModelNode readTransformedModel(ModelVersion modelVersion);

    public abstract ModelNode callReadMasterDomainModelHandler(ModelVersion modelVersion);

    public abstract ModelNode executeOperation(final ModelVersion modelVersion, final TransformedOperation op);

    protected void addLegacyKernelService(ModelVersion modelVersion, KernelServices legacyServices) {
        super.addLegacyKernelService(modelVersion, legacyServices);
    }

    private static class StandardTestModelControllerServiceFactory implements TestModelControllerFactory {
        static final TestModelControllerFactory INSTANCE = new StandardTestModelControllerServiceFactory();
        @Override
        public ModelTestModelControllerService create(ProcessType processType, RunningModeControl runningModeControl,
                StringConfigurationPersister persister, ModelTestOperationValidatorFilter validateOpsFilter, TestModelType type, ModelInitializer modelInitializer, ExtensionRegistry extensionRegistry) {
            CapabilityRegistry cr = new CapabilityRegistry(processType.isServer());
            return TestModelControllerService.create(processType, runningModeControl, persister, validateOpsFilter, type, modelInitializer, extensionRegistry, cr);
        }

        @Override
        public InjectedValue<ContentRepository> getContentRepositoryInjector(ModelTestModelControllerService service) {
            return ((TestModelControllerService)service).getContentRepositoryInjector();
        }
    }

}
