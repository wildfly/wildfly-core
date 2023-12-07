/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.stability;

import java.util.EnumSet;
import java.util.stream.Collectors;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * @author Paul Ferraro
 */
public class BarResourceDefinition extends SimpleResourceDefinition {
    static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PathElement.pathElement("bar"), Stability.PREVIEW);

    static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type", ModelType.STRING)
            .build();

    enum Values implements Feature {
        FOO,
        BAR,
        EXPERIMENTAL(Stability.EXPERIMENTAL), // Experimental value
        ;
        private final Stability stability;

        Values() {
            this(REGISTRATION.getStability());
        }

        Values(Stability stability) {
            this.stability = stability;
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }
    }

    BarResourceDefinition() {
        super(new Parameters(REGISTRATION, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        ParameterValidator validator = new EnumValidator<>(Values.class, EnumSet.allOf(Values.class).stream().filter(registration::enables).collect(Collectors.toUnmodifiableSet()));
        registration.registerReadWriteAttribute(new SimpleAttributeDefinitionBuilder(TYPE).setValidator(validator).build(), null, ModelOnlyWriteAttributeHandler.INSTANCE);
    }
}
