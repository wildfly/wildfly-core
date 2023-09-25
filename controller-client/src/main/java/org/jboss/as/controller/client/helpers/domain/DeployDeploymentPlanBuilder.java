/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Variant of a {@link DeploymentPlanBuilder} that exposes
 * directives that are only applicable following a <code>deploy</code>
 * or <code>replace</code> directive.
 *
 * @author Brian Stansberry
 */
public interface DeployDeploymentPlanBuilder extends DeploymentActionsCompleteBuilder {

}
