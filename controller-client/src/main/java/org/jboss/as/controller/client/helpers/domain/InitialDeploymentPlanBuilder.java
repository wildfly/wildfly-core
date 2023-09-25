/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.concurrent.TimeUnit;


/**
 * Variant of a {@link DeploymentPlanBuilder} that is meant
 * to be used at the initial stages of the building process, when directives that
 * pertain to the entire plan can be applied.
 *
 * @author Brian Stansberry
 */
public interface InitialDeploymentPlanBuilder extends InitialDeploymentSetBuilder {

    /**
     * Indicates that the actions in the plan need to be rolled back across any single
     * given server group, then it should be rolled back across all server groups.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    InitialDeploymentSetBuilder withRollbackAcrossGroups();

    /**
     * Indicates that on a given server all <code>deploy</code>, <code>undeploy</code> or
     * <code>replace</code> operations associated with the deployment set
     * should <strong>not</strong> be rolled back in case of a failure in any of them.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    @Override
    InitialDeploymentSetBuilder withoutSingleServerRollback();

    /**
     * Indicates actions specified subsequent to this call should be organized
     * around a full graceful server shutdown and restart. The server will attempt to shut itself
     * down gracefully, waiting for in-process work to complete before shutting
     * down. See the full JBoss AS documentation for details on what "waiting for
     * in-process work to complete" means.
     *
     * <p>For any <code>deploy</code> or <code>replace</code>
     * actions, the new content will not be deployed until the server is restarted.
     * For any <code>undeploy</code> or <code>replace</code> actions, the old content
     * will be undeployed as part of normal server shutdown processing.</p>
     *
     * @param timeout maximum amount of time the graceful shutdown should wait for
     *                existing work to complete before completing the shutdown
     * @param timeUnit {@link TimeUnit} in which <code>timeout</code> is expressed
     * @return a builder that can continue building the overall deployment plan
     */
    @Override
    InitialDeploymentSetBuilder withGracefulShutdown(long timeout, TimeUnit timeUnit);

    /**
     * Indicates actions specified subsequent to this call should be organized
     * around a full server restart. For any <code>deploy</code> or <code>replace</code>
     * actions, the new content will not be deployed until the server is restarted.
     * For any <code>undeploy</code> or <code>replace</code> actions, the old content
     * will be undeployed as part of normal server shutdown processing.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    @Override
    InitialDeploymentSetBuilder withShutdown();

}
