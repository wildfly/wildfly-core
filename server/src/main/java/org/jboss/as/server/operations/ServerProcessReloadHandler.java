/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.operations;

import java.util.Locale;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.ProcessReloadHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerProcessReloadHandler extends ProcessReloadHandler<RunningModeControl> {

    private static final AttributeDefinition USE_CURRENT_SERVER_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.SERVER_CONFIG)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    protected static final AttributeDefinition ADMIN_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADMIN_ONLY, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.START_MODE)
            .setDeprecated(ModelVersion.create(5, 0, 0))
            .setDefaultValue(ModelNode.FALSE).build();

    private static final AttributeDefinition SERVER_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SERVER_CONFIG, ModelType.STRING, true)
            .setAlternatives(ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG)
            .build();

    protected static final AttributeDefinition START_MODE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.START_MODE, ModelType.STRING, true)
            .setValidator(EnumValidator.create(StartMode.class))
            .setAlternatives(ModelDescriptionConstants.ADMIN_ONLY)
            .setDefaultValue(new ModelNode(StartMode.NORMAL.toString())).build();
    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, USE_CURRENT_SERVER_CONFIG, SERVER_CONFIG, START_MODE};

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
}
