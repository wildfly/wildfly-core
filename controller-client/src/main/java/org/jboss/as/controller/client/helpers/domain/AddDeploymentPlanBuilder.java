/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Variant of a {@link DeploymentPlanBuilder} that exposes
 * directives that are only applicable following an <code>add</code> directive.
 *
 * @author Brian Stansberry
 */
public interface AddDeploymentPlanBuilder extends DeploymentActionsCompleteBuilder {

    /**
     * Indicates content that was added via an immediately preceding
     * <code>add</code> operation should be deployed.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    DeployDeploymentPlanBuilder andDeploy();

    /**
     * Indicates content that was added via an immediately preceding
     * <code>add</code> operation should be deployed, replacing the specified
     * existing content in the runtime.
     *
     * @param toReplace identifier of the existing deployment content that is to be replaced
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ReplaceDeploymentPlanBuilder andReplace(String toReplace);
}
