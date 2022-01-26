/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.domain.http.server.ConsoleAvailability.CONSOLE_AVAILABILITY_CAPABILITY;

import java.io.File;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.BootContext;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.platform.mbean.PlatformMBeanConstants;
import org.jboss.as.platform.mbean.RootPlatformMBeanResource;
import org.jboss.as.remoting.HttpListenerRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.controller.resources.VersionModelInitializer;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeferredDeploymentOverlayDeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentCompleteServiceProcessor;
import org.jboss.as.server.deployment.DeploymentMountProvider;
import org.jboss.as.server.deployment.DeploymentOverlayDeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.ServiceLoaderProcessor;
import org.jboss.as.server.deployment.SubDeploymentProcessor;
import org.jboss.as.server.deployment.annotation.AnnotationIndexProcessor;
import org.jboss.as.server.deployment.annotation.CleanupAnnotationIndexProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndexProcessor;
import org.jboss.as.server.deployment.dependencies.DeploymentDependenciesProcessor;
import org.jboss.as.server.deployment.integration.Seam2Processor;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXMLParsingProcessor;
import org.jboss.as.server.deployment.module.ClassFileTransformerProcessor;
import org.jboss.as.server.deployment.module.DeploymentRootMountProcessor;
import org.jboss.as.server.deployment.module.DeploymentVisibilityProcessor;
import org.jboss.as.server.deployment.module.DriverDependenciesProcessor;
import org.jboss.as.server.deployment.module.ManifestAttachmentProcessor;
import org.jboss.as.server.deployment.module.ManifestClassPathProcessor;
import org.jboss.as.server.deployment.module.ManifestDependencyProcessor;
import org.jboss.as.server.deployment.module.ManifestExtensionListProcessor;
import org.jboss.as.server.deployment.module.ManifestExtensionNameProcessor;
import org.jboss.as.server.deployment.module.ModuleClassPathProcessor;
import org.jboss.as.server.deployment.module.ModuleDependencyProcessor;
import org.jboss.as.server.deployment.module.ModuleExtensionListProcessor;
import org.jboss.as.server.deployment.module.ModuleExtensionNameProcessor;
import org.jboss.as.server.deployment.module.ModuleIdentifierProcessor;
import org.jboss.as.server.deployment.module.ModuleSpecProcessor;
import org.jboss.as.server.deployment.module.ServerDependenciesProcessor;
import org.jboss.as.server.deployment.module.SubDeploymentDependencyProcessor;
import org.jboss.as.server.deployment.module.descriptor.DeploymentStructureDescriptorParser;
import org.jboss.as.server.deployment.reflect.CleanupReflectionIndexProcessor;
import org.jboss.as.server.deployment.reflect.InstallReflectionIndexProcessor;
import org.jboss.as.server.deployment.service.ServiceActivatorDependencyProcessor;
import org.jboss.as.server.deployment.service.ServiceActivatorProcessor;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService;
import org.jboss.as.server.moduleservice.ExtensionIndexService;
import org.jboss.as.server.moduleservice.ExternalModule;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Service for the {@link org.jboss.as.controller.ModelController} for an AS server instance.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ServerService extends AbstractControllerService {

    /** Service is not for general use, so the service name is not declared in the more visible {@code Services} */
    public static final ServiceName JBOSS_SERVER_CLIENT_FACTORY = CLIENT_FACTORY_CAPABILITY.getCapabilityServiceName();
    /** Service is not for general use, so the service name is not declared in the more visible {@code Services} */
    public static final ServiceName JBOSS_SERVER_NOTIFICATION_REGISTRY = NOTIFICATION_REGISTRY_CAPABILITY.getCapabilityServiceName();
    /** Service is not for general use, so the service name is not declared in the more visible {@code Services} */
    public static final ServiceName JBOSS_SERVER_SCHEDULED_EXECUTOR = EXECUTOR_CAPABILITY.getCapabilityServiceName().append("scheduled");
    static final ServiceName MANAGEMENT_EXECUTOR = EXECUTOR_CAPABILITY.getCapabilityServiceName();

    private final InjectedValue<DeploymentMountProvider> injectedDeploymentRepository = new InjectedValue<DeploymentMountProvider>();
    private final InjectedValue<ContentRepository> injectedContentRepository = new InjectedValue<ContentRepository>();
    private final InjectedValue<ServiceModuleLoader> injectedModuleLoader = new InjectedValue<ServiceModuleLoader>();

    private final InjectedValue<ExternalModule> injectedExternalModule = new InjectedValue<>();
    private final InjectedValue<PathManager> injectedPathManagerService = new InjectedValue<PathManager>();

    private final Bootstrap.Configuration configuration;
    private final BootstrapListener bootstrapListener;
    private final ControlledProcessState processState;
    private final RunningModeControl runningModeControl;
    private volatile ExtensibleConfigurationPersister extensibleConfigurationPersister;
    private final ServerDelegatingResourceDefinition rootResourceDefinition;
    private final SuspendController suspendController;
    private final RuntimeExpressionResolver expressionResolver;
    public static final String SERVER_NAME = "server";

    static final String SUSPEND_CONTROLLER_CAPABILITY_NAME = "org.wildfly.server.suspend-controller";
    static final String EXTERNAL_MODULE_CAPABILITY_NAME = "org.wildfly.management.external-module";

    static final RuntimeCapability<Void> SUSPEND_CONTROLLER_CAPABILITY =
            RuntimeCapability.Builder.of(SUSPEND_CONTROLLER_CAPABILITY_NAME, SuspendController.class)
                    .build();

    static final RuntimeCapability<Void> EXTERNAL_MODULE_CAPABILITY =
            RuntimeCapability.Builder.of(EXTERNAL_MODULE_CAPABILITY_NAME, ExternalModule.class)
                    .build();

    /**
     * Construct a new instance.
     * @param configuration the bootstrap configuration
     * @param prepareStep the prepare step to use
     */
    private ServerService(final Supplier<ExecutorService> executorService,
                          final Supplier<ControllerInstabilityListener> instabilityListener,
                          final Bootstrap.Configuration configuration, final ControlledProcessState processState,
                          final OperationStepHandler prepareStep, final BootstrapListener bootstrapListener, final ServerDelegatingResourceDefinition rootResourceDefinition,
                          final RunningModeControl runningModeControl, final ManagedAuditLogger auditLogger,
                          final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                          final CapabilityRegistry capabilityRegistry, final SuspendController suspendController, final RuntimeExpressionResolver expressionResolver) {
        super(executorService, instabilityListener, getProcessType(configuration.getServerEnvironment()), runningModeControl, null, processState,
                rootResourceDefinition, prepareStep, expressionResolver, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry,
                configuration.getServerEnvironment().getConfigurationExtension());
        this.configuration = configuration;
        this.bootstrapListener = bootstrapListener;
        this.processState = processState;
        this.runningModeControl = runningModeControl;
        this.rootResourceDefinition = rootResourceDefinition;
        this.suspendController = suspendController;
        this.expressionResolver = expressionResolver;
    }

    static ProcessType getProcessType(ServerEnvironment serverEnvironment) {
        return serverEnvironment != null
            ? serverEnvironment.getLaunchType().getProcessType()
            : ProcessType.EMBEDDED_SERVER;
    }

    /**
     * Add this service to the given service target.
     *  @param serviceTarget the service target
     * @param configuration the bootstrap configuration
     */
    public static void addService(final ServiceTarget serviceTarget, final Bootstrap.Configuration configuration,
                                  final ControlledProcessState processState, final BootstrapListener bootstrapListener,
                                  final RunningModeControl runningModeControl, final ManagedAuditLogger auditLogger,
                                  final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                  final SuspendController suspendController) {

        // Install Executor services
        final ThreadGroup threadGroup = new ThreadGroup("ServerService ThreadGroup");
        final String namePattern = "ServerService Thread Pool -- %t";
        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<ThreadFactory>() {
            public ThreadFactory run() {
                return new JBossThreadFactory(threadGroup, Boolean.FALSE, null, namePattern, null, null);
            }
        });

        // TODO determine why QueuelessThreadPoolService makes boot take > 35 secs
//        final QueuelessThreadPoolService serverExecutorService = new QueuelessThreadPoolService(Integer.MAX_VALUE, false, new TimeSpec(TimeUnit.SECONDS, 5));
//        serverExecutorService.getThreadFactoryInjector().inject(threadFactory);
        final boolean forDomain = ProcessType.DOMAIN_SERVER == getProcessType(configuration.getServerEnvironment());
        final ServerExecutorService serverExecutorService = new ServerExecutorService(threadFactory, forDomain);
        serviceTarget.addService(MANAGEMENT_EXECUTOR, serverExecutorService)
                .addAliases(Services.JBOSS_SERVER_EXECUTOR, ManagementRemotingServices.SHUTDOWN_EXECUTOR_NAME) // Use this executor for mgmt shutdown for now
                .install();
        final ServerScheduledExecutorService serverScheduledExecutorService = new ServerScheduledExecutorService(threadFactory);
        serviceTarget.addService(JBOSS_SERVER_SCHEDULED_EXECUTOR, serverScheduledExecutorService)
                .addAliases(JBOSS_SERVER_SCHEDULED_EXECUTOR)
                .addDependency(MANAGEMENT_EXECUTOR, ExecutorService.class, serverScheduledExecutorService.executorInjector)
                .install();

        final CapabilityRegistry capabilityRegistry = configuration.getCapabilityRegistry();
        final RuntimeExpressionResolver expressionResolver = new RuntimeExpressionResolver();
        configuration.getExtensionRegistry().setResolverExtensionRegistry(expressionResolver);

        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(Services.JBOSS_SERVER_CONTROLLER);
        final boolean allowMCE = configuration.getServerEnvironment().isAllowModelControllerExecutor();
        final Supplier<ExecutorService> esSupplier = allowMCE ? serviceBuilder.requires(MANAGEMENT_EXECUTOR) : null;
        final boolean isDomainEnv = configuration.getServerEnvironment().getLaunchType() == ServerEnvironment.LaunchType.DOMAIN;
        final Supplier<ControllerInstabilityListener> cilSupplier = isDomainEnv ? serviceBuilder.requires(HostControllerConnectionService.SERVICE_NAME) : null;
        ServerService service = new ServerService(esSupplier, cilSupplier, configuration, processState, null, bootstrapListener, new ServerDelegatingResourceDefinition(),
                runningModeControl, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, suspendController, expressionResolver);
        serviceBuilder.setInstance(service);
        serviceBuilder.addDependency(DeploymentMountProvider.SERVICE_NAME,DeploymentMountProvider.class, service.injectedDeploymentRepository);
        serviceBuilder.addDependency(ContentRepository.SERVICE_NAME, ContentRepository.class, service.injectedContentRepository);
        serviceBuilder.addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ServiceModuleLoader.class, service.injectedModuleLoader);
        serviceBuilder.addDependency(EXTERNAL_MODULE_CAPABILITY.getCapabilityServiceName(), ExternalModule.class,
                service.injectedExternalModule);
        serviceBuilder.addDependency(PATH_MANAGER_CAPABILITY.getCapabilityServiceName(), PathManager.class, service.injectedPathManagerService);
        serviceBuilder.requires(CONSOLE_AVAILABILITY_CAPABILITY.getCapabilityServiceName());

        serviceBuilder.install();

        ExternalManagementRequestExecutor.install(serviceTarget, threadGroup, EXECUTOR_CAPABILITY.getCapabilityServiceName(), service.getStabilityMonitor());

    }

    public synchronized void start(final StartContext context) throws StartException {
        ServerEnvironment serverEnvironment = configuration.getServerEnvironment();
        Bootstrap.ConfigurationPersisterFactory configurationPersisterFactory = configuration.getConfigurationPersisterFactory();
        extensibleConfigurationPersister = configurationPersisterFactory.createConfigurationPersister(serverEnvironment, getExecutorService());
        setConfigurationPersister(extensibleConfigurationPersister);
        rootResourceDefinition.setDelegate(
                new ServerRootResourceDefinition(injectedContentRepository.getValue(),
                        extensibleConfigurationPersister, configuration.getServerEnvironment(), processState,
                        runningModeControl, configuration.getExtensionRegistry(),
                        getExecutorService() != null,
                        (PathManagerService)injectedPathManagerService.getValue(),
                        new DomainServerCommunicationServices.OperationIDUpdater() {
                            @Override
                            public void updateOperationID(final int operationID) {
                                DomainServerCommunicationServices.updateOperationID(operationID);
                            }
                        },
                        authorizer,
                        securityIdentitySupplier,
                        super.getAuditLogger(),
                        getMutableRootResourceRegistrationProvider(),
                        super.getBootErrorCollector(),
                        configuration.getCapabilityRegistry()));
        super.start(context);
    }

    protected void boot(final BootContext context) throws ConfigurationPersistenceException {
        boolean ok;
        try {
            final ServerEnvironment serverEnvironment = configuration.getServerEnvironment();
            final ServiceTarget serviceTarget = context.getServiceTarget();
            final File[] extDirs = serverEnvironment.getJavaExtDirs();
            final File[] newExtDirs = Arrays.copyOf(extDirs, extDirs.length + 1);
            newExtDirs[extDirs.length] = new File(serverEnvironment.getServerBaseDir(), "lib/ext");
            serviceTarget.addService(org.jboss.as.server.deployment.Services.JBOSS_DEPLOYMENT_EXTENSION_INDEX,
                    new ExtensionIndexService(newExtDirs)).setInitialMode(ServiceController.Mode.ON_DEMAND).install();
            final boolean suspend = runningModeControl.getSuspend()!= null ? runningModeControl.getSuspend() : serverEnvironment.isStartSuspended();
            final boolean gracefulStartup = serverEnvironment.isStartGracefully();
            suspendController.setStartSuspended(suspend);
            runningModeControl.setSuspend(false);
            if (!gracefulStartup) {
                if (suspend) {
                    ServerLogger.ROOT_LOGGER.disregardingNonGraceful();
                } else {
                    suspendController.nonGracefulStart();
                }
            }
            context.getServiceTarget().addService(SUSPEND_CONTROLLER_CAPABILITY.getCapabilityServiceName(), suspendController)
                    .addAliases(SuspendController.SERVICE_NAME)
                    .addDependency(JBOSS_SERVER_NOTIFICATION_REGISTRY, NotificationHandlerRegistry.class, suspendController.getNotificationHandlerRegistry())
                    .install();

            GracefulShutdownService gracefulShutdownService = new GracefulShutdownService();
            context.getServiceTarget().addService(GracefulShutdownService.SERVICE_NAME, gracefulShutdownService)
                    .addDependency(SUSPEND_CONTROLLER_CAPABILITY.getCapabilityServiceName(), SuspendController.class, gracefulShutdownService.getSuspendControllerInjectedValue())
                    .install();

            // Activate module loader
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_SERVICE_MODULE_LOADER, new DeploymentUnitProcessor() {
                @Override
                public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
                    phaseContext.getDeploymentUnit().putAttachment(Attachments.SERVICE_MODULE_LOADER, injectedModuleLoader.getValue());
                    phaseContext.getDeploymentUnit().putAttachment(Attachments.EXTERNAL_MODULE_SERVICE, injectedExternalModule.getValue());
                    phaseContext.getDeploymentUnit().putAttachment(Attachments.EXTERNAL_SERVICE_TARGET, serviceTarget);
                }

                @Override
                public void undeploy(DeploymentUnit context) {
                    context.removeAttachment(Attachments.SERVICE_MODULE_LOADER);
                }
            });
            HttpListenerRegistryService.install(serviceTarget);


            // Activate core processors for jar deployment
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT, new DeploymentRootMountProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MANIFEST, new ManifestAttachmentProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_ADDITIONAL_MANIFEST, new ManifestAttachmentProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_DEPLOYMENT_OVERLAY, new DeploymentOverlayDeploymentUnitProcessor(injectedContentRepository.getValue()));
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_DEFERRED_DEPLOYMENT_OVERLAY, new DeferredDeploymentOverlayDeploymentUnitProcessor(injectedContentRepository.getValue()));
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_SUB_DEPLOYMENT, new SubDeploymentProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MODULE_IDENTIFIERS, new ModuleIdentifierProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_ANNOTATION_INDEX, new AnnotationIndexProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_PARSE_JBOSS_ALL_XML, new JBossAllXMLParsingProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_JBOSS_DEPLOYMENT_STRUCTURE, new DeploymentStructureDescriptorParser());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_CLASS_PATH, new ManifestClassPathProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_DEPLOYMENT_DEPENDENCIES, new DeploymentDependenciesProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_DEPENDENCIES_MANIFEST, new ManifestDependencyProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.PARSE, Phase.PARSE_COMPOSITE_ANNOTATION_INDEX, new CompositeIndexProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.PARSE, Phase.PARSE_EXTENSION_LIST, new ManifestExtensionListProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.PARSE, Phase.PARSE_EXTENSION_NAME, new ManifestExtensionNameProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.PARSE, Phase.PARSE_SERVICE_LOADER_DEPLOYMENT, new ServiceLoaderProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_MODULE, new ModuleDependencyProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_SAR_MODULE, new ServiceActivatorDependencyProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_CLASS_PATH, new ModuleClassPathProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_EXTENSION_LIST, new ModuleExtensionListProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_SUB_DEPLOYMENTS, new SubDeploymentDependencyProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JDK, new ServerDependenciesProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_VISIBLE_MODULES, new DeploymentVisibilityProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_DRIVERS, new DriverDependenciesProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_MODULE_SPEC, new ModuleSpecProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.POST_MODULE, Phase.POST_MODULE_INSTALL_EXTENSION, new ModuleExtensionNameProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.POST_MODULE, Phase.POST_MODULE_REFLECTION_INDEX, new InstallReflectionIndexProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.FIRST_MODULE_USE, Phase.FIRST_MODULE_USE_TRANSFORMER, new ClassFileTransformerProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.INSTALL, Phase.INSTALL_SERVICE_ACTIVATOR, new ServiceActivatorProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.INSTALL, Phase.INSTALL_DEPLOYMENT_COMPLETE_SERVICE, new DeploymentCompleteServiceProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.CLEANUP, Phase.CLEANUP_REFLECTION_INDEX, new CleanupReflectionIndexProcessor());
            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.CLEANUP, Phase.CLEANUP_ANNOTATION_INDEX, new CleanupAnnotationIndexProcessor());

            // Ext integration deployers

            DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_SEAM, new Seam2Processor(serviceTarget));

            //jboss.xml parsers
            DeploymentStructureDescriptorParser.registerJBossXMLParsers();
            DeploymentDependenciesProcessor.registerJBossXMLParsers();

            try {
                // Boot but by default don't rollback on runtime failures
                // TODO replace system property used by tests with something properly configurable for general use
                // TODO search for uses of "jboss.unsupported.fail-boot-on-runtime-failure" in tests before changing this!!
                boolean failOnRuntime = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged("jboss.unsupported.fail-boot-on-runtime-failure", "false"));

                // Load the ops
                List<ModelNode> bootOps = extensibleConfigurationPersister.load();
                //Add the controller initialization operation to the boot ops
                ModelNode controllerInitOp = registerModelControllerServiceInitializationBootStep(context);
                if (controllerInitOp != null) {
                    //In a domain server this is an unmodifiable list so copy it
                    bootOps = new ArrayList<>(bootOps);
                    bootOps.add(controllerInitOp);
                }

                ok = boot(bootOps, failOnRuntime);

                if (ok) {
                    finishBoot(suspend);
                }
            } finally {
                DeployerChainAddHandler.INSTANCE.clearDeployerMap();
            }
        } catch (Exception e) {
            ServerLogger.ROOT_LOGGER.caughtExceptionDuringBoot(e);
            ok = false;
        }

        if (ok) {
            // Trigger the started message
            Notification notification = new Notification(ModelDescriptionConstants.BOOT_COMPLETE_NOTIFICATION, PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MANAGEMENT),
                    PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS)), ServerLogger.AS_ROOT_LOGGER.bootComplete());
            getNotificationSupport().emit(notification);
            String message = "";
            if (configuration.getServerEnvironment().getServerConfigurationFile() != null) {
                String serverConfig = configuration.getServerEnvironment().getServerConfigurationFile().getMainFile().getName();
                message = ServerLogger.AS_ROOT_LOGGER.serverConfigFileInUse(serverConfig);
            }
            bootstrapListener.printBootStatistics(message);
        } else {
            // Die!
            String messageToAppend = "";
            if (configuration.getServerEnvironment().getServerConfigurationFile() != null) {
                messageToAppend = ServerLogger.ROOT_LOGGER.serverConfigFileInUse(configuration.getServerEnvironment().getServerConfigurationFile().getMainFile().getName());
            }
            String message = ServerLogger.ROOT_LOGGER.unsuccessfulBoot(messageToAppend);
            bootstrapListener.bootFailure(message);
            SystemExiter.logAndExit(new SystemExiter.ExitLogger() {
                @Override
                public void logExit() {
                    ServerLogger.ROOT_LOGGER.fatal(message);
                }
            }, 1);
        }
    }

    protected void finishBoot(boolean suspend) throws ConfigurationPersistenceException {
        super.finishBoot();
        if (!suspend) {
            suspendController.resume();
        }
    }

    @Override
    protected void postBoot() {
        executeAdditionalCliBootScript();
    }

    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) throws ConfigurationPersistenceException {
        final List<ModelNode> operations = new ArrayList<ModelNode>(bootOperations);
        operations.add(DeployerChainAddHandler.OPERATION);
        return super.boot(operations, rollbackOnRuntimeFailure);
    }

    public void stop(final StopContext context) {
        configuration.getExtensionRegistry().clear();
        configuration.getServerEnvironment().resetProvidedProperties();
        super.stop(context);
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        Resource rootResource = managementModel.getRootResource();
        // TODO maybe make creating of empty nodes part of the MNR description
        Resource managementResource = Resource.Factory.create(); // TODO - Can we get a Resource direct from CoreManagementResourceDefinition?
        managementResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.SERVICE, ModelDescriptionConstants.MANAGEMENT_OPERATIONS), modelControllerResource);
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MANAGEMENT), managementResource);
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.SERVICE_CONTAINER), Resource.Factory.create());
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MODULE_LOADING), PlaceholderResource.INSTANCE);
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.CAPABILITY_REGISTRY), Resource.Factory.create());
        managementResource.registerChild(AccessAuthorizationResourceDefinition.PATH_ELEMENT, AccessAuthorizationResourceDefinition.createResource(authorizer.getWritableAuthorizerConfiguration()));
        rootResource.registerChild(ServerEnvironmentResourceDescription.RESOURCE_PATH, Resource.Factory.create());
        ((PathManagerService)injectedPathManagerService.getValue()).addPathManagerResources(rootResource);

        VersionModelInitializer.registerRootResource(rootResource, configuration.getServerEnvironment() != null ? configuration.getServerEnvironment().getProductConfig() : null);

        // Platform MBeans
        rootResource.registerChild(PlatformMBeanConstants.ROOT_PATH, new RootPlatformMBeanResource());

        final RuntimeCapabilityRegistry capabilityRegistry = managementModel.getCapabilityRegistry();
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(PATH_MANAGER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(EXECUTOR_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(SUSPEND_CONTROLLER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(PROCESS_STATE_NOTIFIER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(EXTERNAL_MODULE_CAPABILITY, CapabilityScope.GLOBAL,new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(CONSOLE_AVAILABILITY_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        // Record the core capabilities with the root MRR so reads of it will show it as their provider
        // This also gets them recorded as 'possible capabilities' in the capability registry
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        rootRegistration.registerCapability(PATH_MANAGER_CAPABILITY);
        rootRegistration.registerCapability(EXECUTOR_CAPABILITY);
        rootRegistration.registerCapability(SUSPEND_CONTROLLER_CAPABILITY);
        rootRegistration.registerCapability(PROCESS_STATE_NOTIFIER_CAPABILITY);
        rootRegistration.registerCapability(EXTERNAL_MODULE_CAPABILITY);
        rootRegistration.registerCapability(CONSOLE_AVAILABILITY_CAPABILITY);
    }

    @Override
    protected ModelControllerServiceInitializationParams getModelControllerServiceInitializationParams() {
        final ServiceLoader<ModelControllerServiceInitialization> sl = ServiceLoader.load(ModelControllerServiceInitialization.class);
        return new ModelControllerServiceInitializationParams(sl) {
            @Override
            public String getHostName() {
                return null;
            }
        };
    }

    /** Temporary replacement for QueuelessThreadPoolService */
    private static class ServerExecutorService implements Service<ExecutorService> {

        private static final int DEFAULT_CORE_POOL_SIZE = 1;
        private static final int DEFAULT_MAX_POOL_SIZE = 1024;
        private static final int DEFAULT_DOMAIN_CORE_POOL_SIZE = 3; // keep more threads in a domain server as the intra-process comms use more tasks
        private static final String CORE_POOL_SIZE_SYS_PROP = "org.jboss.as.server-service.core.threads";
        private static final String MAX_POOL_SIZE_SYS_PROP = "org.jboss.as.server-service.max.threads";
        private static final String ENHANCED_EXECUTOR_MBEAN_NAME = "ServerService";

        private final ThreadFactory threadFactory;
        private final boolean forDomain;
        private ExecutorService executorService;

        private ServerExecutorService(ThreadFactory threadFactory, boolean forDomain) {
            this.threadFactory = threadFactory;
            this.forDomain = forDomain;
        }

        @Override
        public synchronized void start(StartContext context) throws StartException {
            if (EnhancedQueueExecutor.DISABLE_HINT) {
                executorService = new ThreadPoolExecutor(getCorePoolSize(forDomain), Integer.MAX_VALUE, 20L, TimeUnit.SECONDS,
                        new SynchronousQueue<Runnable>(), threadFactory);
            } else {
                executorService = new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(getCorePoolSize(forDomain))
                    .setMaximumPoolSize(getMaxPoolSize())
                    .setKeepAliveTime(20L, TimeUnit.SECONDS)
                    .setThreadFactory(threadFactory)
                    .setMBeanName(ENHANCED_EXECUTOR_MBEAN_NAME)
                    .build();
            }
        }

        @Override
        public synchronized void stop(final StopContext context) {

            if (executorService != null) {
                context.asynchronous();
                Thread executorShutdown = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean interrupted = false;
                        try {
                            executorService.shutdown();
                            // Hack. Give in progress tasks a brief period to complete before we
                            // interrupt threads via shutdownNow
                            executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        } finally {
                            try {
                                List<Runnable> tasks = executorService.shutdownNow();
                                executorService = null;
                                if (!interrupted) {
                                    for (Runnable task : tasks) {
                                        ServerLogger.AS_ROOT_LOGGER.debugf("%s -- Discarding unexecuted task %s", getClass().getSimpleName(), task);
                                    }
                                }
                            } finally {
                                context.complete();
                            }
                        }
                    }
                }, "ServerExecutorService Shutdown Thread");
                executorShutdown.start();
            }
        }

        @Override
        public synchronized ExecutorService getValue() throws IllegalStateException, IllegalArgumentException {
            return executorService;
        }

        private static int getCorePoolSize(boolean forDomain) {
            String val = WildFlySecurityManager.getPropertyPrivileged(CORE_POOL_SIZE_SYS_PROP, null);
            if (val != null) {
                try {
                    int result = Integer.parseInt(val);
                    if (result >= 0) {
                        return result;
                    } else {
                        ServerLogger.ROOT_LOGGER.invalidPoolSize(val, CORE_POOL_SIZE_SYS_PROP);
                    }
                } catch (NumberFormatException nfe) {
                    ServerLogger.ROOT_LOGGER.invalidPoolSize(val, CORE_POOL_SIZE_SYS_PROP);
                }

            }
            return forDomain ? DEFAULT_DOMAIN_CORE_POOL_SIZE : DEFAULT_CORE_POOL_SIZE;
        }

        private static int getMaxPoolSize() {
            String val = WildFlySecurityManager.getPropertyPrivileged(MAX_POOL_SIZE_SYS_PROP, null);
            if (val != null) {
                try {
                    int result = Integer.parseInt(val);
                    if (result >= 0) {
                        return result;
                    } else {
                        ServerLogger.ROOT_LOGGER.invalidPoolSize(val, MAX_POOL_SIZE_SYS_PROP);
                    }
                } catch (NumberFormatException nfe) {
                    ServerLogger.ROOT_LOGGER.invalidPoolSize(val, MAX_POOL_SIZE_SYS_PROP);
                }

            }
            return DEFAULT_MAX_POOL_SIZE;
        }
    }

    static final class ServerDelegatingResourceDefinition extends DelegatingResourceDefinition{
        @Override
        public void setDelegate(ResourceDefinition delegate) {
            super.setDelegate(delegate);
        }
    }

    static final class ServerScheduledExecutorService implements Service<ScheduledExecutorService> {
        private final ThreadFactory threadFactory;
        private ScheduledThreadPoolExecutor scheduledExecutorService;
        private final InjectedValue<ExecutorService> executorInjector = new InjectedValue<>();

        private ServerScheduledExecutorService(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        @Override
        public synchronized void start(final StartContext context) throws StartException {
            scheduledExecutorService = new ScheduledThreadPoolExecutor(4 , threadFactory);
            scheduledExecutorService.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public synchronized void stop(final StopContext context) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        scheduledExecutorService.shutdown();
                    } finally {
                        scheduledExecutorService = null;
                        context.complete();
                    }
                }
            };
            try {
                executorInjector.getValue().execute(r);
            } catch (RejectedExecutionException e) {
                r.run();
            } finally {
                context.asynchronous();
            }
        }

        @Override
        public synchronized ScheduledExecutorService getValue() throws IllegalStateException {
            return scheduledExecutorService;
        }
    }
}
