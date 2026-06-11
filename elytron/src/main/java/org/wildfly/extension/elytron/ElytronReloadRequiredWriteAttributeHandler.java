/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.applyCredentialReferenceUpdateToRuntime;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:thofman@redhat.com">Tomas Hofman</a>
 */
class ElytronReloadRequiredWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler implements ElytronOperationStepHandler {

    static final OperationStepHandler INSTANCE = new ElytronReloadRequiredWriteAttributeHandler();

    private ElytronReloadRequiredWriteAttributeHandler() {
        // Hide
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                    ModelNode oldValue, Resource resource) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, resource);
        if (isCredentialReference(attributeName)) {
            handleCredentialReferenceUpdate(context, resource.getModel().get(attributeName), attributeName);
        }
        if (attributeName.equals(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING)) {
            CertificateAuthorityAccountDefinition.validateExternalAccountBinding(resource.getModel());
            ModelNode externalAccountBinding = resource.getModel().get(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING);
            if (externalAccountBinding.isDefined()) {
                handleCredentialReferenceUpdate(context, externalAccountBinding.get(CredentialReference.CREDENTIAL_REFERENCE),
                        CredentialReference.CREDENTIAL_REFERENCE, ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING);
            }
        }
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue,
                                           ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        boolean requiresReload = false;
        if (isCredentialReference(attributeName)) {
            requiresReload = applyCredentialReferenceUpdateToRuntime(context, operation, resolvedValue, currentValue, attributeName);

        } else if (attributeName.equals(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING)) {
            ModelNode credentialReferenceOperation = new ModelNode();
            if (operation.get(ModelDescriptionConstants.VALUE).hasDefined(CredentialReference.CREDENTIAL_REFERENCE)) {
                credentialReferenceOperation.get(ModelDescriptionConstants.VALUE).set(operation.get(ModelDescriptionConstants.VALUE).get(CredentialReference.CREDENTIAL_REFERENCE));
            }
            ModelNode resolvedCredentialReference = resolvedValue.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)
                    ? resolvedValue.get(CredentialReference.CREDENTIAL_REFERENCE)
                    : new ModelNode();
            ModelNode currentCredentialReference = currentValue.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)
                    ? currentValue.get(CredentialReference.CREDENTIAL_REFERENCE)
                    : new ModelNode();
            requiresReload = applyCredentialReferenceUpdateToRuntime(context, credentialReferenceOperation, resolvedCredentialReference, currentCredentialReference,
                    CredentialReference.CREDENTIAL_REFERENCE, ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING) || ! resolvedValue.equals(currentValue);
        }
        return super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder) || requiresReload;
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) throws OperationFailedException {
        if (isCredentialReference(attributeName)) {
            rollbackCredentialStoreUpdate(context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName).getAttributeDefinition(), context, resolvedValue);
        } else if (attributeName.equals(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING)) {
            ModelNode credentialReference = resolvedValue.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)
                    ? resolvedValue.get(CredentialReference.CREDENTIAL_REFERENCE)
                    : new ModelNode();
            rollbackCredentialStoreUpdate(CertificateAuthorityAccountDefinition.EXTERNAL_ACCOUNT_BINDING_CREDENTIAL_REFERENCE, context, credentialReference,
                    ElytronDescriptionConstants.EXTERNAL_ACCOUNT_BINDING);
        }
    }

    private static boolean isCredentialReference(String attributeName) {
        return attributeName.equals(CredentialReference.CREDENTIAL_REFERENCE);
    }
}
