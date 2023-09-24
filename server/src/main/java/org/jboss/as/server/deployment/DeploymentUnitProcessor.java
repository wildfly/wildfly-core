/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

/**
 * A deployment processor.  Instances of this interface represent a step in the deployer chain.  They may perform
 * a variety of tasks, including (but not limited to):
 * <ol>
 * <li>Parsing a deployment descriptor and adding it to the context</li>
 * <li>Reading a deployment descriptor's data and using it to produce deployment items</li>
 * <li>Replacing a deployment descriptor with a transformed version of that descriptor</li>
 * <li>Removing a deployment descriptor to prevent it from being processed</li>
 * </ol>
 *
 *
 */
public interface DeploymentUnitProcessor {

    /**
     * Perform a single step in processing the deployment phase.  The resulting state after executing this method
     * should be that either the method completes normally and all changes are made, or an exception is thrown
     * and all changes made in this method are reverted such that the original pre-invocation state is restored.
     * <p>
     * Data stored on the phase context only exists until the end of the phase.  The deployment unit context
     * which is persistent is available via {@code context.getDeploymentUnitContext()}.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException if an error occurs during processing
     */
    void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException;

    /**
     * Undo the deployment processing.  This method should undo any action taken by {@code deploy()}; however, if
     * the {@code deploy()} method added services, they need not be removed here (they will automatically be removed).
     * <p>This method should avoid throwing exceptions; any exceptions thrown are logged and ignored.  Implementations of this
     * method cannot assume that the deployment process has (or has not) proceeded beyond the current processor, nor can they
     * assume that the {@code undeploy()} method will be called from the same thread as the {@code deploy()} method.
     *
     * @param context the deployment unit context
     */
    default void undeploy(DeploymentUnit context) {
        // no-op
    }
}
