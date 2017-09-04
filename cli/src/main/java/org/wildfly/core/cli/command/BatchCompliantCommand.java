/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
