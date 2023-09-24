/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.dmr.ModelNode;

/**
 * Handler to read the UUID of the instance.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class InstanceUuidReadHandler implements OperationStepHandler {
    private final ProcessEnvironment environment;

    public InstanceUuidReadHandler(final ProcessEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set(environment.getInstanceUuid().toString());
    }
}
