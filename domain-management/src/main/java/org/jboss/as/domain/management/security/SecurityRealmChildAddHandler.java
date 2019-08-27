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

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.jboss.as.domain.management.security.SecretServerIdentityResourceDefinition.CREDENTIAL_REFERENCE;

import java.util.Collections;
import java.util.Set;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Add handler for a child resource of a management security realm.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SecurityRealmChildAddHandler extends SecurityRealmParentRestartHandler {

    private final boolean validateAuthentication;
    private final boolean validateAuthorization;
    private final AttributeDefinition[] attributeDefinitions;
    private final Set<RuntimeCapability> capabilities;

    public SecurityRealmChildAddHandler(boolean validateAuthentication, boolean validateAuthorization, AttributeDefinition... attributeDefinitions) {
        this(validateAuthentication, validateAuthorization, Collections.emptySet(), attributeDefinitions);
    }

    public SecurityRealmChildAddHandler( boolean validateAuthentication, boolean validateAuthorization, Set<RuntimeCapability> capabilities, AttributeDefinition... attributeDefinitions) {
        this.capabilities = capabilities == null ? Collections.emptySet() : capabilities;
        this.validateAuthentication = validateAuthentication;
        this.validateAuthorization = validateAuthorization;
        this.attributeDefinitions = attributeDefinitions == null ? new AttributeDefinition[0] : attributeDefinitions;
    }

    @Override
    protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
        return false;
    }

    @Override
    protected void updateModel(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        for (AttributeDefinition attr : attributeDefinitions) {
            attr.validateAndSet(operation, model);
            if (attr.getName().equals(CredentialReference.CREDENTIAL_REFERENCE) ||
                    attr.getName().equals(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) ||
                    attr.getName().equals(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                handleCredentialReferenceUpdate(context, model.get(attr.getName()), attr.getName());
            }
        }

        if (!context.isBooting()) {
            if (validateAuthentication) {
                ModelNode validationOp = AuthenticationValidatingHandler.createOperation(operation);
                context.addStep(validationOp, AuthenticationValidatingHandler.INSTANCE, OperationContext.Stage.MODEL);
            }
            if (validateAuthorization) {
                ModelNode validationOp = AuthorizationValidatingHandler.createOperation(operation);
                context.addStep(validationOp, AuthorizationValidatingHandler.INSTANCE, OperationContext.Stage.MODEL);
            }
        } // else we know the SecurityRealmAddHandler is part of this overall set of ops and it added the handlers.
        recordCapabilitiesAndRequirements(context, operation, resource);
    }

    @Override
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        rollbackCredentialStoreUpdate(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, context, resource);
        rollbackCredentialStoreUpdate(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE, context, resource);
    }

    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        Set<RuntimeCapability> capabilitySet = capabilities.isEmpty() ? context.getResourceRegistration().getCapabilities() : capabilities;

        for (RuntimeCapability capability : capabilitySet) {
            if (capability.isDynamicallyNamed()) {
                context.registerCapability(capability.fromBaseCapability(context.getCurrentAddress()));
            } else {
                context.registerCapability(capability);
            }
        }

        ModelNode model = resource.getModel();
        for (AttributeDefinition ad : attributeDefinitions) {
            if (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements()) {
                ad.addCapabilityRequirements(context, resource, model.get(ad.getName()));
            }
        }
    }
}
