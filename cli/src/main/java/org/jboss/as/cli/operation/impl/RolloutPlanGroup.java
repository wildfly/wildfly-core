/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.dmr.ModelNode;

/**
 * Represents a group in the rollout plan
 *
 * @author Alexey Loubyansky
 */
public interface RolloutPlanGroup {

    void addTo(ModelNode inSeries) throws CommandFormatException;

    ModelNode toModelNode() throws CommandFormatException;
}
