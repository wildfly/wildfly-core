/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * @author Emanuel Muckenhuber
 */
public class ProfileRemoveHandler extends AbstractRemoveStepHandler {

    public static final ProfileRemoveHandler INSTANCE = new ProfileRemoveHandler();

    private ProfileRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
