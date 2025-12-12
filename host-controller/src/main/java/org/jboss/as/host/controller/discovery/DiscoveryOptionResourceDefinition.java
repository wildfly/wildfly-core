/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.discovery;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTION;
import static org.jboss.as.host.controller.discovery.Constants.DEFAULT_MODULE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.operations.DiscoveryOptionAddHandler;
import org.jboss.as.host.controller.operations.DiscoveryOptionRemoveHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing a generic discovery option.
 *
 * @author Farah Juma
 */
public class DiscoveryOptionResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition CODE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CODE, ModelType.STRING)
        .setValidator(new StringLengthValidator(1))
        .setStorageRuntime()
        .setRuntimeServiceNotRequired()
        .build();

    public static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MODULE, ModelType.STRING, true)
        .setDefaultValue(new ModelNode(DEFAULT_MODULE))
        .setValidator(new StringLengthValidator(1))
        .setCorrector(ModuleIdentifierUtil.MODULE_NAME_CORRECTOR)
        .setStorageRuntime()
        .setRuntimeServiceNotRequired()
        .build();

    public static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder(ModelDescriptionConstants.PROPERTIES, true)
        .addFlag(Flag.RESTART_ALL_SERVICES)
        .setCorrector(MapAttributeDefinition.LIST_TO_MAP_CORRECTOR)
        .setAllowExpression(true)
        .setStorageRuntime()
        .setRuntimeServiceNotRequired()
        .build();

    public static final AttributeDefinition[] DISCOVERY_ATTRIBUTES = new AttributeDefinition[] {CODE, MODULE, PROPERTIES};

    public DiscoveryOptionResourceDefinition(final LocalHostControllerInfoImpl hostControllerInfo) {
        super(new Parameters(PathElement.pathElement(DISCOVERY_OPTION), HostResolver.getResolver(DISCOVERY_OPTION))
                .setAddHandler(new DiscoveryOptionAddHandler(hostControllerInfo))
                .setRemoveHandler(new DiscoveryOptionRemoveHandler())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        for (final AttributeDefinition attribute : DISCOVERY_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }
}
