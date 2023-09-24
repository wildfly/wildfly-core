/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.security;

import static org.jboss.as.controller.security.CredentialReference.applyCredentialReferenceUpdateToRuntime;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * A write attribute handler that is capable of handling automatic updates of credential stores via
 * credential reference attributes.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CredentialReferenceWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    public CredentialReferenceWriteAttributeHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    public CredentialReferenceWriteAttributeHandler(AttributeDefinition attribute) {
        super(attribute);
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                    ModelNode oldValue, Resource resource) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, resource);
        handleCredentialReferenceUpdate(context, resource.getModel().get(attributeName), attributeName);
    }


    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue,
                                           ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        return applyCredentialReferenceUpdateToRuntime(context, operation, resolvedValue, currentValue, attributeName);
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) throws OperationFailedException {
        rollbackCredentialStoreUpdate(getAttributeDefinition(attributeName), context, resolvedValue);
    }

}
