/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * This class is private api
 * <p>
 * When r-r-d is called for a wildcard address, which is an alias, we want r-r-d to return the invoked address. However,
 * AliasStepHandler replaces the operation address with the real address, which in turn means that the r-r-d handler
 * never knows about the real address. This class contains two methods:
 * <ol>
 *     <li>{@code attachAliasAddress()} called by {@link AliasStepHandler} to attach the original/alias PathAddress</li>
 *     <li>{@code detachAliasAddress()} called by {@link org.jboss.as.controller.operations.global.GlobalOperationHandlers.RegistrationAddressResolver}
 *     (called by the r-r-d handler) to detach the original PathAddress</li>
 * </ol>
 * This ensures that the r-r-d handler gets access to the original/alias PathAddress. The removal of the attachment ensures not
 * that subsequent steps in the OC do not get access to it. Since the attach and detach only take effect for
 * {@code :read-resource-description} we avoid the risk of a call to another operation at an alias address pollutes a
 * subsequent {@code :read-resource-description} call at a 'real' address.
 *
 * @author Kabir Khan
 */
public class WildcardReadResourceDescriptionAddressHack {

    private static final OperationContext.AttachmentKey<PathAddress> ALIAS_ORIGINAL_ADDRESS =
            OperationContext.AttachmentKey.create(PathAddress.class);

    /**
     *
     * @param context
     * @param operation
     */
    static void attachAliasAddress(OperationContext context, ModelNode operation) {
        if (operation.get(OP).asString().equals(READ_RESOURCE_DESCRIPTION_OPERATION)) {
            context.attach(ALIAS_ORIGINAL_ADDRESS, PathAddress.pathAddress(operation.get(OP_ADDR)));
        }
    }

    public static PathAddress detachAliasAddress(OperationContext context, ModelNode operation) {
        if (operation.get(OP).asString().equals(READ_RESOURCE_DESCRIPTION_OPERATION)) {
            return context.detach(ALIAS_ORIGINAL_ADDRESS);
        }
        return null;
    }
}
