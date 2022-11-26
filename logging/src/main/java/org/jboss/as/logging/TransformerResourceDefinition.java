package org.jboss.as.logging;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.wildfly.common.Assert;

/**
 * Handles resource transformation registration for a management resource definition.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class TransformerResourceDefinition {

    private final PathElement pathElement;

    /**
     * Creates a new transformer resource definition.
     *
     * @param pathElement the path element of the resource. Cannot be {@code null}
     */
    protected TransformerResourceDefinition(final PathElement pathElement) {
        Assert.checkNotNullParam("pathElement", pathElement);
        this.pathElement = pathElement;
    }

    /**
     * Gets the final element of the resource's address.
     *
     * @return the path element of the resource. Will not be {@code null}
     */
    public final PathElement getPathElement() {
        return pathElement;
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
