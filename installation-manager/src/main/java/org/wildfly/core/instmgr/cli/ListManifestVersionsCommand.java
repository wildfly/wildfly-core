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
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrListManifestVersionsHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

@CommandDefinition(name = "list-manifest-versions", description = "List available manifest versions for subscribed channels.",
        activator = InstMgrActivator.class)
public class ListManifestVersionsCommand extends AbstractInstMgrCommand {

    @Option(name = "include-downgrades", defaultValue = "false")
    private boolean includeDowngrades;

    @OptionList(name = "repositories")
    private List<String> repositories;
    @Option(name = "local-cache")
    private Path localCache;
    @Option(name = USE_DEFAULT_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.UseDefaultLocalCacheActivator.class)
    private boolean useDefaultLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @OptionList(name = "maven-repo-files")
    private List<File> mavenRepoFiles;

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

        assertOperationSupported(ctx, InstMgrListManifestVersionsHandler.OPERATION_NAME);

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? this.useDefaultLocalCache : null;

        ModelNode response = this.executeOp(ctx, this.host);

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
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrListManifestVersionsHandler.DEFINITION.getName());

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
            op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.normalize().toAbsolutePath().toString());
        }

        if (optUseDefaultLocalCache != null) {
            op.get(InstMgrConstants.USE_DEFAULT_LOCAL_CACHE).set(useDefaultLocalCache);
        }

        op.get(InstMgrConstants.OFFLINE).set(offline);

        op.get(InstMgrConstants.INCLUDE_DOWNGRADES).set(this.includeDowngrades);

        return operationBuilder.build();
    }

    /**
     * Verifies that given operation name is supported by the installer resource on the target host.
     * <p>
     * (The operation may not be supported in situations like connecting from newer version of CLI to an older version
     * of server, or in mixed versions managed domains.)
     *
     * @param ctx command context
     * @param operationName operation name to check
     * @throws CommandException if operation is not supported by the host
     */
    private void assertOperationSupported(CommandContext ctx, String operationName) throws CommandException {
        try {
            ModelControllerClient client = ctx.getModelControllerClient();
            final ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(READ_OPERATION_NAMES_OPERATION);

            // validate the host parameter before building the operation address
            validateHostParameter(ctx, host);

            final PathAddress address;
            if (ctx.isDomainMode()) {
                address = createHost(host, client);
            } else {
                address = createStandalone();
            }
            op.get(ModelDescriptionConstants.ADDRESS).set(address.toModelNode());

            ModelNode response = client.execute(op);
            if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
                String reason = response.hasDefined(FAILURE_DESCRIPTION) ?
                        response.get(FAILURE_DESCRIPTION).asString() : "Unknown reason";
                throw new CommandException("Can't read operations for " + address + ": " + reason);
            }
            if (!response.hasDefined(RESULT)) {
                // Can't verify request property exists, probably an error state.
                throw new CommandException("Can't read operations list for " + address + ": incomplete response");
            }

            if (!response.get(RESULT).asList().contains(new ModelNode(operationName))) {
                String message = String.format("The list-manifest-versions command is not supported by the target host.", address, operationName);
                throw new CommandException(message);
            }
        } catch (IOException e) {
            throw new CommandException(e);
        }
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
