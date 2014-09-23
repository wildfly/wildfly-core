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

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.NotificationSupport;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A base class for controller services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
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

    private static final OperationDefinition INIT_CONTROLLER_OP = new SimpleOperationDefinitionBuilder("boottime-controller-initializer-step", null)
        .setPrivateEntry()
        .build();

    protected final ProcessType processType;
    protected final DelegatingConfigurableAuthorizer authorizer;
    private final RunningModeControl runningModeControl;
    private final ResourceDefinition rootResourceDefinition;
    private final ControlledProcessState processState;
    private final OperationStepHandler prepareStep;
    private final InjectedValue<ExecutorService> injectedExecutorService = new InjectedValue<ExecutorService>();
    private final ExpressionResolver expressionResolver;
    private volatile ModelControllerImpl controller;
    private ConfigurationPersister configurationPersister;
    private final ManagedAuditLogger auditLogger;
    private final BootErrorCollector bootErrorCollector;

    /**
     * Construct a new instance.
     *
     * @param processType             the type of process being controlled
     * @param runningModeControl      the controller of the process' running mode
     * @param configurationPersister  the configuration persister
     * @param processState            the controlled process state
     * @param rootDescriptionProvider the root description provider
     * @param prepareStep             the prepare step to prepend to operation execution
     * @param expressionResolver      the expression resolver
     * @param auditLogger             the audit logger
     */
    @Deprecated
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final DescriptionProvider rootDescriptionProvider,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver,
                                        final ManagedAuditLogger auditLogger, DelegatingConfigurableAuthorizer authorizer) {
        this(processType, runningModeControl, configurationPersister, processState, null, rootDescriptionProvider,
                prepareStep, expressionResolver, auditLogger, authorizer);

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
     */
    protected AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                        final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final ResourceDefinition rootResourceDefinition,
                                        final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver,
                                        final ManagedAuditLogger auditLogger, final DelegatingConfigurableAuthorizer authorizer) {
        this(processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, auditLogger, authorizer);
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
        this(processType, runningModeControl, configurationPersister, processState, null, rootDescriptionProvider,
                prepareStep, expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer());

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
        this(processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null,
                prepareStep, expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer());
    }

    private AbstractControllerService(final ProcessType processType, final RunningModeControl runningModeControl,
                                      final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                      final ResourceDefinition rootResourceDefinition, final DescriptionProvider rootDescriptionProvider,
                                      final OperationStepHandler prepareStep, final ExpressionResolver expressionResolver, final ManagedAuditLogger auditLogger,
                                      final DelegatingConfigurableAuthorizer authorizer) {
        assert rootDescriptionProvider == null: "description provider cannot be used anymore";
        assert rootResourceDefinition != null: "Null root resource definition";
        assert expressionResolver != null : "Null expressionResolver";
        assert auditLogger != null : "Null auditLogger";
        assert authorizer != null : "Null authorizer";
        this.processType = processType;
        this.runningModeControl = runningModeControl;
        this.configurationPersister = configurationPersister;
        this.rootResourceDefinition = rootResourceDefinition;
        this.processState = processState;
        this.prepareStep = prepareStep;
        this.expressionResolver = expressionResolver;
        this.auditLogger = auditLogger;
        this.authorizer = authorizer;
        this.bootErrorCollector = new BootErrorCollector();
    }

    @Override
    public void start(final StartContext context) throws StartException {

        if (configurationPersister == null) {
            throw ControllerLogger.ROOT_LOGGER.persisterNotInjected();
        }
        final ServiceController<?> serviceController = context.getController();
        final ServiceContainer container = serviceController.getServiceContainer();
        final ServiceTarget target = context.getChildTarget();
        final ExecutorService executorService = injectedExecutorService.getOptionalValue();

        final NotificationSupport notificationSupport = NotificationSupport.Factory.create(executorService);
        WritableAuthorizerConfiguration authorizerConfig = authorizer.getWritableAuthorizerConfiguration();
        authorizerConfig.reset();
        ManagementResourceRegistration rootResourceRegistration = ManagementResourceRegistration.Factory.create(rootResourceDefinition, authorizerConfig);
        final ModelControllerImpl controller = new ModelControllerImpl(container, target,
                rootResourceRegistration,
                new ContainerStateMonitor(container),
                configurationPersister, processType, runningModeControl, prepareStep,
                processState, executorService, expressionResolver, authorizer, auditLogger, notificationSupport, bootErrorCollector);

        // Initialize the model
        initModel(controller.getManagementModel(), controller.getModelControllerResource());
        this.controller = controller;

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

    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, false, ModelControllerImpl.getMutableRootResourceRegistrationProvider());
    }

    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure, boolean skipModelValidation) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, skipModelValidation, ModelControllerImpl.getMutableRootResourceRegistrationProvider());
    }

    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure,
            MutableRootResourceRegistrationProvider parallelBootRootResourceRegistrationProvider) throws ConfigurationPersistenceException {
        return boot(bootOperations, rollbackOnRuntimeFailure, false, parallelBootRootResourceRegistrationProvider);
    }

    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure, boolean skipModelValidation,
            MutableRootResourceRegistrationProvider parallelBootRootResourceRegistrationProvider) throws ConfigurationPersistenceException {
        return controller.boot(bootOperations, OperationMessageHandler.logging, ModelController.OperationTransactionControl.COMMIT,
                rollbackOnRuntimeFailure, parallelBootRootResourceRegistrationProvider, skipModelValidation);
    }

    /** @deprecated internal use only  only for use by legacy test controllers */
    @Deprecated
    protected ModelNode internalExecute(final ModelNode operation, final OperationMessageHandler handler,
                                        final ModelController.OperationTransactionControl control,
                                        final OperationAttachments attachments, final OperationStepHandler prepareStep) {
        OperationResponse or = controller.internalExecute(operation, handler, control, attachments, prepareStep, false);
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
        return controller.internalExecute(operation.getOperation(), handler, control, operation, prepareStep, false);
    }

    protected OperationResponse internalExecute(final Operation operation, final OperationMessageHandler handler, final ModelController.OperationTransactionControl control,
                                                final OperationStepHandler prepareStep, final boolean attemptLock) {
        return controller.internalExecute(operation.getOperation(), handler, control, operation, prepareStep, attemptLock);
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
    }

    protected void bootThreadDone() {

    }

    protected final MutableRootResourceRegistrationProvider getMutableRootResourceRegistrationProvider() {
        return ModelControllerImpl.getMutableRootResourceRegistrationProvider();
    }

    public void stop(final StopContext context) {
        controller = null;

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
        final ExecutorService executorService = injectedExecutorService.getOptionalValue();
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

    public InjectedValue<ExecutorService> getExecutorServiceInjector() {
        return injectedExecutorService;
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
                    assert processType != ProcessType.HOST_CONTROLLER || hostName != null;
                    for (ModelControllerServiceInitialization init : sl) {
                        if (processType == ProcessType.HOST_CONTROLLER) {
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

}

