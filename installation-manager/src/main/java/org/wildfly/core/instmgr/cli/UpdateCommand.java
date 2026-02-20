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
import org.aesh.readline.Prompt;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrPrepareUpdateHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

@CommandDefinition(name = "update", description = "Apply the latest available patches on a server instance.", activator = InstMgrActivator.class)
public class UpdateCommand extends AbstractInstMgrCommand {
    public static final String DRY_RUN_OPTION = "dry-run";
    public static final String CONFIRM_OPTION = "confirm";
    @Option(name = DRY_RUN_OPTION, hasValue = false, activator = AbstractInstMgrCommand.DryRunActivator.class)
    private boolean dryRun;
    @Option(name = CONFIRM_OPTION, hasValue = false, activator = AbstractInstMgrCommand.ConfirmActivator.class)
    private boolean confirm;
    @OptionList(name = "repositories")
    private List<String> repositories;
    @OptionList(name = "manifest-versions")
    private List<String> manifestVersions;
    @Option(name = "allow-manifest-downgrades", defaultValue = "false")
    private boolean allowManifestDowngrades;
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

        if (confirm && dryRun) {
            throw new CommandException(String.format("%s and %s cannot be used at the same time.", CONFIRM_OPTION, DRY_RUN_OPTION));
        }

        validateHostParameter(ctx, host);

        if (manifestVersions != null && !manifestVersions.isEmpty()) {
            // If user set --manifest-versions parameter, verify the parameter is supported by the host.
            assertOperationPropertySupported(ctx, InstMgrPrepareUpdateHandler.OPERATION_NAME, InstMgrConstants.MANIFEST_VERSIONS,
                    "The --manifest-versions attribute is not supported by the target host.");
        }

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        final Boolean optNoResolveLocalCache = cmdParser.hasProperty("--" + NO_RESOLVE_LOCAL_CACHE_OPTION) ? noResolveLocalCache : null;
        final Boolean optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? useDefaultLocalCache : null;

        ListUpdatesAction.Builder listUpdatesCmdBuilder = new ListUpdatesAction.Builder()
                .setNoResolveLocalCache(optNoResolveLocalCache)
                .setUseDefaultLocalCache(optUseDefaultLocalCache)
                .setLocalCache(localCache)
                .setRepositories(repositories)
                .setManifestVersions(manifestVersions)
                .setMavenRepoFiles(mavenRepoFiles)
                .setOffline(offline)
                .setHeaders(headers);

        ListUpdatesAction listUpdatesCmd = listUpdatesCmdBuilder.build();
        ModelNode response = listUpdatesCmd.executeOp(ctx, this.host);

        if (response.hasDefined(Util.RESULT)) {
            final ModelNode result = response.get(Util.RESULT);

            boolean manifestDowngradesPresent = false;
            List<ModelNode> manifestChangesMn = Collections.emptyList();
            if (result.hasDefined(InstMgrConstants.LIST_MANIFEST_UPDATES_RESULT)) {
                manifestChangesMn = result.get(InstMgrConstants.LIST_MANIFEST_UPDATES_RESULT).asListOrEmpty();
                printManifestUpdatesResult(commandInvocation, manifestChangesMn);

                manifestDowngradesPresent = manifestChangesMn.stream().anyMatch(node ->
                        node.get(InstMgrConstants.LIST_UPDATES_IS_DOWNGRADE).asBoolean(false));
            }

            final List<ModelNode> artifactChangesMn = result.get(InstMgrConstants.LIST_UPDATES_RESULT).asListOrEmpty();
            printArtifactUpdatesResult(commandInvocation, artifactChangesMn);

            if (!allowManifestDowngrades && manifestDowngradesPresent) {
                commandInvocation.println("This operation would result in a manifest being downgraded, but allow-manifest-downgrades parameter is set to false. Exiting...");
                return CommandResult.FAILURE;
            }

            if (dryRun) {
                return CommandResult.SUCCESS;
            }

            Path lstUpdatesWorkDir = null;
            if (result.hasDefined(InstMgrConstants.LIST_UPDATES_WORK_DIR)) {
                lstUpdatesWorkDir = Paths.get(result.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).asString());
            }

            if (!artifactChangesMn.isEmpty() || !manifestChangesMn.isEmpty()) {
                if (!confirm) {
                    String reply;

                    try {
                        while (true) {
                            reply = commandInvocation.inputLine(new Prompt("Would you like to proceed with preparing this update? [y/N]: "));
                            if (reply != null && (reply.equalsIgnoreCase("N") || reply.isEmpty())) {
                                // clean the cache if there is one
                                if (lstUpdatesWorkDir != null) {
                                    CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                                    cleanCommand.executeOp(ctx, this.host);
                                }

                                return CommandResult.SUCCESS;
                            } else if (reply != null && reply.equalsIgnoreCase("y")) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // In case of an error, clean the cache if there is one
                        if (lstUpdatesWorkDir != null) {
                            CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                            cleanCommand.executeOp(ctx, this.host);
                        }

                        return CommandResult.FAILURE;
                    }
                }

                commandInvocation.println("\nThe new installation is being prepared ...\n");
                // trigger an prepare-update
                PrepareUpdateAction prepareUpdateAction = new PrepareUpdateAction.Builder()
                        .setNoResolveLocalCache(optNoResolveLocalCache)
                        .setUseDefaultLocalCache(optUseDefaultLocalCache)
                        .setLocalCache(localCache)
                        .setRepositories(repositories)
                        .setManifestVersions(manifestVersions)
                        .setAllowManifestDowngrades(allowManifestDowngrades)
                        .setOffline(offline)
                        .setListUpdatesWorkDir(lstUpdatesWorkDir)
                        .setHeaders(headers)
                        .build();

                ModelNode prepareUpdateResult = prepareUpdateAction.executeOp(ctx, this.host);
                printUpdatesResult(ctx, prepareUpdateResult.get(Util.RESULT));
            }
        } else {
            ctx.printLine("Operation result is not available.");
        }

        return CommandResult.SUCCESS;
    }

    static void printManifestUpdatesResult(CLICommandInvocation commandInvocation, List<ModelNode> changesMn) {
        if (changesMn.isEmpty()) {
            commandInvocation.println("No manifest updates found");
            return;
        }

        int maxNameLength = changesMn.stream()
                .map(node -> node.get(InstMgrConstants.CHANNEL_NAME).asString().length())
                .max(Integer::compareTo).orElse(0) + 1;

        int maxLocationLength = changesMn.stream()
                .map(node -> node.get(InstMgrConstants.MANIFEST_LOCATION).asString().length())
                .max(Integer::compareTo).orElse(0) + 1;

        commandInvocation.println("Manifest updates found:");
        for (ModelNode change : changesMn) {
            String channelName = change.get(InstMgrConstants.CHANNEL_NAME).asString();
            String location = change.get(InstMgrConstants.MANIFEST_LOCATION).asString();
            String oldVersion = change.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).asStringOrNull();
            String newVersion = change.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).asStringOrNull();

            oldVersion = oldVersion == null ? "[]" : oldVersion;
            newVersion = newVersion == null ? "[]" : newVersion;

            commandInvocation.println(String.format("    %1$-" + maxNameLength + "s %2$-" + maxLocationLength + "s %3$15s ==> %4$-15s",
                    channelName, location, oldVersion, newVersion));
        }
        commandInvocation.println(""); // Add newline after the listing
    }

    static void printArtifactUpdatesResult(CLICommandInvocation commandInvocation, List<ModelNode> changesMn) {
        if (changesMn.isEmpty()) {
            commandInvocation.println("No artifact updates found");
            return;
        }

        int maxLength = 0;
        for (ModelNode artifactChange : changesMn) {
            String name = artifactChange.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).asString();
            maxLength = Math.max(maxLength, name.length());
        }
        maxLength += 1;

        commandInvocation.println("Artifact updates found:");
        for (ModelNode change : changesMn) {
            String status = change.get(InstMgrConstants.LIST_UPDATES_STATUS).asString();
            String artifactName = change.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).asString();
            String oldVersion = change.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).asStringOrNull();
            String newVersion = change.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).asStringOrNull();

            oldVersion = oldVersion == null ? "[]" : oldVersion;
            newVersion = newVersion == null ? "[]" : newVersion;

            commandInvocation.println(String.format("    %4$-7s %1$-" + maxLength + "s %2$15s ==> %3$-15s", artifactName, oldVersion, newVersion, status));
        }
        commandInvocation.println(""); // Add newline after the listing
    }

    private void printUpdatesResult(CommandContext ctx, ModelNode result) {
        if (result.isDefined()) {
            ctx.printLine("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command.");
        } else {
            ctx.printLine("The candidate server was not generated as there were no pending updates found.");
        }
    }

    @Override
    protected Operation buildOperation() {
        throw new IllegalStateException("Update Command has not build operation");
    }

    /**
     * Verifies that an operation supports a parameter.
     * <p>
     * (The parameter may not be supported in situations like connecting from newer version of CLI to an older version
     * of server, or in mixed versions managed domains.)
     *
     * @param ctx command context
     * @param operationName operation name to check
     * @throws CommandException if operation parameter is not supported by the host
     */
    private void assertOperationPropertySupported(CommandContext ctx, String operationName, String propertyName, String errorMessage)
            throws CommandException {
        try {
            ModelControllerClient client = ctx.getModelControllerClient();
            final ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION);
            op.get(ModelDescriptionConstants.NAME).set(operationName);

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
                throw new CommandException("Can't read request properties for operation " + operationName + ": " + reason);
            }
            if (!response.hasDefined(RESULT) || !response.get(RESULT).hasDefined(REQUEST_PROPERTIES)) {
                // Can't verify request property exists, probably an error state.
                throw new CommandException("Can't read request properties for operation " + operationName + ": incomplete response");
            }

            if (!response.get(RESULT).get(REQUEST_PROPERTIES).hasDefined(propertyName)) {
                throw new CommandException(errorMessage);
            }
        } catch (IOException e) {
            throw new CommandException(e);
        }
    }

}
