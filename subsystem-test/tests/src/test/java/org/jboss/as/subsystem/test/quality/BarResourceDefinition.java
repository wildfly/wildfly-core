/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.quality;

import java.util.EnumSet;
import java.util.stream.Collectors;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Quality;
import org.jboss.dmr.ModelType;

/**
 * @author Paul Ferraro
 */
public class BarResourceDefinition extends SimpleResourceDefinition {
    static final PathElement PATH = PathElement.pathElement("bar", Quality.PREVIEW);

    static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type", ModelType.STRING)
            .build();

    enum Values implements Feature {
        FOO,
        BAR,
        EXPERIMENTAL(Quality.EXPERIMENTAL), // Experimental value
        ;
        private final Quality quality;

        Values() {
            this(PATH.getQuality());
        }

        Values(Quality quality) {
            this.quality = quality;
        }

        @Override
        public Quality getQuality() {
            return this.quality;
        }
    }

    BarResourceDefinition() {
        super(new Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        ParameterValidator validator = new EnumValidator<>(Values.class, EnumSet.allOf(Values.class).stream().filter(registration::enables).collect(Collectors.toUnmodifiableSet()));
        registration.registerReadWriteAttribute(new SimpleAttributeDefinitionBuilder(TYPE).setValidator(validator).build(), null, ModelOnlyWriteAttributeHandler.INSTANCE);
    }
}
