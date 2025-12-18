/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

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
import org.wildfly.core.instmgr.InstMgrListManifestVersionsHandler;

import java.io.File;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "list-manifest-versions", description = "List available manifest versions for subscribed channels.",
        activator = InstMgrActivator.class)
public class ListManifestVersionsCommand extends AbstractInstMgrCommand {

    @Option(name = "include-downgrades", defaultValue = "false")
    private boolean includeDowngrades;

    // Bellow options are common with UpdateCommand -> could be defined in common parent

    @OptionList(name = "repositories")
    private List<String> repositories;
    @Option(name = "local-cache")
    private File localCache;
    @Option(name = NO_RESOLVE_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.NoResolveLocalCacheActivator.class, defaultValue = "true")
    private boolean noResolveLocalCache;
    @Option(name = USE_DEFAULT_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.UseDefaultLocalCacheActivator.class)
    private boolean useDefaultLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @OptionList(name = "maven-repo-files")
    private List<File> mavenRepoFiles;
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

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        final Boolean optNoResolveLocalCache = cmdParser.hasProperty("--" + NO_RESOLVE_LOCAL_CACHE_OPTION) ? noResolveLocalCache : null;
        final Boolean optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? useDefaultLocalCache : null;

        ListManifestVersionsAction action = new ListManifestVersionsAction.Builder()
                .setNoResolveLocalCache(optNoResolveLocalCache)
                .setUseDefaultLocalCache(optUseDefaultLocalCache)
                .setLocalCache(localCache)
                .setRepositories(repositories)
                .setMavenRepoFiles(mavenRepoFiles)
                .setOffline(offline)
                .setHeaders(headers)
                .setIncludeDowngrades(includeDowngrades)
                .build();

        ModelNode response = action.executeOp(ctx, this.host);

        if (response.hasDefined(Util.RESULT)) {
            final ModelNode result = response.get(Util.RESULT);

            final List<ModelNode> manifestInfoList = result.asListOrEmpty();

            if (manifestInfoList.isEmpty()) {
                ctx.printLine("No available manifest versions were found in specified repositories.");

                return CommandResult.SUCCESS;
            }
            printManifestNode(ctx, manifestInfoList);
        }

        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() {
        // TODO: I don't know what this is for.
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrListManifestVersionsHandler.DEFINITION.getName());
        op.get(InstMgrConstants.INCLUDE_DOWNGRADES).set(includeDowngrades);
        return OperationBuilder.create(op).build();
    }

    static void printManifestNode(CommandContext ctx, List<ModelNode> manifestNodes) {
        ctx.printLine("-------");
        for (ModelNode manifestInfoMn: manifestNodes) {
            String currentVersion = manifestInfoMn.get(InstMgrConstants.MANIFEST_CURRENT_VERSION).asString();
            String currentLogicalVersion = manifestInfoMn.get(InstMgrConstants.MANIFEST_CURRENT_LOGICAL_VERSION).asStringOrNull();

            ctx.printLine("# Channel name: " + manifestInfoMn.get(InstMgrConstants.CHANNEL_NAME).asString());
            ctx.printLine("  Manifest location: " + manifestInfoMn.get(InstMgrConstants.MANIFEST_LOCATION).asString());

            ctx.print("  Current manifest version: " +  currentVersion);
            if (currentLogicalVersion != null) {
                ctx.print(" (" + currentLogicalVersion + ")");
            }
            ctx.printLine("");

            ctx.printLine("  Available manifest versions: ");
            for (ModelNode versionMn: manifestInfoMn.get(InstMgrConstants.MANIFEST_VERSIONS).asList()) {
                ctx.print("  - " + versionMn.get(InstMgrConstants.MANIFEST_VERSION).asString());
                String logicalVersion = versionMn.get(InstMgrConstants.MANIFEST_LOGICAL_VERSION).asStringOrNull();
                if (logicalVersion != null) {
                    ctx.print(" (" + logicalVersion + ")");
                }
                ctx.printLine("");
            }
            ctx.printLine("-------");
        }
    }
}
