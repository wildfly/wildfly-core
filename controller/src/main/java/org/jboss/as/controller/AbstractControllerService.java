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

package org.jboss.as.controller;

import static org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY;
import static org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY;
import static org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.controller.notification.NotificationSupport;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.filter.PathFilter;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A base class for controller services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractControllerService implements Service<ModelController> {

    /**
     * Name of the system property to set to control the stack size for the thread used to process boot operations.
     * The boot sequence can have a very deep stack, so if needed setting this property can be used to create a larger
     * memory area for storing data on the stack.
     *
     * @see #DEFAULT_BOOT_STACK_SIZE
     */
    public static final String BOOT_STACK_SIZE_PROPERTY = "jboss.boot.thread.stack.size";

    /**
     * The default stack size for the thread used to process boot operations.
     *
     * @see #BOOT_STACK_SIZE_PROPERTY
     */
    public static final int DEFAULT_BOOT_STACK_SIZE = 2 * 1024 * 1024;

    private static int getBootStackSize() {
        String prop = WildFlySecurityManager.getPropertyPrivileged(BOOT_STACK_SIZE_PROPERTY, null);
        if (prop == null) {
            return  DEFAULT_BOOT_STACK_SIZE;
        } else {
            int base = 1;
            String multiple = prop;
            int lastIdx = prop.length() - 1;
            if (lastIdx > 0) {
                char last = prop.charAt(lastIdx);
                if ('k' == last || 'K' == last) {
                    multiple = prop.substring(0, lastIdx);
                    base = 1024;
                } else if ('m' == last || 'M' == last) {
                    multiple = prop.substring(0, lastIdx);
                    base = 1024 * 1024;
                }
            }
            try {
                return Integer.parseInt(multiple) * base;
            } catch (NumberFormatException e) {
                ROOT_LOGGER.invalidSystemPropertyValue(prop, BOOT_STACK_SIZE_PROPERTY, DEFAULT_BOOT_STACK_SIZE);
                return DEFAULT_BOOT_STACK_SIZE;
            }
        }
    }

    /** Capability in-vm users of the controller use to create clients */
    protected static final RuntimeCapability<Void> CLIENT_FACTORY_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.model-controller-client-factory", ModelControllerClientFactory.class)
            .build();

    /** Capability in-vm users of the controller use to register notification handlers */
    protected static final RuntimeCapability<Void> NOTIFICATION_REGISTRY_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.notification-handler-registry", NotificationHandlerRegistry.class)
                    .build();

    /**
     * Capability users of the controller use to coordinate changes to paths.
     * This capability isn't necessarily directly related to this class but we declare it
     * here as it's as good a place as any at this time.
     */
    public static final RuntimeCapability<Void> PATH_MANAGER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.path-manager", PathManager.class)
                    .build();

    /**
     * Capability users of the controller use to perform asynchronous management tasks.
     * This capability isn't necessarily directly related to this class but we declare it
     * here as it's as good a place as any at this time.
     */
    public static final RuntimeCapability<Void> EXECUTOR_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.executor", ExecutorService.class)
                    .build();

    /**
     * Capability users of the controller use to read process state and get notification of state changes.
     * This capability isn't necessarily directly related to this class but we declare it
     * here as it's as good a place as any at this time.
     */
    protected static final RuntimeCapability<Void> PROCESS_STATE_NOTIFIER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.process-state-notifier", ProcessStateNotifier.class)
                    .build();

    /**
     * Name of a capability that extensions that provide {@link ExpressionResolverExtension} implementations
     * can use to register their extensions with the core {@link ExpressionResolver}.
     *
     * @deprecated Will be removed in an upcoming major version.
     */
    @Deprecated
    protected static final String EXPRESSION_RESOLVER_EXTENSION_REGISTRY_CAPABILITY_NAME =
            "org.wildfly.management.expression-resolver-extension-registry";

    private static final OperationDefinition INIT_CONTROLLER_OP = new SimpleOperationDefinitionBuilder("boottime-controller-initializer-step", null)
        .setPrivateEntry()
        .build();

    protected final ProcessType processType;
    protected final DelegatingConfigurableAuthorizer authorizer;
    protected final ManagementSecurityIdentitySupplier securityIdentitySupplier;
    private final RunningModeControl runningModeControl;
    private final ResourceDefinition rootResourceDefinition;
    private final ControlledProcessState processState;
    private final OperationStepHandler prepareStep;
    private final Supplier<ExecutorService> executorService;
    private final Supplier<ControllerInstabilityListener> instabilityListener;
    private final ExpressionResolver expressionResolver;
    private volatile ModelControllerImpl controller;
    private volatile StabilityMonitor stabilityMonitor;
    private ConfigurationPersister configurationPersister;
    private final ManagedAuditLogger auditLogger;
    private final BootErrorCollector bootErrorCollector;
    private final CapabilityRegistry capabilityRegistry;
    private final ConfigurationExtension configExtension;
    private final RuntimeCapability<ExpressionResolver.ResolverExtensionRegistry> extensionRegistryCapability;

    /**
     * Construct a new instance.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootResourceDefinition  the root resource definition
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     * @param auditLogger             the audit logger
     * @param authorizer              handles authorization
     * @param capabilityRegistry      the capability registry
     */
    @Deprecated
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final ResourceDefinition rootResourceDefinition,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver,
                                        final ManagedAuditLogger auditLogger, final DelegatingConfigurableAuthorizer authorizer,
                                        final ManagementSecurityIdentitySupplier securityIdentitySupplier, final CapabilityRegistry capabilityRegistry) {
        this(null, null, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, null);
    }

    /**
     * Construct a new instance.
     * Simplified constructor for test case subclasses.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootDescriptionProvider the root description provider
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     *
     * @deprecated Here for backwards compatibility for ModelTestModelControllerService
     */
    @Deprecated
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final DescriptionProvider rootDescriptionProvider,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver) {
        this(null, null, processType, runningModeControl, configurationPersister, processState, null, rootDescriptionProvider,
                prepareStep, expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(),
                new CapabilityRegistry(processType.isServer()), null);

    }

    /**
     * Construct a new instance.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootResourceDefinition  the root resource definition
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     *
     * @deprecated Here for backwards compatibility for ModelTestModelControllerService
     */
    @Deprecated
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final ResourceDefinition rootResourceDefinition,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver) {
        this(null, null, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(),
                new CapabilityRegistry(processType.isServer()), null);
    }

    /**
     * Construct a new instance.
     *
     * @param processType            the type of process being controlled
     * @param runningModeControl     the controller of the process' running mode
     * @param configurationPersister the configuration persister
     * @param processState           the controlled process state
     * @param rootResourceDefinition the root resource definition
     * @param prepareStep            the prepare step to prepend to operation execution
     * @param expressionResolver     the expression resolver
     * @deprecated Here for backwards compatibility for ModelTestModelControllerService
     */
    @Deprecated
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                        final ResourceDefinition rootResourceDefinition, final OperationStepHandler prepareStep,
                                        final ExpressionResolver expressionResolver, final ManagedAuditLogger auditLogger,
                                        final DelegatingConfigurableAuthorizer authorizer) {
        this(null, null, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, auditLogger, authorizer, new ManagementSecurityIdentitySupplier(),
                new CapabilityRegistry(processType.isServer()), null);

    }
    /**
     * Construct a new instance.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootResourceDefinition  the root resource definition
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     * @param auditLogger             the audit logger
     * @param authorizer              handles authorization
     * @param capabilityRegistry      the capability registry
     */
    @Deprecated
    protected AbstractControllerService(final Supplier<ExecutorService> executorService,
                                        final Supplier<ControllerInstabilityListener> instabilityListener,
                                        final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final ResourceDefinition rootResourceDefinition,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver,
                                        final ManagedAuditLogger auditLogger, final DelegatingConfigurableAuthorizer authorizer,
                                        final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                        final CapabilityRegistry capabilityRegistry) {
        this(executorService, instabilityListener, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, null);
    }

    /**
     * Construct a new instance.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootResourceDefinition  the root resource definition
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     * @param auditLogger             the audit logger
     * @param authorizer              handles authorization
     * @param capabilityRegistry      the capability registry
     */
    protected AbstractControllerService(final Supplier<ExecutorService> executorService,
                                        final Supplier<ControllerInstabilityListener> instabilityListener,
                                        final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final ResourceDefinition rootResourceDefinition,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver,
                                        final ManagedAuditLogger auditLogger, final DelegatingConfigurableAuthorizer authorizer,
                                        final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                        final CapabilityRegistry capabilityRegistry, final ConfigurationExtension configExtension) {
        this(executorService, instabilityListener, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, configExtension);
    }

    private AbstractControllerService(final Supplier<ExecutorService> executorService,
                                      final Supplier<ControllerInstabilityListener> instabilityListener,
                                      final ProcessType processType, final RunningModeControl runningModeControl,
                                      final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                      final ResourceDefinition rootResourceDefinition, final DescriptionProvider rootDescriptionProvider,
                                      final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver, final ManagedAuditLogger auditLogger,
                                      final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                      final CapabilityRegistry capabilityRegistry, final ConfigurationExtension configExtension) {
        assert rootDescriptionProvider == null: "description provider cannot be used anymore";
        assert rootResourceDefinition != null: "Null root resource definition";
        assert expressionResolver != null : "Null expressionResolver";
        assert auditLogger != null : "Null auditLogger";
        assert authorizer != null : "Null authorizer";
        assert securityIdentitySupplier != null : "Null securityIdentitySupplier";
        assert capabilityRegistry!=null : "Null capabilityRegistry";
        this.executorService = executorService;
        this.instabilityListener = instabilityListener;
        this.processType = processType;
        this.runningModeControl = runningModeControl;
        this.configurationPersister = configurationPersister;
        this.rootResourceDefinition = rootResourceDefinition;
        this.processState = processState;
        this.prepareStep = prepareStep;
        this.expressionResolver = expressionResolver;
        if (expressionResolver instanceof ExpressionResolver.ResolverExtensionRegistry) {
            this.extensionRegistryCapability =
                    RuntimeCapability.Builder.of(EXPRESSION_RESOLVER_EXTENSION_REGISTRY_CAPABILITY_NAME,
                            (ExpressionResolver.ResolverExtensionRegistry) expressionResolver).build();
        } else {
            this.extensionRegistryCapability = null;
        }
        this.auditLogger = auditLogger;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.bootErrorCollector = new BootErrorCollector();
        this.capabilityRegistry = capabilityRegistry.createShadowCopy(); //create shadow copy of proper registry so changes can only be visible by .publish()
        this.configExtension = configExtension;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        assert capabilityRegistry.getPossibleCapabilities().isEmpty(): "registry is not empty";

        if (configurationPersister == null) {
            throw ControllerLogger.ROOT_LOGGER.persisterNotInjected();
        }
        final ServiceController<?> serviceController = context.getController();
        final ServiceContainer container = serviceController.getServiceContainer();
        final ServiceTarget target = context.getChildTarget();
        final ExecutorService executorService = this.executorService != null ? this.executorService.get() : null;

        final NotificationSupport notificationSupport = NotificationSupport.Factory.create(executorService);
        WritableAuthorizerConfiguration authorizerConfig = authorizer.getWritableAuthorizerConfiguration();
        authorizerConfig.reset();
        ManagementResourceRegistration rootResourceRegistration = ManagementResourceRegistration.Factory.forProcessType(processType).createRegistration(rootResourceDefinition, authorizerConfig, capabilityRegistry);
        final ModelControllerImpl controller = new ModelControllerImpl(container, target,
                rootResourceRegistration,
                new ContainerStateMonitor(container, getStabilityMonitor()),
                configurationPersister, processType, runningModeControl, prepareStep,
                processState, executorService, expressionResolver, authorizer, securityIdentitySupplier, auditLogger, notificationSupport,
                bootErrorCollector, createExtraValidationStepHandler(), capabilityRegistry, getPartialModelIndicator(),
                instabilityListener != null ? instabilityListener.get() : null);

        // Initialize the model
        initModel(controller.getManagementModel(), controller.getModelControllerResource());

        // Expose the client factory
        if (isExposingClientServicesAllowed()) {
            capabilityRegistry.registerCapability(
                    new RuntimeCapabilityRegistration(CLIENT_FACTORY_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
            capabilityRegistry.registerCapability(
                    new RuntimeCapabilityRegistration(NOTIFICATION_REGISTRY_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
            // Record the core capabilities with the root MRR so reads of it will show it as their provider
            // This also gets them recorded as 'possible capabilities' in the capability registry
            rootResourceRegistration.registerCapability(CLIENT_FACTORY_CAPABILITY);
            rootResourceRegistration.registerCapability(NOTIFICATION_REGISTRY_CAPABILITY);
            ModelControllerClientFactory clientFactory = new ModelControllerClientFactoryImpl(controller, securityIdentitySupplier);
            final ServiceName clientFactorySN = CLIENT_FACTORY_CAPABILITY.getCapabilityServiceName();
            final ServiceBuilder<?> clientFactorySB = target.addService(clientFactorySN);
            clientFactorySB.setInstance(new SimpleService(clientFactorySB.provides(clientFactorySN), clientFactory));
            clientFactorySB.install();
            final ServiceName notifyRegistrySN = NOTIFICATION_REGISTRY_CAPABILITY.getCapabilityServiceName();
            final ServiceBuilder<?> notifyRegistrySB = target.addService(notifyRegistrySN);
            notifyRegistrySB.setInstance(new SimpleService(notifyRegistrySB.provides(notifyRegistrySN), controller.getNotificationRegistry()));
            notifyRegistrySB.install();
        }
        if (extensionRegistryCapability != null) {
            capabilityRegistry.registerCapability(
                    new RuntimeCapabilityRegistration(extensionRegistryCapability, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
            rootResourceRegistration.registerCapability(extensionRegistryCapability);
        }
        capabilityRegistry.publish();  // These are visible immediately; no waiting for finishBoot
                                       // We publish even if we didn't register anything in case parent services did

        this.controller = controller;

        this.processState.setStarting();

        final long bootStackSize = getBootStackSize();
        final Thread bootThread = new Thread(null, new Runnable() {
            public void run() {
                try {
                    try {
                        boot(new BootContext() {
                            public ServiceTarget getServiceTarget() {
                                return target;
                            }
                        });
                    } finally {
                        processState.setRunning();
                    }
                    postBoot();
                } catch (Throwable t) {
                    container.shutdown();
                    if (t instanceof StackOverflowError) {
                        ROOT_LOGGER.errorBootingContainer(t, bootStackSize, BOOT_STACK_SIZE_PROPERTY);
                    } else {
                        ROOT_LOGGER.errorBootingContainer(t);
                    }
                } finally {
                    bootThreadDone();
                }

            }
        }, "Controller Boot Thread", bootStackSize);
        bootThread.start();
    }

    /**
     * Gets whether this controller service should install a {@link ModelControllerClientFactory}
     * and a {@link org.jboss.as.controller.notification.NotificationHandlerRegistry}
     * as a service. Default is {@code true}; this method allows test infrastructure subclasses to turn this off.
     * @return {@code true} if a service should be installed
     */
    protected boolean isExposingClientServicesAllowed() {
        return true;
    }

    /**
     * Boot the controller.  Called during service start.
     *
     * @param context the boot context
     * @throws ConfigurationPersistenceException
     *          if the configuration failed to be loaded
     */
    protected void boot(final BootContext context) throws ConfigurationPersistenceException {
        List<ModelNode> bootOps = configurationPersister.load();
        ModelNode op = registerModelControllerServiceInitializationBootStep(context);
        if (op != null) {
            bootOps.add(op);
        }
        boot(bootOps, false);
        finishBoot();
    }

    /**
     * Boot with the given operations, performing full model and capability registry validation.
     *
     * @param bootOperations the operations. Cannot be {@code null}
     * @param rollbackOnRuntimeFailure {@code true} if the boot should fail if operations fail in the runtime stage
     * @return {@code true} if boot was successful
     * @throws ConfigurationPersistenceException
     */
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, false, ModelControllerImpl.getMutableRootResourceRegistrationProvider());
    }

    /**
     * Boot with the given operations, optionally disabling model and capability registry validation.
     *
     * @param bootOperations the operations. Cannot be {@code null}
     * @param rollbackOnRuntimeFailure {@code true} if the boot should fail if operations fail in the runtime stage
     * @param skipModelValidation {@code true} if model and capability validation should be skipped.
     * @return {@code true} if boot was successful
     * @throws ConfigurationPersistenceException
     */
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure, boolean skipModelValidation) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, skipModelValidation, ModelControllerImpl.getMutableRootResourceRegistrationProvider());
    }

    /**
     * @deprecated internal use only  only for use by legacy test controllers
     */
    @Deprecated
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure,
            MutableRootResourceRegistrationProvider parallelBootRootResourceRegistrationProvider) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, false, parallelBootRootResourceRegistrationProvider);
    }

    /**
     * Boot, optionally disabling model and capability registry validation, using the given provider for the root
     * {@link ManagementResourceRegistration}.
     *
     * @param bootOperations the operations. Cannot be {@code null}
     * @param rollbackOnRuntimeFailure {@code true} if the boot should fail if operations fail in the runtime stage
     * @param skipModelValidation {@code true} if model and capability validation should be skipped.
     * @param parallelBootRootResourceRegistrationProvider provider of the root resource registration
     * @return {@code true} if boot was successful
     * @throws ConfigurationPersistenceException
     */
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure, boolean skipModelValidation,
                           MutableRootResourceRegistrationProvider parallelBootRootResourceRegistrationProvider) throws ConfigurationPersistenceException {
        return controller.boot(bootOperations, OperationMessageHandler.logging, ModelController.OperationTransactionControl.COMMIT,
                rollbackOnRuntimeFailure, parallelBootRootResourceRegistrationProvider, skipModelValidation,
                getPartialModelIndicator().isModelPartial(), configExtension);
    }

    /** @deprecated internal use only  only for use by legacy test controllers */
    @Deprecated
    protected ModelNode internalExecute(final ModelNode operation, final OperationMessageHandler handler,
                                        final ModelController.OperationTransactionControl control,
                                        final OperationAttachments attachments, final OperationStepHandler prepareStep) {
        OperationResponse or = controller.internalExecute(operation, handler, control, attachments, prepareStep, false, false);
        ModelNode result = or.getResponseNode();
        try {
            or.close();
        } catch (IOException e) {
            ROOT_LOGGER.debugf(e, "Caught exception closing response to %s whose associated streams, " +
                    "if any, were not wanted", operation);
        }
        return result;
    }

    protected OperationResponse internalExecute(final Operation operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control, final OperationStepHandler prepareStep) {
        return controller.internalExecute(operation.getOperation(), handler, control, operation, prepareStep, false,
                getPartialModelIndicator().isModelPartial());
    }

    protected OperationResponse internalExecute(final Operation operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control,
                                                final OperationStepHandler prepareStep, final boolean attemptLock) {
        return controller.internalExecute(operation.getOperation(), handler, control, operation, prepareStep, attemptLock,
                getPartialModelIndicator().isModelPartial());
    }

    protected OperationResponse internalExecute(final Operation operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control,
                                                final OperationStepHandler prepareStep, final boolean attemptLock, final boolean partialModel) {
        return controller.internalExecute(operation.getOperation(), handler, control, operation, prepareStep, attemptLock, partialModel);
    }

    /**
     * @deprecated internal use only and only by legacy test controllers
     */
    @Deprecated
    protected ModelNode executeReadOnlyOperation(final ModelNode operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control, final OperationAttachments attachments, final OperationStepHandler prepareStep, int lockPermit) {
        return controller.executeReadOnlyOperation(operation, handler, control, prepareStep, lockPermit);
    }

    /**
     * @deprecated internal use only
     */
    @Deprecated
    protected ModelNode executeReadOnlyOperation(final ModelNode operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control, final OperationStepHandler prepareStep, int lockPermit) {
        return controller.executeReadOnlyOperation(operation, handler, control, prepareStep, lockPermit);
    }

    @Deprecated
    protected ModelNode executeReadOnlyOperation(final ModelNode operation, final ModelController.OperationTransactionControl control, final OperationStepHandler prepareStep) {
        return controller.executeReadOnlyOperation(operation, control, prepareStep);
    }

    @Deprecated
    protected ModelNode executeReadOnlyOperation(final ModelNode operation, Resource model, final ModelController.OperationTransactionControl control, final OperationStepHandler prepareStep) {
        return controller.executeReadOnlyOperation(operation, model, control, prepareStep);
    }

    protected void finishBoot() throws ConfigurationPersistenceException {
        controller.finishBoot();
        configurationPersister.successfulBoot();
        capabilityRegistry.publish();
    }

    protected void finishBoot(boolean readOnly) throws ConfigurationPersistenceException {
        controller.finishBoot(readOnly);
        configurationPersister.successfulBoot();
        capabilityRegistry.publish();
    }

    protected void clearBootingReadOnlyFlag() {
        controller.clearBootingReadOnlyFlag();
    }

    protected void bootThreadDone() {

    }

    protected void postBoot() {
    }

    protected NotificationSupport getNotificationSupport() {
        return controller.getNotificationSupport();
    }

    protected final MutableRootResourceRegistrationProvider getMutableRootResourceRegistrationProvider() {
        return ModelControllerImpl.getMutableRootResourceRegistrationProvider();
    }

    protected PartialModelIndicator getPartialModelIndicator() {
        return PartialModelIndicator.DEFAULT;
    }

    protected final StabilityMonitor getStabilityMonitor() {
        if (stabilityMonitor == null) {
            stabilityMonitor = new StabilityMonitor();
        }
        return stabilityMonitor;
    }

    public void stop(final StopContext context) {
        capabilityRegistry.clear();
        capabilityRegistry.publish();
        ServiceNameFactory.clearCache();
        controller = null;
        stabilityMonitor = null;
        processState.setStopping();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    stopAsynchronous(context);
                } finally {
                    try {
                        authorizer.shutdown();
                    } finally {
                        context.complete();
                    }
                }
            }
        };
        final ExecutorService executorService = this.executorService != null ? this.executorService.get() : null;
        try {
            if (executorService != null) {
                try {
                    executorService.execute(r);
                } catch (RejectedExecutionException e) {
                    r.run();
                }
            } else {
                Thread executorShutdown = new Thread(r, getClass().getSimpleName() + " Shutdown Thread");
                executorShutdown.start();
            }
        } finally {
            processState.setStopped();
            context.asynchronous();
        }
    }

    /**
     * Hook for subclasses to perform work during the asynchronous task started by
     * {@link #stop(org.jboss.msc.service.StopContext)}. This base method does nothing.
     * <p><strong>Subclasses must not invoke {@link org.jboss.msc.service.StopContext#complete()}</strong></p>
     * @param context the stop context
     */
    protected void stopAsynchronous(StopContext context) {
        // no-op
    }

    /**
     * {@inheritDoc}
     *
     * @throws SecurityException if the caller does not have {@link ModelController#ACCESS_PERMISSION}
     */
    public ModelController getValue() throws IllegalStateException, IllegalArgumentException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ModelController.ACCESS_PERMISSION);
        }
        final ModelController controller = this.controller;
        if (controller == null) {
            throw new IllegalStateException();
        }
        return controller;
    }

    protected ExecutorService getExecutorService() {
        return executorService != null ? executorService.get() : null;
    }

    protected void setConfigurationPersister(final ConfigurationPersister persister) {
        this.configurationPersister = persister;
    }

    protected abstract void initModel(ManagementModel managementModel, Resource modelControllerResource);

    protected ManagedAuditLogger getAuditLogger() {
        return auditLogger;
    }

    protected BootErrorCollector getBootErrorCollector() {
        return bootErrorCollector;
    }

    protected OperationStepHandler createExtraValidationStepHandler() {
        return null;
    }

    /*
     * This attempts to obtain a shared mode ModelControllerLock, which will allow
     * multiple acquisition for concurrent reads while blocking acquisition of the exclusive
     * lock. Acquisition of the the exclusive lock will block read locks, until released.
     */
    protected void acquireReadLock(int operationId) throws InterruptedException {
        // block until a read lock is available
        controller.acquireReadLock(operationId, true);
    }

    /*
     * Release the shared mode read lock. The exclusive lock may not be aquired
     * while there are existing shared mode locks active.
     */
    protected void releaseReadLock(int operationId) {
        controller.releaseReadLock(operationId);
    }

    /**
     * Used to add the operation used to initialise the ModelControllerServiceInitialization instances.
     * The operation will only be registered, and called if the implementing class overrides and returns
     * a non {@code null} value from  {@link #getModelControllerServiceInitializationParams()}
     *
     * @param context the boot context
     */
    protected final ModelNode registerModelControllerServiceInitializationBootStep(BootContext context) {
        ModelControllerServiceInitializationParams initParams = getModelControllerServiceInitializationParams();
        if (initParams != null) {
            //Register the hidden op. The operation handler removes the operation once it is done
            controller.getManagementModel().getRootResourceRegistration().registerOperationHandler(INIT_CONTROLLER_OP, new ModelControllerServiceInitializationBootStepHandler(initParams));
            //Return the operation
            return Util.createEmptyOperation(INIT_CONTROLLER_OP.getName(), PathAddress.EMPTY_ADDRESS);
        }
        return null;
    }

    /**
     * Override to return a {@link ModelControllerServiceInitializationParams}. If {@code null} is returned,
     * {@link #registerModelControllerServiceInitializationBootStep(BootContext)} will not perform any initialization
     * of {@link ModelControllerServiceInitialization} instances.
     *
     * @return the context to use for registering {@link ModelControllerServiceInitialization} instances
     */
    protected ModelControllerServiceInitializationParams getModelControllerServiceInitializationParams() {
        return null;
    }

    protected void executeAdditionalCliBootScript() {
        // Do this check here so we don't need to load the additional class for the normal use-cases
        final String additionalBootCliScriptPath =
                WildFlySecurityManager.getPropertyPrivileged(CLI_SCRIPT_PROPERTY, null);

        ROOT_LOGGER.debug("Checking -D" + CLI_SCRIPT_PROPERTY + " to see if additional CLI operations are needed");
        if (additionalBootCliScriptPath == null) {
            ROOT_LOGGER.debug("No additional CLI operations are needed");
            return;
        }

        AdditionalBootCliScriptInvocation invocation = AdditionalBootCliScriptInvocation.create(additionalBootCliScriptPath,this);
        invocation.invoke();
    }

    /**
     * Operation step handler performing initialisation of the {@link ModelControllerServiceInitialization} instances.
     *
     */
    private final class ModelControllerServiceInitializationBootStepHandler implements OperationStepHandler {
        private final ModelControllerServiceInitializationParams initParams;

        ModelControllerServiceInitializationBootStepHandler(ModelControllerServiceInitializationParams initParams) {
            this.initParams = initParams;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    //Get the current management model instance here, so make sure that if ModelControllerServiceInitializations
                    //add resources, those resources end up in the model
                    assert context instanceof OperationContextImpl;
                    ManagementModel managementModel = ((OperationContextImpl)context).getManagementModel();

                    final ServiceLoader<ModelControllerServiceInitialization> sl = initParams.serviceLoader;

                    final String hostName = initParams.getHostName();
                    assert !processType.isHostController() || hostName != null;
                    for (ModelControllerServiceInitialization init : sl) {
                        if (processType.isHostController()) {
                            init.initializeHost(context.getServiceTarget(), managementModel, hostName);
                            init.initializeDomain(context.getServiceTarget(), managementModel);
                        } else {
                            init.initializeStandalone(context.getServiceTarget(), managementModel);

                        }
                    }
                    managementModel.getRootResourceRegistration().unregisterOperationHandler(INIT_CONTROLLER_OP.getName());
                }
            }, Stage.RUNTIME);
        }
    }

    /**
     * Parameters for initializing {@link ModelControllerServiceInitialization} instances
     */
    protected abstract static class ModelControllerServiceInitializationParams {

        /**
         * The service loader used to load the {@link ModelControllerServiceInitialization} instances.
         * The TCCL when creating the service loader needs to be the classloader of the {@link AbstractControllerService}
         * sub-class, which is what the TCCL is in the controller boot() method, which is where the instances of this interface get
         * created via the {@link AbstractControllerService#getModelControllerServiceInitializationParams()}
         *
         */
        private final ServiceLoader<ModelControllerServiceInitialization> serviceLoader;

        public ModelControllerServiceInitializationParams(ServiceLoader<ModelControllerServiceInitialization> serviceLoader) {
            this.serviceLoader = serviceLoader;
        }

        /**
         * Get the host name. If the process is a host controller this value cannot be {@code null}. If it is a
         * server process it may be {@code null}, since it never gets used.
         *
         * @return the host name
         */
        protected abstract String getHostName();
    }

    /**
     * Tracks whether the controller is working with a complete model or just a partial one.
     * Use case for this is host controller operation, particularly with --admin-only slaves,
     * where the domain-wide model may not be available.
     */
    protected interface PartialModelIndicator {

        PartialModelIndicator DEFAULT = new PartialModelIndicator(){};

        /**
         * Gets whether the configuration model be regarded as only being partial.
         * @return  {@code true} if the model is partial
         */
        default boolean isModelPartial() {
            return false;
        }
    }

    /**
     * Listener for notifications that the {@link ModelController} is unstable and a
     * process restart is necessary.
     */
    public interface ControllerInstabilityListener {
        /**
         * Notification that the {@link ModelController} should be considered to be unstable,
         * e.g. due to the service container managed by the {@code ModelController}
         * not being able to reach stability or due to some unhandled error.
         */
        void controllerUnstable();
    }

    private static final class SimpleService<V> implements Service {

        private final Consumer<V> injector;
        private final V value;

        private SimpleService(final Consumer<V> injector, final V value) {
            this.injector = injector;
            this.value = value;
        }

        @Override
        public void start(final StartContext context) {
            injector.accept(value);
        }

        @Override
        public void stop(final StopContext context) {
            injector.accept(null);
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    private static class AdditionalBootCliScriptInvocation {
        private final AbstractControllerService controllerService;
        private final File additionalBootCliScript;
        private final boolean keepAlive;
        // Will be null if keepAlive=true
        private final File doneMarker;
        // Will be null if keepAlive=true
        private final File restartInitiated;
        // Will be null if keepAlive=true
        private final File embeddedServerNeedsRestart;

        public AdditionalBootCliScriptInvocation(AbstractControllerService controllerService, File additionalBootCliScript, boolean keepAlive, File markerDirectory) {
            this.controllerService = controllerService;
            this.additionalBootCliScript = additionalBootCliScript;
            this.keepAlive = keepAlive;
            this.doneMarker = markerDirectory == null ? null : new File(markerDirectory, "wf-cli-invoker-result");
            this.restartInitiated = markerDirectory == null ? null : new File(markerDirectory, "wf-cli-shutdown-initiated");
            this.embeddedServerNeedsRestart = markerDirectory == null ? null : new File(markerDirectory, "wf-restart-embedded-server");
        }

        static AdditionalBootCliScriptInvocation create(String additionalBootCliScriptPath, AbstractControllerService controllerService) {

            boolean keepAlive = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged(SKIP_RELOAD_PROPERTY, "false"));
            final String markerDirectoryProperty =
                    WildFlySecurityManager.getPropertyPrivileged(MARKER_DIRECTORY_PROPERTY, null);
            if (keepAlive && markerDirectoryProperty == null) {
                throw ROOT_LOGGER.cliScriptPropertyDefinedWithoutMarkerDirectoryWhenNotSkippingReload(SKIP_RELOAD_PROPERTY, CLI_SCRIPT_PROPERTY, MARKER_DIRECTORY_PROPERTY);
            }

            if (controllerService.processType != ProcessType.STANDALONE_SERVER &&
                    controllerService.processType != ProcessType.EMBEDDED_SERVER) {
                throw ROOT_LOGGER.propertyCanOnlyBeUsedWithStandaloneOrEmbeddedServer(CLI_SCRIPT_PROPERTY);
            }
            if (controllerService.runningModeControl.getRunningMode() != RunningMode.ADMIN_ONLY) {
                throw ROOT_LOGGER.propertyCanOnlyBeUsedWithAdminOnlyModeServer(CLI_SCRIPT_PROPERTY);
            }
            File additionalBootCliScriptFile = new File(additionalBootCliScriptPath);
            if (!additionalBootCliScriptFile.exists()) {
                throw ROOT_LOGGER.couldNotFindDirectorySpecifiedByProperty(additionalBootCliScriptPath, CLI_SCRIPT_PROPERTY);
            }

            File markerDirectory = null;
            if (markerDirectoryProperty != null) {
                markerDirectory = new File(markerDirectoryProperty);
                if (!markerDirectory.exists()) {
                    throw ROOT_LOGGER.couldNotFindDirectorySpecifiedByProperty(markerDirectoryProperty, MARKER_DIRECTORY_PROPERTY);
                }
            }

            return new AdditionalBootCliScriptInvocation(controllerService, additionalBootCliScriptFile, keepAlive, markerDirectory);
        }

        void invoke() {
            ROOT_LOGGER.checkingForPresenceOfRestartMarkerFile();
            if (restartInitiated != null && restartInitiated.exists()) {
                ROOT_LOGGER.foundRestartMarkerFile(restartInitiated);
                try (ModelControllerClient client = controllerService.controller.createBootClient(controllerService.executorService.get())) {
                    // The shutdown takes us back to admin-only mode, we now need to reload into normal mode
                    // remove the marker first
                    deleteFile(restartInitiated);

                    executeReload(client, true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                ROOT_LOGGER.noRestartMarkerFile();
                if (keepAlive) {
                    ROOT_LOGGER.initialisedAdditionalBootCliScriptSystemKeepingAlive(additionalBootCliScript, doneMarker);
                } else {
                    ROOT_LOGGER.initialisedAdditionalBootCliScriptSystemNotKeepingAlive(additionalBootCliScript);
                }
                executeAdditionalCliScript();
            }
        }

        private void executeAdditionalCliScript() {
            boolean success = false;
            Throwable originalException = null;
            try {
                deleteFile(doneMarker);
                deleteFile(embeddedServerNeedsRestart);

                try ( InvokerLoader loader = new InvokerLoader()) {

                    assert loader.getInvoker() != null : "No invoker found";

                    try ( ModelControllerClient client = controllerService.controller.createBootClient(controllerService.executorService.get())) {

                        ROOT_LOGGER.executingBootCliScript(additionalBootCliScript);

                        loader.getInvoker().runCliScript(client, additionalBootCliScript);

                        ROOT_LOGGER.completedRunningBootCliScript();

                        if (!keepAlive) {
                            boolean restart = controllerService.processState.checkRestartRequired();
                            if (restart) {
                                executeRestart(client);
                            } else {
                                executeReload(client, false);
                            }
                        }
                        success = true;
                    }
                }
            } catch (IOException e) {
                originalException = new UncheckedIOException(e);
            } catch (Throwable ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                originalException = ex;
            } finally {
                clearProperties();
                Throwable suppressed = originalException; // OK to be null
                try {
                    if (doneMarker != null) {
                        doneMarker.createNewFile();
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(doneMarker))) {
                            writer.write(success ? SUCCESS : FAILED);
                            writer.write('\n');
                        }
                    }
                } catch (IOException ex) {
                    if (originalException != null) {
                        originalException.addSuppressed(ex);
                        suppressed = originalException;
                    } else {
                        suppressed = new UncheckedIOException(ex);
                    }
                }
                if (suppressed != null) {
                    if (suppressed instanceof RuntimeException) {
                        throw (RuntimeException) suppressed;
                    }
                    if (suppressed instanceof Error) {
                        throw (Error) suppressed;
                    }
                }
            }
        }

        private void executeRestart(ModelControllerClient client) {
            if (controllerService.processType == ProcessType.STANDALONE_SERVER) {
                executeRestartNormalServer(client);
            } else {
                recordRestartEmbeddedServer();
            }
        }

        private void executeRestartNormalServer(ModelControllerClient client) {
            try {
                ModelNode shutdown = Util.createOperation(SHUTDOWN, PathAddress.EMPTY_ADDRESS);
                shutdown.get(RESTART).set(true);
                // Since we cannot clear system properties for a shutdown, we write a marker here to
                // skip running the cli script again
                Files.createFile(restartInitiated.toPath());

                ROOT_LOGGER.restartingServerAfterBootCliScript(restartInitiated, CLI_SCRIPT_PROPERTY, SKIP_RELOAD_PROPERTY, MARKER_DIRECTORY_PROPERTY);

                ModelNode result = client.execute(shutdown);
                if (result.get(OUTCOME).asString().equals(FAILED)) {
                    throw new RuntimeException(result.get(FAILURE_DESCRIPTION).asString());
                }
            } catch (IOException e) {
                try {
                    deleteFile(restartInitiated);
                } catch (IOException ex) {
                    e = ex;
                }
                throw new RuntimeException(e);
            }
        }

        private void recordRestartEmbeddedServer() {
            try {
                Files.createFile(embeddedServerNeedsRestart.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void executeReload(ModelControllerClient client, boolean afterRestart) {
            try {
                ModelNode reload = Util.createOperation(RELOAD, PathAddress.EMPTY_ADDRESS);
                clearProperties();
                if (!afterRestart) {
                    ROOT_LOGGER.reloadingServerToNormalModeAfterAdditionalBootCliScript(CLI_SCRIPT_PROPERTY, SKIP_RELOAD_PROPERTY, MARKER_DIRECTORY_PROPERTY);
                } else {
                    ROOT_LOGGER.reloadingServerToNormalModeAfterRestartAfterAdditionalBootCliScript(CLI_SCRIPT_PROPERTY, SKIP_RELOAD_PROPERTY, MARKER_DIRECTORY_PROPERTY);
                }
                ModelNode result = client.execute(reload);
                if (result.get(OUTCOME).asString().equals(FAILED)) {
                    throw new RuntimeException(result.get(FAILURE_DESCRIPTION).asString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                clearProperties();
            }
        }

        private void clearProperties() {
            WildFlySecurityManager.clearPropertyPrivileged(CLI_SCRIPT_PROPERTY);
            WildFlySecurityManager.clearPropertyPrivileged(SKIP_RELOAD_PROPERTY);
            WildFlySecurityManager.clearPropertyPrivileged(MARKER_DIRECTORY_PROPERTY);
        }

        private void deleteFile(File file) throws IOException {
            if (file != null) {
                Path path = file.toPath();
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            }
        }

        private static class InvokerLoader implements Closeable {

            private final AdditionalBootCliScriptInvoker invoker;
            private TemporaryModuleLayer tempModuleLayer;

            private InvokerLoader() {
                // Ability to override the invoker in unit tests where we don't have all the modules set up
                String testInvoker = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.test.override.cli.boot.invoker", null);
                if (testInvoker != null) {
                    try {
                        invoker = (AdditionalBootCliScriptInvoker) Class.forName(testInvoker).newInstance();
                        return;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                // We are running in a proper server, load the invoker normally
                this.tempModuleLayer = TemporaryModuleLayer.create(new PathFilter() {
                    @Override
                    public boolean accept(String path) {
                        return path.startsWith("org/jboss/as/cli/main") || path.startsWith("org/aesh/main");
                    }
                });

                try {
                    Module module = tempModuleLayer.getModuleLoader().loadModule("org.jboss.as.cli");
                    ServiceLoader<AdditionalBootCliScriptInvoker> sl = module.loadService(AdditionalBootCliScriptInvoker.class);
                    AdditionalBootCliScriptInvoker invoker = null;
                    for (AdditionalBootCliScriptInvoker currentInvoker : sl) {
                        if (invoker != null) {
                            throw ROOT_LOGGER.moreThanOneInstanceOfAdditionalBootCliScriptInvokerFound(invoker.getClass().getName(), currentInvoker.getClass().getName());
                        }
                        invoker = currentInvoker;
                    }
                    this.invoker = invoker;
                } catch (ModuleLoadException e) {
                    throw new RuntimeException(e);
                }
            }

            private AdditionalBootCliScriptInvoker getInvoker() {
                return invoker;
            }

            @Override
            public void close() throws IOException {
                if (tempModuleLayer != null) {
                    tempModuleLayer.close();
                }
            }
        }
    }

}
