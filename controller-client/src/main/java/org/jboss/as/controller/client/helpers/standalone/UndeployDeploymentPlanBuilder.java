/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers.standalone;

/**
 * Extension of {@link DeploymentPlanBuilder} that exposes
 * directives that are only applicable following an <code>undeploy</code> directive.
 *
 * @author Brian Stansberry
 */
public interface UndeployDeploymentPlanBuilder extends DeploymentPlanBuilder {

    /**
     * Indicates that deployment content that was undeployed via the preceding
     * <code>undeploy</code> action should be removed from the content repository.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    DeploymentPlanBuilder andRemoveUndeployed();

}
