/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.model.test.ModelTestKernelServices;
import org.jboss.dmr.ModelNode;


/**
 * Allows access to the service container and the model controller
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface KernelServices extends ModelTestKernelServices<KernelServices> {

    Class<?> getTestClass();

    /**
     * Transforms the model to the legacy subsystem model version
     * @param modelVersion the target legacy subsystem model version
     * @return the transformed model
     * @throws IllegalStateException if this is not the test's main model controller
     */
    ModelNode readTransformedModel(ModelVersion modelVersion);

    /**
     * Transforms the model to the legacy subsystem model version
     * @param modelVersion the target legacy subsystem model version
     * @param includeDefaults {@code true} if default values for undefined attributes should be included
     * @return the transformed model
     * @throws IllegalStateException if this is not the test's main model controller
     */
    ModelNode readTransformedModel(ModelVersion modelVersion, boolean includeDefaults);

    /**
     * Execute an operation in the  controller containg the passed in version of the subsystem.
     * The operation and results will be translated from the format for the main controller to the
     * legacy controller's format.
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param op the operation for the main controller
     * @throws IllegalStateException if this is not the test's main model controller or if there is no legacy controller containing the version of the subsystem
     */
    ModelNode executeOperation(final ModelVersion modelVersion, final TransformedOperation op);


    /**
     * Execute an operation in the main controller, and get hold of the transformed operation. This
     * is useful for testing cases when the transformer needs to check the current state of the model.
     * Note that if the transformation ends up rejecting the operation, the main controller will not be rolled back
     * to its previous state.
     *
     * @param op the operation to transform
     * @param modelVersion  the subsystem model version of the legacy subsystem model controller
     * @return the transformed operation
     */
    TransformedOperation executeInMainAndGetTheTransformedOperation(ModelNode op, ModelVersion modelVersion);
}
