/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Reports the process type
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ProcessTypeHandler implements OperationStepHandler {

    public static final ProcessTypeHandler MASTER = new ProcessTypeHandler(true);
    public static final ProcessTypeHandler SLAVE = new ProcessTypeHandler(false);

    public static final String DOMAIN_CONTROLLER_TYPE = "Domain Controller";
    public static final String HOST_CONTROLLER_TYPE = "Host Controller";

    private final boolean master;
    private ProcessTypeHandler(final boolean master) {
        this.master = master;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set(master ? DOMAIN_CONTROLLER_TYPE : HOST_CONTROLLER_TYPE);
    }
}
