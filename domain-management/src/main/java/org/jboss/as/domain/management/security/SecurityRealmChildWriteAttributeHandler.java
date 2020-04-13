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

import static org.jboss.as.controller.security.CredentialReference.applyCredentialReferenceUpdateToRuntime;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.jboss.as.domain.management.ModelDescriptionConstants.SECURITY_REALM;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Attribute write handler for a child resource of a management security realm.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SecurityRealmChildWriteAttributeHandler extends RestartParentWriteAttributeHandler {

    private final AttributeDefinition[] attributeDefinitions;

    public SecurityRealmChildWriteAttributeHandler(AttributeDefinition... attributes) {
        super(SECURITY_REALM, attributes);
        this.attributeDefinitions = attributes;
    }

    void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : attributeDefinitions) {
            resourceRegistration.registerReadWriteAttribute(attr, null, this);
        }
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                    ModelNode oldValue, Resource resource) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, resource);
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE) ||
                attributeName.equals(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) ||
                attributeName.equals(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
            handleCredentialReferenceUpdate(context, resource.getModel().get(attributeName), attributeName);
        }
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue,
                                           ModelNode currentValue, HandbackHolder<ModelNode> handbackHolder) throws OperationFailedException {
        boolean requiresReload = false;
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE) ||
                attributeName.equals(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) ||
                attributeName.equals(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
            requiresReload = applyCredentialReferenceUpdateToRuntime(context, operation, resolvedValue, currentValue, attributeName);
        }
        return super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder) || requiresReload;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, ModelNode invalidatedParentModel) throws OperationFailedException {
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE) ||
                attributeName.equals(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) ||
                attributeName.equals(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
            rollbackCredentialStoreUpdate(getAttributeDefinition(attributeName), context, resolvedValue);
        }
        super.revertUpdateToRuntime(context, operation, attributeName, valueToRestore, resolvedValue, invalidatedParentModel);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
        return !ManagementUtil.isSecurityRealmReloadRequired(context, service);
    }

    @Override
    protected void removeServices(OperationContext context, ServiceName parentService, ModelNode parentModel) throws OperationFailedException {
        SecurityRealmRemoveHandler.INSTANCE.removeServices(context, parentService.getSimpleName(), parentModel);
    }

    @Override
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
        SecurityRealmAddHandler.INSTANCE.installServices(context, parentAddress.getLastElement().getValue(), parentModel);
    }

    @Override
    protected ServiceName getParentServiceName(PathAddress parentAddress) {
        final String realmName = parentAddress.getLastElement().getValue();
        return SecurityRealm.ServiceUtil.createServiceName(realmName);
    }
}
