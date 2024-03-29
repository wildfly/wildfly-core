/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.jboss.as.cli.operation.ParsedCommandLine;
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
    @Option(name = NO_RESOLVE_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.NoResolveLocalCacheActivator.class)
    private boolean noResolveLocalCache;
    @Option(name = USE_DEFAULT_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.UseDefaultLocalCacheActivator.class)
    private boolean useDefaultLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @OptionList(name = "maven-repo-files")
    private List<File> mavenRepoFiles;
    @Option(name = "revision")
    private String revision;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class)
    public ModelNode headers;

    private Boolean optNoResolveLocalCache;
    private Boolean optUseDefaultLocalCache;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        optNoResolveLocalCache = cmdParser.hasProperty("--" + NO_RESOLVE_LOCAL_CACHE_OPTION) ? noResolveLocalCache : null;
        optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? useDefaultLocalCache : null;

        if (optNoResolveLocalCache != null && optUseDefaultLocalCache != null) {
            throw new CommandException(String.format("%s and %s cannot be used at the same time.", NO_RESOLVE_LOCAL_CACHE_OPTION, USE_DEFAULT_LOCAL_CACHE_OPTION));
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

        if (optNoResolveLocalCache != null) {
            op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        }

        if (optUseDefaultLocalCache != null) {
            op.get(InstMgrConstants.USE_DEFAULT_LOCAL_CACHE).set(useDefaultLocalCache);
        }

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
