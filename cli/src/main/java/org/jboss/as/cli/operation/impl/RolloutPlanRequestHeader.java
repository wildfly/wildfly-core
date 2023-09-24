/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.operation.OperationRequestHeader;

/**
 *
 * @author Alexey Loubyansky
 */
public class RolloutPlanRequestHeader implements OperationRequestHeader {

    public static final RolloutPlanRequestHeader INSTANCE = new RolloutPlanRequestHeader();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.OperationRequestHeader#getName()
     */
    @Override
    public String getName() {
        return "rollout";
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.OperationRequestHeader#getCompleter()
     */
    @Override
    public CommandLineCompleter getCompleter() {
        return RolloutPlanCompleter.INSTANCE;
    }
}
