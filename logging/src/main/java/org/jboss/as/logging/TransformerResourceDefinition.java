package org.jboss.as.logging;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;

/**
 * Adds the ability to register transformers in a resource definition.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class TransformerResourceDefinition extends SimpleResourceDefinition {

    protected TransformerResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver) {
        super(new Parameters(pathElement, descriptionResolver));
    }

    protected TransformerResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver, final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        super(new Parameters(pathElement, descriptionResolver).setAddHandler(addHandler).setRemoveHandler(removeHandler));
    }

    protected TransformerResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver, final OperationStepHandler addHandler, final OperationStepHandler removeHandler, final Flag addRestartLevel, final Flag removeRestartLevel) {
        super(new Parameters(pathElement, descriptionResolver)
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setAddRestartLevel(addRestartLevel)
                .setRemoveRestartLevel(removeRestartLevel));
    }

    protected TransformerResourceDefinition(final Parameters parameters) {
        super(parameters);
    }
    /**
     * Register the transformers for the resource.
     *
     * @param modelVersion          the model version we're registering
     * @param rootResourceBuilder   the builder for the root resource
     * @param loggingProfileBuilder the builder for the logging profile, {@code null} if the profile was rejected
     */
    public abstract void registerTransformers(KnownModelVersion modelVersion,
                                              ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                              ResourceTransformationDescriptionBuilder loggingProfileBuilder);
}
