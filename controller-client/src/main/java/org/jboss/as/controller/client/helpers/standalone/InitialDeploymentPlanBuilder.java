/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers.standalone;

import java.util.concurrent.TimeUnit;

/**
 * Extension of {@link DeploymentPlanBuilder} that exposes additional methods meant
 * to be used at the initial stages of the building process, when directives that
 * pertain to the entire plan can be applied.
 *
 * @author Brian Stansberry
 */
public interface InitialDeploymentPlanBuilder extends DeploymentPlanBuilder {

    /**
     * Indicates all <code>deploy</code>, <code>undeploy</code>, <code>replace</code>
     * or <code>remove</code> operations associated with the deployment plan
     * should <strong>not</strong> be rolled back in case of a failure in any of them.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    DeploymentPlanBuilder withoutRollback();

    /**
     * Indicates actions specified subsequent to this call should be organized
     * around a full graceful server shutdown. The server will attempt to shut itself
     * down gracefully, waiting for in-process work to complete before shutting
     * down. See the full JBoss AS documentation for details on what "waiting for
     * in-process work to complete" means.
     *
     * <p>For any <code>deploy</code> or <code>replace</code>
     * actions, the new content will not be deployed until the server is restarted.
     * For any <code>undeploy</code> or <code>replace</code> actions, the old content
     * will be undeployed as part of normal server shutdown processing.</p>
     * <p>
     * <strong>NOTE: In order to organize a deployment plan around a server
     * shutdown, {@link #withoutRollback() rollback of the plan must be disabled}.
     * A rollback is not compatible with a server restart.</strong>
     * </p>
     * <p>
     * <strong>NOTE:</strong> A Standalone JBoss AS instance is not able to restart
     * itself; it can only shut itself down, and requires external action to
     * start again.
     * </p>
     *
     * @param timeout maximum amount of time the graceful shutdown should wait for
     *                existing work to complete before completing the shutdown
     * @param timeUnit {@link TimeUnit} in which <code>timeout</code> is expressed
     * @return a builder that can continue building the overall deployment plan
     */
    DeploymentPlanBuilder withGracefulShutdown(long timeout, TimeUnit timeUnit);

    /**
     * Indicates actions specified subsequent to this call should be organized
     * around a full server shutdown. For any <code>deploy</code> or <code>replace</code>
     * actions, the new content will not be deployed until the server is restarted.
     * For any <code>undeploy</code> or <code>replace</code> actions, the old content
     * will be undeployed as part of normal server shutdown processing.
     * <p>
     * <strong>NOTE: In order to organize a deployment plan around a server
     * shutdown, {@link #withoutRollback() rollback of the plan must be disabled}.
     * A rollback is not compatible with a server restart.</strong>
     * </p>
     * <p>
     * <strong>NOTE:</strong> A Standalone JBoss AS instance is not able to restart
     * itself; it can only shut itself down, and requires external action to
     * start again.
     * </p>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    DeploymentPlanBuilder withShutdown();

}
