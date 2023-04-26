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

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.SystemExiter;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.as.server.suspend.SuspendController.State;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler that shuts down the standalone server.
 *
 * @author Jason T. Greene
 */
public class ServerShutdownHandler implements OperationStepHandler {

    protected static final SimpleAttributeDefinition RESTART = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RESTART, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setAlternatives(ModelDescriptionConstants.PERFORM_INSTALLATION)
            .setRequired(false)
            .build();

    // This requires the Installation Manager capability
    protected static final SimpleAttributeDefinition PERFORM_INSTALLATION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PERFORM_INSTALLATION, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setAlternatives(ModelDescriptionConstants.RESTART)
            .build();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SHUTDOWN, ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
            .setParameters(RESTART, TIMEOUT, SUSPEND_TIMEOUT, PERFORM_INSTALLATION)
            .setRuntimeOnly()
            .build();


    private final ControlledProcessState processState;
    private final ServerEnvironment environment;

    public ServerShutdownHandler(ControlledProcessState processState, ServerEnvironment serverEnvironment) {
        this.processState = processState;
        this.environment = serverEnvironment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        renameTimeoutToSuspendTimeout(operation);
        final boolean restart = RESTART.resolveModelAttribute(context, operation).asBoolean();
        final int timeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt(); //in seconds, need to convert to ms
        final boolean performInstallation = PERFORM_INSTALLATION.resolveModelAttribute(context, operation).asBoolean();

        // Verify the candidate server is prepared
        if (performInstallation) {
            // Cannot use the Installation Manager service, we will generate a circular reference via maven
            final String productName = environment.getProductConfig().getProductName();
            try (FileInputStream in = new FileInputStream(environment.getHomeDir().toPath().resolve("bin").resolve("installation-manager.properties").toFile())) {
                final Properties prop = new Properties();
                prop.load(in);
                String current = (String) prop.get("INST_MGR_STATUS");
                if (current == null || !current.trim().equals("PREPARED")) {
                    throw ServerLogger.ROOT_LOGGER.noServerInstallationPrepared(productName);
                }
            } catch (Exception e) {
                throw ServerLogger.ROOT_LOGGER.noServerInstallationPrepared(productName);
            }
        }


        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // WFLY-2741 -- DO NOT call context.getServiceRegistry(true) as that will trigger blocking for
                // service container stability and one use case for this op is to recover from a
                // messed up service container from a previous op. Instead just ask for authorization.
                // Note that we already have the exclusive lock, so we are just skipping waiting for stability.
                // If another op that is a step in a composite step with this op needs to modify the container
                // it will have to wait for container stability, so skipping this only matters for the case
                // where this step is the only runtime change.
//                context.getServiceRegistry(true);
                AuthorizationResult authorizationResult = context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                            PathAddress.pathAddress(operation.get(OP_ADDR)), authorizationResult.getExplanation());
                }
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            //even if the timeout is zero we still pause the server
                            //to stop new requests being accepted as it is shutting down
                            final ShutdownAction shutdown = new ShutdownAction(getOperationName(operation), restart, performInstallation);
                            final ServiceRegistry registry = context.getServiceRegistry(false);
                            final ServiceController<SuspendController> suspendControllerServiceController = (ServiceController<SuspendController>) registry.getRequiredService(JBOSS_SUSPEND_CONTROLLER);
                            final SuspendController suspendController = suspendControllerServiceController.getValue();
                            if (suspendController.getState() == State.SUSPENDED) {
                                // WFCORE-4935 if server is already in suspend, don't register any listener, just shut it down
                                shutdown.shutdown();
                            } else {
                                OperationListener listener = new OperationListener() {
                                    @Override
                                    public void suspendStarted() {

                                    }

                                    @Override
                                    public void complete() {
                                        suspendController.removeListener(this);
                                        shutdown.shutdown();
                                    }

                                    @Override
                                    public void cancelled() {
                                        suspendController.removeListener(this);
                                        shutdown.cancel();
                                    }

                                    @Override
                                    public void timeout() {
                                        suspendController.removeListener(this);
                                        shutdown.shutdown();
                                    }
                                };
                                suspendController.addListener(listener);
                                suspendController.suspend(timeout > 0 ? timeout * 1000 : timeout);
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private static String getOperationName(ModelNode op) {
        return op.hasDefined(OP) ? op.get(OP).asString() : SHUTDOWN;
    }

    private final class ShutdownAction extends AtomicBoolean {

        private final String op;
        private final boolean restart;
        private final boolean performInstallation;

        private ShutdownAction(String op, boolean restart, boolean performInstallation) {
            this.op = op;
            this.restart = restart;
            this.performInstallation = performInstallation;
        }

        void cancel() {
            compareAndSet(false, true);
        }

        void shutdown() {
            if(compareAndSet(false, true)) {
                processState.setStopping();
                final Thread thread = new Thread(new Runnable() {
                    public void run() {
                        int exitCode = restart ? ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT : performInstallation ? ExitCodes.PERFORM_INSTALLATION_FROM_STARTUP_SCRIPT : ExitCodes.NORMAL;
                        SystemExiter.logAndExit(new SystemExiter.ExitLogger() {
                            @Override
                            public void logExit() {
                                ServerLogger.ROOT_LOGGER.shuttingDownInResponseToManagementRequest(op);
                            }
                        }, exitCode);
                    }
                });
                // The intention is that this shutdown is graceful, and so the client gets a reply.
                // At the time of writing we did not yet have graceful shutdown.
                thread.setName("Management Triggered Shutdown");
                thread.start();
            }
        }
    }
}
