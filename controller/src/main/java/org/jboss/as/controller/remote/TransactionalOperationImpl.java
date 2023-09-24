/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.remote;

import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.dmr.ModelNode;

/**
* @author Emanuel Muckenhuber
*/
public class TransactionalOperationImpl implements TransactionalProtocolClient.Operation {

    private final ModelNode operation;
    private final OperationMessageHandler messageHandler;
    private final OperationAttachments attachments;

    protected TransactionalOperationImpl(final ModelNode operation, final OperationMessageHandler messageHandler, final OperationAttachments attachments) {
        this.operation = operation;
        this.messageHandler = messageHandler;
        this.attachments = attachments;
    }

    @Override
    public ModelNode getOperation() {
        return operation;
    }

    @Override
    public OperationMessageHandler getMessageHandler() {
        return messageHandler;
    }

    @Override
    public OperationAttachments getAttachments() {
        return attachments;
    }

}
