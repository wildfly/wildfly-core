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

import java.io.File;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrPrepareRevertHandler;

@CommandDefinition(name = "revert", description = "Reverts to a previous installation state.", activator = InstMgrActivator.class)
public class RevertCommand extends AbstractInstMgrCommand {
    @OptionList(name = "repositories")
    private List<String> repositories;
    @Option(name = "local-cache")
    private File localCache;
    @Option(name = "no-resolve-local-cache", hasValue = false)
    private boolean noResolveLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @OptionList(name = "maven-repo-files")
    private List<File> mavenRepoFiles;
    @Option(name = "revision")
    private String revision;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class)
    public ModelNode headers;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        commandInvocation.println("\nThe new installation is being prepared ...\n");
        this.executeOp(ctx, this.host);
        ctx.printLine("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command.");

        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrPrepareRevertHandler.DEFINITION.getName());

        if (mavenRepoFiles != null && !mavenRepoFiles.isEmpty()) {
            final ModelNode filesMn = new ModelNode().addEmptyList();
            for (int i = 0; i < mavenRepoFiles.size(); i++) {
                filesMn.add(i);
                operationBuilder.addFileAsAttachment(mavenRepoFiles.get(i));
            }
            op.get(InstMgrConstants.MAVEN_REPO_FILES).set(filesMn);
        }

        addRepositoriesToModelNode(op, this.repositories);

        if (localCache != null) {
            op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.toPath().normalize().toAbsolutePath().toString());
        }

        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        op.get(InstMgrConstants.OFFLINE).set(offline);

        if (revision != null) {
            op.get(InstMgrConstants.REVISION).set(revision);
        }

        if (this.headers != null && headers.isDefined()) {
            op.get(Util.OPERATION_HEADERS).set(headers);
        }

        return operationBuilder.build();
    }
}
