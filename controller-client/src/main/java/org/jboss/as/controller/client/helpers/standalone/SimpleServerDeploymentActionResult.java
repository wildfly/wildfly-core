/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import java.util.UUID;


/**
 * Default implementation of {@link ServerDeploymentActionResult}.
 *
 * @author Brian Stansberry
 */
public class SimpleServerDeploymentActionResult
extends AbstractServerUpdateActionResult<ServerDeploymentActionResult>
implements ServerDeploymentActionResult {

    private static final long serialVersionUID = 1075795087623522316L;

    public SimpleServerDeploymentActionResult(UUID id, Result result) {
        this(id, result, null);
    }

    public SimpleServerDeploymentActionResult(UUID id, Throwable deploymentException) {
        this(id, Result.FAILED, deploymentException);
    }

    public SimpleServerDeploymentActionResult(UUID id, Result result, Throwable deploymentException) {
        super(id, result, deploymentException);
    }

    @Override
    protected Class<ServerDeploymentActionResult> getRollbackResultClass() {
        return ServerDeploymentActionResult.class;
    }

}
