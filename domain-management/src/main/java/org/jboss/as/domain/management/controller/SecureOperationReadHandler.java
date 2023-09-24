/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.management.AuthorizedAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handler for reading the 'operation' and 'address' fields of an active operation that
 * ensures that responses are elided if the caller does not have rights to address the
 * operation's target.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class SecureOperationReadHandler implements OperationStepHandler {

    private static final String HIDDEN = "<hidden>";

    static final OperationStepHandler INSTANCE = new SecureOperationReadHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        AuthorizedAddress authorizedAddress = AuthorizedAddress.authorizeAddress(context, operation);

        String attribute = operation.require(ModelDescriptionConstants.NAME).asString();
        if (ActiveOperationResourceDefinition.OPERATION_NAME.getName().equals(attribute)) {
            if (authorizedAddress.isElided()) {
                context.getResult().set(HIDDEN);
            } else {
                context.getResult().set(model.get(attribute));
            }
        } else if (ActiveOperationResourceDefinition.ADDRESS.getName().equals(attribute)) {
            if (authorizedAddress.isElided()) {
                context.getResult().set(authorizedAddress.getAddress());
            } else {
                context.getResult().set(model.get(attribute));
            }
        } else {
            // Programming error
            throw new IllegalStateException();
        }
    }
}
