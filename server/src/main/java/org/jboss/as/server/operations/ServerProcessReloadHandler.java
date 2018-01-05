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
package org.jboss.as.server.operations;

import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.ProcessReloadHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import static org.jboss.as.controller.AbstractControllerService.EXECUTOR_CAPABILITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerProcessReloadHandler extends ProcessReloadHandler<RunningModeControl> {

    private static final AttributeDefinition USE_CURRENT_SERVER_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.SERVER_CONFIG)
            .setDefaultValue(new ModelNode(true))
            .build();

    protected static final AttributeDefinition ADMIN_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADMIN_ONLY, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.START_MODE)
            .setDeprecated(ModelVersion.create(5, 0, 0))
            .setDefaultValue(new ModelNode(false)).build();

    private static final AttributeDefinition SERVER_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SERVER_CONFIG, ModelType.STRING, true)
            .setAlternatives(ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG)
            .build();

    protected static final AttributeDefinition START_MODE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.START_MODE, ModelType.STRING, true)
            .setValidator(new EnumValidator<>(StartMode.class, true, false))
            .setAlternatives(ModelDescriptionConstants.ADMIN_ONLY)
            .setDefaultValue(new ModelNode(StartMode.NORMAL.toString())).build();

    protected static final AttributeDefinition SUSPEND_TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT, true)
            .setDefaultValue(new ModelNode(0)).build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, USE_CURRENT_SERVER_CONFIG, SERVER_CONFIG, START_MODE, SUSPEND_TIMEOUT};

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ServerDescriptions.getResourceDescriptionResolver("server"))
                                                                .setParameters(ATTRIBUTES)
                                                                .setRuntimeOnly()
                                                                .build();

    private final ServerEnvironment environment;
    public ServerProcessReloadHandler(ServiceName rootService, RunningModeControl runningModeControl,
            ControlledProcessState processState, ServerEnvironment environment) {
        super(rootService, runningModeControl, processState);
        this.environment = environment;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final int timeoutInSeconds = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();
        context.acquireControllerLock();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ReloadContext<RunningModeControl> reloadContext = initializeReloadContext(context, operation);

                AuthorizationResult authorizationResult = context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                            PathAddress.pathAddress(operation.get(OP_ADDR)), authorizationResult.getExplanation());
                }

                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            final ServiceController<?> service = context.getServiceRegistry(false).getRequiredService(rootService);
                            final ServiceRegistry registry = context.getServiceRegistry(false);
                            final ServiceController<SuspendController> suspendControllerServiceController = (ServiceController<SuspendController>) registry.getRequiredService(SuspendController.SERVICE_NAME);
                            final SuspendController suspendController = suspendControllerServiceController.getValue();
                            final ExecutorService executor = (ExecutorService) context.getServiceRegistry(false).getRequiredService(EXECUTOR_CAPABILITY.getCapabilityServiceName()).getValue();
                            final RestartAction restartAction = new RestartAction(service, reloadContext, suspendController, executor);
                            if (timeoutInSeconds != 0) {
                                OperationListener operationListener = new OperationListener() {
                                    @Override
                                    public void suspendStarted() {}

                                    @Override
                                    public void complete() {
                                        suspendController.removeListener(this);
                                        restartAction.restart();
                                    }

                                    @Override
                                    public void cancelled() {
                                        suspendController.removeListener(this);
                                        restartAction.resume();
                                    }

                                    @Override
                                    public void timeout() {
                                        suspendController.removeListener(this);
                                        restartAction.restart();
                                    }
                                };
                                suspendController.addListener(operationListener);
                                suspendController.suspend(timeoutInSeconds > 0 ?  timeoutInSeconds * 1000 : timeoutInSeconds);
                            } else {
                                restartAction.restart();
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    @Override
    protected ProcessReloadHandler.ReloadContext<RunningModeControl> initializeReloadContext(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final boolean unmanaged = context.getProcessType() != ProcessType.DOMAIN_SERVER; // make sure that the params are ignored for managed servers
        boolean adminOnly = unmanaged && ADMIN_ONLY.resolveModelAttribute(context, operation).asBoolean(false);
        final boolean useCurrentConfig = unmanaged && USE_CURRENT_SERVER_CONFIG.resolveModelAttribute(context, operation).asBoolean(true);
        final String startMode = START_MODE.resolveModelAttribute(context, operation).asString();

        if(operation.get(ModelDescriptionConstants.ADMIN_ONLY).isDefined() && operation.get(ModelDescriptionConstants.START_MODE).isDefined()) {
            throw ServerLogger.ROOT_LOGGER.cannotSpecifyBothAdminOnlyAndStartMode();
        }

        boolean suspend = false;
        if(!adminOnly) {
            switch (startMode.toLowerCase(Locale.ENGLISH)) {
                case ModelDescriptionConstants.ADMIN_ONLY:
                    if(unmanaged) {
                        adminOnly = true;
                    }
                    break;
                case ModelDescriptionConstants.SUSPEND:
                    suspend = true;
                    break;
            }
        }
        final boolean finalSuspend = suspend;
        final boolean finalAdminOnly = adminOnly;

        final String serverConfig = unmanaged && operation.hasDefined(SERVER_CONFIG.getName()) ? SERVER_CONFIG.resolveModelAttribute(context, operation).asString() : null;

        if (operation.hasDefined(USE_CURRENT_SERVER_CONFIG.getName()) && serverConfig != null) {
            throw ServerLogger.ROOT_LOGGER.cannotBothHaveFalseUseCurrentConfigAndServerConfig();
        }
        if (serverConfig != null && !environment.getServerConfigurationFile().checkCanFindNewBootFile(serverConfig)) {
            throw ServerLogger.ROOT_LOGGER.serverConfigForReloadNotFound(serverConfig);
        }
        return new ReloadContext<RunningModeControl>() {

            @Override
            public void reloadInitiated(RunningModeControl runningModeControl) {
            }

            @Override
            public void doReload(RunningModeControl runningModeControl) {
                runningModeControl.setRunningMode(finalAdminOnly ? RunningMode.ADMIN_ONLY : RunningMode.NORMAL);
                runningModeControl.setReloaded();
                runningModeControl.setUseCurrentConfig(useCurrentConfig);
                runningModeControl.setNewBootFileName(serverConfig);
                runningModeControl.setSuspend(finalSuspend);
            }
        };
    }

    private enum StartMode {
        NORMAL("normal"),
        ADMIN_ONLY("admin-only"),
        SUSPEND("suspend");

        private final String localName;

        StartMode(String localName) {
            this.localName = localName;
        }

        @Override
        public String toString() {
            return localName;
        }
    }

    private class RestartAction {
        private final ServiceController<?> service;
        private final ReloadContext<RunningModeControl> reloadContext;
        private final SuspendController suspendController;
        private final ExecutorService executorService;

        RestartAction(final ServiceController<?> service, final ReloadContext<RunningModeControl> reloadContext, final SuspendController suspendController, ExecutorService executorService) {
            this.service = service;
            this.reloadContext = reloadContext;
            this.suspendController = suspendController;
            this.executorService = executorService;
        }

        void restart() {
            service.addListener(new AbstractServiceListener<Object>() {
                @Override
                public void listenerAdded(final ServiceController<?> controller) {
                    Future<?> stopping = executorService.submit(() -> {
                        reloadContext.reloadInitiated(runningModeControl);
                        processState.setStopping();
                        controller.setMode(ServiceController.Mode.NEVER);
                    });
                    try {
                        stopping.get();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        ControllerLogger.ROOT_LOGGER.errorStoppingServer(ex);
                    }
                }

                @Override
                public void transition(final ServiceController<? extends Object> controller, final ServiceController.Transition transition) {
                    if (transition == ServiceController.Transition.STOPPING_to_DOWN) {
                        controller.removeListener(this);
                        reloadContext.doReload(runningModeControl);
                        controller.setMode(ServiceController.Mode.ACTIVE);
                    }
                }
            });
        }

        void resume(){
            suspendController.resume();
        }
    }
}
