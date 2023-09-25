/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.operations.DomainIncludesValidationWriteAttributeHandler;
import org.jboss.as.domain.controller.operations.GenericModelDescribeOperationHandler;
import org.jboss.as.domain.controller.operations.ProfileAddHandler;
import org.jboss.as.domain.controller.operations.ProfileCloneHandler;
import org.jboss.as.domain.controller.operations.ProfileDescribeHandler;
import org.jboss.as.domain.controller.operations.ProfileModelDescribeHandler;
import org.jboss.as.domain.controller.operations.ProfileRemoveHandler;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProfileResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH = PathElement.pathElement(PROFILE);

    static final String PROFILE_CAPABILITY_NAME = "org.wildfly.domain.profile";
    public static final RuntimeCapability<Void> PROFILE_CAPABILITY = RuntimeCapability.Builder.of(PROFILE_CAPABILITY_NAME, true)
            .build();

    private static OperationDefinition DESCRIBE = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.DESCRIBE, DomainResolver.getResolver(PROFILE, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .withFlag(OperationEntry.Flag.HIDDEN)
            .setReadOnly()
            .build();

    //This attribute exists in 7.1.2 and 7.1.3 but was always nillable
    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
            .setValidator(new StringLengthValidator(1, true))
            .setRequired(false)
            .setResourceOnly()
            .build();

    public static final ListAttributeDefinition INCLUDES = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.INCLUDES)
            .setRequired(false)
            .setElementValidator(new StringLengthValidator(1, true))
            .setCapabilityReference(PROFILE_CAPABILITY_NAME, PROFILE_CAPABILITY_NAME)
            .build();

    public static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {INCLUDES};

    private final LocalHostControllerInfo hostInfo;

    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;


    public ProfileResourceDefinition(LocalHostControllerInfo hostInfo, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry) {
        super(new SimpleResourceDefinition.Parameters(PATH, DomainResolver.getResolver(PROFILE, false))
                .setAddHandler(ProfileAddHandler.INSTANCE)
                .setRemoveHandler(ProfileRemoveHandler.INSTANCE)
                .addCapabilities(PROFILE_CAPABILITY)
        );
        this.hostInfo = hostInfo;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DESCRIBE, ProfileDescribeHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(ProfileCloneHandler.DEFINITION, new ProfileCloneHandler(hostInfo, ignoredDomainResourceRegistry));
        resourceRegistration.registerOperationHandler(GenericModelDescribeOperationHandler.DEFINITION, ProfileModelDescribeHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(INCLUDES, null, createIncludesValidationHandler());
    }

    public static OperationStepHandler createIncludesValidationHandler() {
        return new DomainIncludesValidationWriteAttributeHandler(INCLUDES);
    }
}
