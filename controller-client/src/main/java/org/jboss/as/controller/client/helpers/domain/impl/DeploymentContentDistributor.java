/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.InputStream;
import java.io.IOException;

import org.jboss.as.controller.client.helpers.domain.DuplicateDeploymentNameException;


/**
 * Object capable of distributing distributing new deployment content from a
 * client to a DomainController.
 *
 * @author Brian Stansberry
 */
public interface DeploymentContentDistributor {

    byte[] distributeDeploymentContent(String uniqueName, String runtimeName, InputStream stream) throws IOException, DuplicateDeploymentNameException;

    byte[] distributeReplacementDeploymentContent(String uniqueName, String runtimeName, InputStream stream) throws IOException;
}
