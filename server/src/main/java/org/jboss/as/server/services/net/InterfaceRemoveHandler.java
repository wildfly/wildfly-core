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

package org.jboss.as.server.services.net;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * TODO remove this once we can get the superclass out of the controller module to a place
 * where the NetworkInterface class is visible.
 *
 * @author Brian Stansberry
 */
public class InterfaceRemoveHandler extends AbstractRemoveStepHandler {

    public static final InterfaceRemoveHandler INSTANCE = new InterfaceRemoveHandler();

    /**
     * Create the InterfaceRemoveHandler
     */
    InterfaceRemoveHandler() {
        // Pass the capability through the OSH constructor rather than relying
        // on the MRR, as in some subsystem test stuff due to restrictions for
        // legacy controllers the MRR can't record the cap
        super(InterfaceResourceDefinition.INTERFACE_CAPABILITY);
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);
        context.deregisterCapability(InterfaceResourceDefinition.INTERFACE_CAPABILITY.getDynamicName(context.getCurrentAddressValue()));
    }


}
