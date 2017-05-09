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

import java.util.List;
import org.jboss.as.controller.AbstractRemoveStepHandler;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler to remove security realm definitions and remove the service.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SecurityRealmRemoveHandler extends AbstractRemoveStepHandler {

    public static final SecurityRealmRemoveHandler INSTANCE = new SecurityRealmRemoveHandler(SecurityRealmResourceDefinition.MANAGEMENT_SECURITY_REALM_CAPABILITY);

    private SecurityRealmRemoveHandler(RuntimeCapability ... capabilities) {
        super(capabilities);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performRuntime(context, operation, model);
        final boolean reloadRequired = ManagementUtil.isSecurityRealmReloadRequired(context, operation);
        if (reloadRequired) {
            context.reloadRequired();
        } else {
            removeServices(context, context.getCurrentAddressValue(), model);
        }
    }

    protected void removeServices(final OperationContext context, final String realmName, final ModelNode model) throws OperationFailedException {
        // KISS -- Just remove the service and all child services.
        ServiceName realmServiceName = SecurityRealmService.ServiceUtil.createServiceName(realmName);
        ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        List<ServiceName> allNames = serviceRegistry.getServiceNames();
        for (ServiceName current : allNames) {
            if (realmServiceName.isParentOf(current)) {
                context.removeService(current);
            }
        }
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.recoverServices(context, operation, model);
        try {
            SecurityRealmAddHandler.INSTANCE.installServices(context, context.getCurrentAddressValue(), model);
        } catch (OperationFailedException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToRecoverServices(e);
        }
    }
}
