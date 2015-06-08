/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
