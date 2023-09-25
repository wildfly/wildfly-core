/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.process.ProcessInfo;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Starts or reconnect all auto-start servers (at boot).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class StartServersHandler implements OperationStepHandler {

    public static final boolean START_BLOCKING = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged("org.jboss.as.host.start.servers.sequential", "false"));
    public static final String OPERATION_NAME = "start-servers";

  //Private method does not need resources for description
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, null)
        .setPrivateEntry()
        .build();

    private final ServerInventory serverInventory;
    private final HostControllerEnvironment hostControllerEnvironment;
    private final HostRunningModeControl runningModeControl;

    public static final AttributeDefinition ENABLE_AUTO_START = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ENABLE_AUTO_START, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    /**
     * Create the ServerAddHandler
     */
    public StartServersHandler(final HostControllerEnvironment hostControllerEnvironment, final ServerInventory serverInventory, HostRunningModeControl runningModeControl) {
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.serverInventory = serverInventory;
        this.runningModeControl = runningModeControl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final boolean enabledAutoStart = ENABLE_AUTO_START.resolveModelAttribute(context, operation).asBoolean();

        if (!context.isBooting() && !enabledAutoStart) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.invocationNotAllowedAfterBoot(operation.require(OP)));
        }

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        final ModelNode domainModel = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // start servers
                final Resource resource =  context.readResource(PathAddress.EMPTY_ADDRESS);
                final ModelNode hostModel = Resource.Tools.readModel(resource);
                if(hostModel.hasDefined(SERVER_CONFIG)) {
                    final ModelNode servers = hostModel.get(SERVER_CONFIG).clone();
                    if (hostControllerEnvironment.isRestart() || runningModeControl.getRestartMode() == RestartMode.HC_ONLY){
                        restartedHcStartOrReconnectServers(servers, domainModel, context, enabledAutoStart);
                        runningModeControl.setRestartMode(RestartMode.SERVERS);
                    } else if (enabledAutoStart) {
                        cleanStartServers(servers, domainModel, context);
                    }
                }
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private void cleanStartServers(final ModelNode servers, final ModelNode domainModel, OperationContext context) throws OperationFailedException {
        Map<String, ProcessInfo> processInfos = serverInventory.determineRunningProcesses();
        for(final Property serverProp : servers.asPropertyList()) {
            String serverName = serverProp.getName();
            if (ServerConfigResourceDefinition.AUTO_START.resolveModelAttribute(context, serverProp.getValue()).asBoolean(true)) {
                ProcessInfo info = processInfos.get(serverInventory.getServerProcessName(serverName));
                if ( info != null ){
                    serverInventory.reconnectServer(serverName, domainModel, info.isRunning(), info.isStopping());
                } else {
                    try {
                        serverInventory.startServer(serverName, domainModel, START_BLOCKING, false);
                    } catch (Exception e) {
                        ROOT_LOGGER.failedToStartServer(e, serverName);
                    }
                }
            }
        }
    }

    private void restartedHcStartOrReconnectServers(final ModelNode servers, final ModelNode domainModel, final OperationContext context, final boolean enabledAutoStart) {
        Map<String, ProcessInfo> processInfos = serverInventory.determineRunningProcesses();
        for(final String serverName : servers.keys()) {
            ProcessInfo info = processInfos.get(serverInventory.getServerProcessName(serverName));
            boolean auto = servers.get(serverName, AUTO_START).asBoolean(true);
            if (info == null && auto && enabledAutoStart) {
                try {
                    serverInventory.startServer(serverName, domainModel, START_BLOCKING, false);
                } catch (Exception e) {
                    ROOT_LOGGER.failedToStartServer(e, serverName);
                }
            } else if (info != null){
                serverInventory.reconnectServer(serverName, domainModel, info.isRunning(), info.isStopping());
            }
        }
    }
}
