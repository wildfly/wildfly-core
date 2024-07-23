/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerGroupAddHandler extends AbstractAddStepHandler {

    public static final OperationStepHandler INSTANCE = new ServerGroupAddHandler();

    private ServerGroupAddHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

}
