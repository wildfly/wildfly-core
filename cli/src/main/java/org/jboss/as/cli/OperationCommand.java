/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import org.jboss.as.cli.handlers.ResponseHandler;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public interface OperationCommand extends CommandHandler {

    static class HandledRequest {

        private final ModelNode request;
        private final ResponseHandler handler;

        public HandledRequest(ModelNode request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
        }

        /**
         * @return the request
         */
        public ModelNode getRequest() {
            return request;
        }

        /**
         * @return the handler
         */
        public ResponseHandler getResponseHandler() {
            return handler;
        }
    }

    ModelNode buildRequest(CommandContext ctx) throws CommandFormatException;

    default ModelNode buildRequest(CommandContext ctx, Attachments attachments) throws CommandFormatException {
        return buildRequest(ctx);
    }

    default HandledRequest buildHandledRequest(CommandContext ctx, Attachments attachments) throws CommandFormatException {
        return new HandledRequest(buildRequest(ctx, attachments), null);
    }
}
