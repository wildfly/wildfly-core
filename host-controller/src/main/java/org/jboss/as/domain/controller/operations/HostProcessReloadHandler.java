/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import java.util.Locale;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DefaultOperationDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.ProcessReloadHandler;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostEnvironmentAwareProcessReloadHandler;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostProcessReloadHandler extends HostEnvironmentAwareProcessReloadHandler {

    private static final AttributeDefinition RESTART_SERVERS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RESTART_SERVERS, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE).build();

    private static final AttributeDefinition USE_CURRENT_DOMAIN_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USE_CURRENT_DOMAIN_CONFIG, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.DOMAIN_CONFIG)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private static final AttributeDefinition USE_CURRENT_HOST_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USE_CURRENT_HOST_CONFIG, ModelType.BOOLEAN, true)
            .setAlternatives(ModelDescriptionConstants.HOST_CONFIG)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private static final AttributeDefinition HOST_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST_CONFIG, ModelType.STRING, true)
            .setAlternatives(ModelDescriptionConstants.USE_CURRENT_HOST_CONFIG)
            .build();

    private static final AttributeDefinition DOMAIN_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DOMAIN_CONFIG, ModelType.STRING, true)
            .setAlternatives(ModelDescriptionConstants.USE_CURRENT_DOMAIN_CONFIG)
            .build();


    private static final AttributeDefinition[] PRIMARY_HC_ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, RESTART_SERVERS, USE_CURRENT_DOMAIN_CONFIG, USE_CURRENT_HOST_CONFIG, DOMAIN_CONFIG, HOST_CONFIG};

    private static final AttributeDefinition[] SECONDARY_HC_ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, RESTART_SERVERS, USE_CURRENT_HOST_CONFIG, HOST_CONFIG};

    private static final AttributeDefinition[] ENHANCED_PRIMARY_HC_ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, USE_CURRENT_DOMAIN_CONFIG, USE_CURRENT_HOST_CONFIG, DOMAIN_CONFIG, HOST_CONFIG, STABILITY};

    private static final AttributeDefinition[] ENHANCED_SECONDARY_HC_ATTRIBUTES = new AttributeDefinition[] {ADMIN_ONLY, USE_CURRENT_HOST_CONFIG, HOST_CONFIG, STABILITY};

    private final HostControllerEnvironment environment;
    private final LocalHostControllerInfo hostControllerInfo;
    private final ProcessType processType;

    public static OperationDefinition getDefinition(final LocalHostControllerInfo hostControllerInfo) {
        return new DeferredParametersOperationDefinitionBuilder(hostControllerInfo, OPERATION_NAME, HostModelUtil.getResourceDescriptionResolver())
                .setParameters(hostControllerInfo.isMasterDomainController() ? PRIMARY_HC_ATTRIBUTES : SECONDARY_HC_ATTRIBUTES)
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
                .setRuntimeOnly()
                .build();
    }

    public static OperationDefinition getEnhancedDefinition(final LocalHostControllerInfo hostControllerInfo) {
        return new DeferredParametersOperationDefinitionBuilder(hostControllerInfo, ENHANCED_OPERATION_NAME, HostModelUtil.getResourceDescriptionResolver(), ENHANCED_PRIMARY_HC_ATTRIBUTES, ENHANCED_SECONDARY_HC_ATTRIBUTES, Stability.COMMUNITY)
                .setStability(Stability.COMMUNITY)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.RELOAD_ENHANCED)
                .setParameters(hostControllerInfo.isMasterDomainController() ? ENHANCED_PRIMARY_HC_ATTRIBUTES : ENHANCED_SECONDARY_HC_ATTRIBUTES)
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
                .setRuntimeOnly()
                .build();
    }

    public HostProcessReloadHandler(final ServiceName rootService, final HostRunningModeControl runningModeControl,
                                    final ControlledProcessState processState, final HostControllerEnvironment environment,
                                    final LocalHostControllerInfo hostControllerInfo) {
        super(rootService, runningModeControl, processState, environment);
        this.processType = environment.getProcessType();
        this.environment = environment;
        this.hostControllerInfo = hostControllerInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        //WFCORE-938 embedded HC reload requires --admin-only=true to be provided explicitly until we support it.
        if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER) {
            final boolean adminOnly = ADMIN_ONLY.resolveModelAttribute(context, operation).asBoolean(false);
            if (!adminOnly) {
                throw HostControllerLogger.ROOT_LOGGER.embeddedHostControllerRestartMustProvideAdminOnlyTrue();
            }
        }
        super.execute(context, operation);
    }

    @Override
    protected ProcessReloadHandler.ReloadContext<HostRunningModeControl> initializeReloadContext(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Stability stability;
        if (operation.hasDefined(STABILITY.getName())) {
            String val = STABILITY.resolveModelAttribute(context, operation).asString();
            stability = Stability.fromString(val);
            environment.checkStabilityIsValidForInstallation(stability);
        } else {
            stability = null;
        }
        final boolean adminOnly = ADMIN_ONLY.resolveModelAttribute(context, operation).asBoolean(false);
        final boolean restartServers = RESTART_SERVERS.resolveModelAttribute(context, operation).asBoolean(true);
        final boolean useCurrentHostConfig = USE_CURRENT_HOST_CONFIG.resolveModelAttribute(context, operation).asBoolean(true);
        final boolean useCurrentDomainConfig = hostControllerInfo.isMasterDomainController() && USE_CURRENT_DOMAIN_CONFIG.resolveModelAttribute(context, operation).asBoolean(true);
        final String domainConfig = hostControllerInfo.isMasterDomainController() && operation.hasDefined(DOMAIN_CONFIG.getName()) ? DOMAIN_CONFIG.resolveModelAttribute(context, operation).asString() : null;
        final String hostConfig = operation.hasDefined(HOST_CONFIG.getName()) ? HOST_CONFIG.resolveModelAttribute(context, operation).asString() : null;
        // we use the same name as the current one on a reload. If a host has been added with a specific name, it stays with that name until it is stopped and reloaded
        // from the persistent config.
        final String hostName = context.getCurrentAddress().getLastElement().getValue();
        if (operation.hasDefined(USE_CURRENT_DOMAIN_CONFIG.getName()) && domainConfig != null) {
            throw HostControllerLogger.ROOT_LOGGER.cannotBothHaveFalseUseCurrentDomainConfigAndDomainConfig();
        }
        if (operation.hasDefined(USE_CURRENT_HOST_CONFIG.getName()) && hostConfig != null) {
            throw HostControllerLogger.ROOT_LOGGER.cannotBothHaveFalseUseCurrentHostConfigAndHostConfig();
        }
        if (domainConfig != null && !environment.getDomainConfigurationFile().checkCanFindNewBootFile(domainConfig)) {
            throw HostControllerLogger.ROOT_LOGGER.domainConfigForReloadNotFound(domainConfig);
        }
        if (hostConfig != null && !environment.getHostConfigurationFile().checkCanFindNewBootFile(hostConfig)) {
            throw HostControllerLogger.ROOT_LOGGER.domainConfigForReloadNotFound(hostConfig);
        }

        return new ReloadContext<>() {

            @Override
            public void reloadInitiated(HostRunningModeControl runningModeControl) {
                runningModeControl.setRestartMode(restartServers ? RestartMode.SERVERS : RestartMode.HC_ONLY);
            }

            @Override
            public void doReload(HostRunningModeControl runningModeControl) {
                runningModeControl.setRunningMode(adminOnly ? RunningMode.ADMIN_ONLY : RunningMode.NORMAL);
                runningModeControl.setReloaded();
                runningModeControl.setUseCurrentConfig(useCurrentHostConfig);
                runningModeControl.setUseCurrentDomainConfig(useCurrentDomainConfig);
                runningModeControl.setNewDomainBootFileName(domainConfig);
                runningModeControl.setNewBootFileName(hostConfig);
                runningModeControl.setReloadHostName(hostName);
                if (stability != null) {
                    updateHostEnvironmentStability(stability);
                }
            }
        };
    }

    /**
     * The host controller info does not know if it is master or not until later in the booting process
     */
    private static class DeferredParametersOperationDefinitionBuilder extends SimpleOperationDefinitionBuilder {
        private final LocalHostControllerInfo hostControllerInfo;
        private final AttributeDefinition[] primaryHcAttr;
        private final AttributeDefinition[] secondaryHcAttr;
        private final Stability stability;

        public DeferredParametersOperationDefinitionBuilder(LocalHostControllerInfo hostControllerInfo, String name, ResourceDescriptionResolver resolver) {
            super(name, resolver);
            this.hostControllerInfo = hostControllerInfo;
            this.primaryHcAttr = PRIMARY_HC_ATTRIBUTES;
            this.secondaryHcAttr = SECONDARY_HC_ATTRIBUTES;
            this.stability = Stability.DEFAULT;
        }

        public DeferredParametersOperationDefinitionBuilder(LocalHostControllerInfo hostControllerInfo, String name, ResourceDescriptionResolver resolver, AttributeDefinition[] primaryHcAttr, AttributeDefinition[] secondaryHcAttr, Stability stability) {
            super(name, resolver);
            this.hostControllerInfo = hostControllerInfo;
            this.primaryHcAttr = primaryHcAttr;
            this.secondaryHcAttr = secondaryHcAttr;
            this.stability = stability;
        }

        @Override
        public SimpleOperationDefinition internalBuild(final ResourceDescriptionResolver resolver, final ResourceDescriptionResolver attributeResolver) {
            return new SimpleOperationDefinition(new SimpleOperationDefinitionBuilder(name, resolver)
                    .setStability(stability)
                    .setAttributeResolver(attributeResolver)
                    .setParameters(parameters)
                    .withFlags(flags)) {
                @Override
                public DescriptionProvider getDescriptionProvider() {
                    return new DescriptionProvider() {
                        @Override
                        public ModelNode getModelDescription(Locale locale) {
                            AttributeDefinition[] params = hostControllerInfo.isMasterDomainController() ? primaryHcAttr : secondaryHcAttr;
                            return new DefaultOperationDescriptionProvider(getName(), resolver, attributeResolver, replyType, replyValueType, replyAllowNull, deprecationData, replyParameters, params, accessConstraints, stability).getModelDescription(locale);
                        }
                    };
                }
            };
        }
    }
}
