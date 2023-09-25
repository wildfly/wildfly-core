/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.applyCredentialReferenceUpdateToRuntime;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;

import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:thofman@redhat.com">Tomas Hofman</a>
 */
class ElytronReloadRequiredWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler implements ElytronOperationStepHandler {
    ElytronReloadRequiredWriteAttributeHandler(final AttributeDefinition... definitions) {
        super(definitions);
    }

    ElytronReloadRequiredWriteAttributeHandler(final Collection<AttributeDefinition> definitions) {
        super(definitions);
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                    ModelNode oldValue, Resource resource) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, resource);
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE)) {
            handleCredentialReferenceUpdate(context, resource.getModel().get(attributeName), attributeName);
        }
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue,
                                           ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        boolean requiresReload = false;
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE)) {
            requiresReload = applyCredentialReferenceUpdateToRuntime(context, operation, resolvedValue, currentValue, attributeName);

        }
        return super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder) || requiresReload;
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) throws OperationFailedException {
        if (attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE)) {
            rollbackCredentialStoreUpdate(getAttributeDefinition(attributeName), context, resolvedValue);
        }
    }
}
