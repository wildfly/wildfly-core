/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

/**
 * Add the threads subsystem.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ThreadsSubsystemAdd extends AbstractAddStepHandler {

    static final ThreadsSubsystemAdd INSTANCE = new ThreadsSubsystemAdd();

    protected void populateModel(ModelNode operation, ModelNode model) {
    }

    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
