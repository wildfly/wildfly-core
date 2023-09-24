/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerGroupAddHandler extends AbstractAddStepHandler {

    public static OperationStepHandler INSTANCE = new ServerGroupAddHandler();

    ServerGroupAddHandler() {
        super(ServerGroupResourceDefinition.ADD_ATTRIBUTES);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

}
