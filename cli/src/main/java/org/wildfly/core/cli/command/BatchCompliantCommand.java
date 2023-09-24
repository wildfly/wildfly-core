/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command;

import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 * A Command that can be added to batch must implement this interface.
 *
 * @author jdenise@redhat.com
 */
public interface BatchCompliantCommand extends DMRCommand {
    /**
     * Called by batch once composite response is received.
     *
     * @author jdenise@redhat.com
     */
    interface BatchResponseHandler {

        /**
         * Handle the passed step.
         *
         * @param step The current step.
         * @param response The operation that contains the step.
         * @throws CommandLineException
         */
        void handleResponse(ModelNode step, OperationResponse response) throws CommandLineException;
    }

    BatchResponseHandler buildBatchResponseHandler(CommandContext commandContext,
            Attachments attachments) throws CommandLineException;
}
