/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import static org.jboss.as.jmx.CommonAttributes.JMX;
import static org.jboss.as.jmx.CommonAttributes.REMOTING_CONNECTOR;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Stuart Douglas
 */
public class RemotingConnectorResource extends SimpleResourceDefinition {

    static final PathElement REMOTE_CONNECTOR_CONFIG_PATH = PathElement.pathElement(REMOTING_CONNECTOR, JMX);
    static final SimpleAttributeDefinition USE_MANAGEMENT_ENDPOINT
            = new SimpleAttributeDefinitionBuilder(CommonAttributes.USE_MANAGEMENT_ENDPOINT, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .build();

    static final String REMOTING_CAPABILITY = "org.wildfly.remoting.endpoint";
    static final RuntimeCapability<Void> REMOTE_JMX_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.management.jmx.remote")
            .addRequirements(JMXSubsystemRootResource.JMX_CAPABILITY_NAME)
            .build();

    static final RemotingConnectorResource INSTANCE = new RemotingConnectorResource();

    private RemotingConnectorResource() {
        super(new SimpleResourceDefinition.Parameters(REMOTE_CONNECTOR_CONFIG_PATH, JMXExtension.getResourceDescriptionResolver(CommonAttributes.REMOTING_CONNECTOR))
                .setAddHandler(RemotingConnectorAdd.INSTANCE)
                .setRemoveHandler(RemotingConnectorRemove.INSTANCE)
                .setCapabilities(REMOTE_JMX_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(USE_MANAGEMENT_ENDPOINT) {
            @Override
            protected void recordCapabilitiesAndRequirements(OperationContext context, AttributeDefinition attributeDefinition, ModelNode newValue, ModelNode oldValue) {
                super.recordCapabilitiesAndRequirements(context, attributeDefinition, newValue, oldValue);

                Boolean needRemoting = needRemoting(context, newValue);
                if (needRemoting != null) {
                    if (needRemoting) {
                        context.registerAdditionalCapabilityRequirement(REMOTING_CAPABILITY,
                                REMOTE_JMX_CAPABILITY.getName(),
                                USE_MANAGEMENT_ENDPOINT.getName());
                    } else {
                        context.deregisterCapabilityRequirement(REMOTING_CAPABILITY,
                                REMOTE_JMX_CAPABILITY.getName(),
                                USE_MANAGEMENT_ENDPOINT.getName());
                    }
                }
            }

            private Boolean needRemoting(OperationContext context, ModelNode attributeValue) {
                // Set up a fake model to resolve the USE_MANAGEMENT_ENDPOINT value
                ModelNode model = new ModelNode();
                model.get(USE_MANAGEMENT_ENDPOINT.getName()).set(attributeValue);
                try {
                    return !USE_MANAGEMENT_ENDPOINT.resolveModelAttribute(context, model).asBoolean();
                } catch (OperationFailedException ofe) {
                    if (model.get(USE_MANAGEMENT_ENDPOINT.getName()).getType() == ModelType.EXPRESSION) {
                        // Must be a vault expression or something we can't resolve in Stage.MODEL.
                        // So we can only do nothing and hope for the best when they reload
                        return null;
                    }
                    throw new IllegalStateException(ofe);
                }
            }
        };
        resourceRegistration.registerReadWriteAttribute(USE_MANAGEMENT_ENDPOINT, null, writeHandler);
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("org.jboss.remoting-jmx"));
    }
}
