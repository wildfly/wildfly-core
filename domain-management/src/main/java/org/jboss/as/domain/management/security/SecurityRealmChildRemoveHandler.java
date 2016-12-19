/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security;

import java.util.Set;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Remove handler for a child resource of a management security realm.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SecurityRealmChildRemoveHandler extends SecurityRealmParentRestartHandler {

    private final boolean validateAuthentication;

    public SecurityRealmChildRemoveHandler(boolean validateAuthentication) {
        this(validateAuthentication, null);
    }

    public SecurityRealmChildRemoveHandler(boolean validateAuthentication, RuntimeCapability... capabilities) {
        super(capabilities);
        this.validateAuthentication = validateAuthentication;
    }

    @Override
    protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
        return false;
    }

    @Override
    protected void updateModel(OperationContext context, ModelNode operation) throws OperationFailedException {
        // verify that the resource exist before removing it
        context.readResource(PathAddress.EMPTY_ADDRESS, false);
        Resource resource = context.removeResource(PathAddress.EMPTY_ADDRESS);
        recordCapabilitiesAndRequirements(context, operation, resource);

        if (validateAuthentication && !context.isBooting()) {
            ModelNode validationOp = AuthenticationValidatingHandler.createOperation(operation);
            context.addStep(validationOp, AuthenticationValidatingHandler.INSTANCE, OperationContext.Stage.MODEL);
        } // else we know the SecurityRealmAddHandler is part of this overall set of ops and it added AuthenticationValidatingHandler
    }

    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        Set<RuntimeCapability> capabilitySet = capabilities.isEmpty() ? context.getResourceRegistration().getCapabilities() : capabilities;
        for (RuntimeCapability capability : capabilitySet) {
            if (capability.isDynamicallyNamed()) {
                context.deregisterCapability(capability.getDynamicName(context.getCurrentAddress()));
            } else {
                context.deregisterCapability(capability.getName());
            }
        }
        ModelNode model = resource.getModel();
        ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
        for (String attr : mrr.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
            AttributeAccess aa = mrr.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
            if (aa != null) {
                AttributeDefinition ad = aa.getAttributeDefinition();
                if (ad != null && (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements())) {
                    ad.removeCapabilityRequirements(context, resource, model.get(ad.getName()));
                }
            }
        }
    }
}
