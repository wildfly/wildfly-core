/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.model.jvm;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * {@code OperationHandler} for the jvm resource remove operation.
 *
 * @author Emanuel Muckenhuber
 */
public final class JVMRemoveHandler extends AbstractRemoveStepHandler {

    public static final String OPERATION_NAME = REMOVE;
    public static final JVMRemoveHandler INSTANCE = new JVMRemoveHandler();


    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
