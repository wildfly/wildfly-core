/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import java.util.UUID;



/**
 * Default implementation of {@link ServerUpdateActionResult}.
 *
 * @author Brian Stansberry
 */
public class SimpleServerUpdateActionResult
    extends AbstractServerUpdateActionResult<ServerUpdateActionResult> {

    private static final long serialVersionUID = 6337992911667021814L;

    public SimpleServerUpdateActionResult(UUID id, Result result) {
        this(id, result, null);
    }

    public SimpleServerUpdateActionResult(UUID id, Exception deploymentException) {
        this(id, Result.FAILED, deploymentException);
    }

    public SimpleServerUpdateActionResult(UUID id, Result result, Exception deploymentException) {
        super(id, result, deploymentException);
    }

    @Override
    protected Class<ServerUpdateActionResult> getRollbackResultClass() {
        return ServerUpdateActionResult.class;
    }

}
