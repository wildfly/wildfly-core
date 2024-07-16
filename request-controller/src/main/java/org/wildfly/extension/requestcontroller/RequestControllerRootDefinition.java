/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Stuart Douglas
 */
class RequestControllerRootDefinition extends PersistentResourceDefinition {

    static final String REQUEST_CONTROLLER_CAPABILITY_NAME = "org.wildfly.request-controller";

    public static final SimpleAttributeDefinition MAX_REQUESTS = SimpleAttributeDefinitionBuilder.create(Constants.MAX_REQUESTS, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(-1))
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition TRACK_INDIVIDUAL_ENDPOINTS = SimpleAttributeDefinitionBuilder.create(Constants.TRACK_INDIVIDUAL_ENDPOINTS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition ACTIVE_REQUESTS = SimpleAttributeDefinitionBuilder.create(Constants.ACTIVE_REQUESTS, ModelType.INT, true)
            .setStorageRuntime()
            .build();
    public static final RequestControllerRootDefinition INSTANCE = new RequestControllerRootDefinition(true);

    static final RuntimeCapability<Void> REQUEST_CONTROLLER_CAPABILITY =
            RuntimeCapability.Builder.of(REQUEST_CONTROLLER_CAPABILITY_NAME, false, RequestController.class)
                    .build();

    private final boolean registerRuntimeOnly;

    RequestControllerRootDefinition(boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(RequestControllerExtension.SUBSYSTEM_PATH, RequestControllerExtension.getResolver())
                .setAddHandler(new RequestControllerSubsystemAdd())
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addCapabilities(REQUEST_CONTROLLER_CAPABILITY));
        this.registerRuntimeOnly = registerRuntimeOnly;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return getAttributeDefinitions(registerRuntimeOnly);
    }

    private static Collection<AttributeDefinition> getAttributeDefinitions(boolean registerRuntimeOnly) {
        if(registerRuntimeOnly) {
            return Arrays.asList(new AttributeDefinition[]{MAX_REQUESTS, TRACK_INDIVIDUAL_ENDPOINTS, ACTIVE_REQUESTS});
        } else {
            return Arrays.asList(new AttributeDefinition[]{MAX_REQUESTS, TRACK_INDIVIDUAL_ENDPOINTS});
        }
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        MaxRequestsWriteHandler handler = new MaxRequestsWriteHandler(MAX_REQUESTS);
        resourceRegistration.registerReadWriteAttribute(MAX_REQUESTS, null, handler);
        resourceRegistration.registerReadWriteAttribute(TRACK_INDIVIDUAL_ENDPOINTS, null, new ReloadRequiredWriteAttributeHandler(TRACK_INDIVIDUAL_ENDPOINTS));
        if(registerRuntimeOnly) {
            resourceRegistration.registerMetric(ACTIVE_REQUESTS, new ActiveRequestsReadHandler());
        }
    }
}
