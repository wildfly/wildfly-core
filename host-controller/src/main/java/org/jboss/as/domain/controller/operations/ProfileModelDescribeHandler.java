/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * This profile describe handler branches off to the actual subsystem describe operations, however keeping the addresses
 * consistent with the generic model describe handler.
 *
 * @author Emanuel Muckenhuber
 */
public class ProfileModelDescribeHandler extends GenericModelDescribeOperationHandler {

    public static final ProfileModelDescribeHandler INSTANCE = new ProfileModelDescribeHandler();

    ProfileModelDescribeHandler() {
        super(ModelDescriptionConstants.DESCRIBE, false);
    }

    @Override
    protected void addChildOperation(PathAddress parent, List<ModelNode> operations, ModelNode results) {
        assert parent.size() == 1;
        assert parent.getLastElement().getKey().equals(PROFILE);
        for (final ModelNode operation : operations) {
            final PathAddress child = PathAddress.pathAddress(operation.require(OP_ADDR));
            assert child.getElement(0).getKey().equals(SUBSYSTEM);
            operation.get(OP_ADDR).set(parent.append(child).toModelNode());
            results.add(operation);
        }
    }

}
