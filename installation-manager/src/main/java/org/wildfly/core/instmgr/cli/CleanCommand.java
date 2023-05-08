/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.nio.file.Path;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCleanHandler;
import org.wildfly.core.instmgr.InstMgrConstants;

@CommandDefinition(name = "clean", description = "Clean installation manager content.")
public class CleanCommand extends AbstractInstMgrCommand {
    final Path lstUpdatesWorkDir;

    public CleanCommand() {
        this.lstUpdatesWorkDir = null;
    }

    public CleanCommand(Builder builder) {
        this.lstUpdatesWorkDir = builder.lstUpdatesWorkDir;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrCleanHandler.DEFINITION.getName());
        if (lstUpdatesWorkDir != null) {
            op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set(lstUpdatesWorkDir.toString());
        }

        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        this.executeOp(commandInvocation.getCommandContext(), this.host);
        return CommandResult.SUCCESS;
    }

    public static class Builder {
        private Path lstUpdatesWorkDir;

        public Builder setLstUpdatesWorkDir(Path lstUpdatesWorkDir) {
            this.lstUpdatesWorkDir = lstUpdatesWorkDir;
            return this;
        }

        public CleanCommand createCleanCommand() {
            return new CleanCommand(this);
        }
    }
}
