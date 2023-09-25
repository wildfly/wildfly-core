/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

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
    @Option(name = "local-cache")
    private File localCache;
    @Option(name = "no-resolve-local-cache", hasValue = false)
    private boolean noResolveLocalCache;
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

        ListUpdatesAction.Builder listUpdatesCmdBuilder = new ListUpdatesAction.Builder()
                .setNoResolveLocalCache(noResolveLocalCache)
                .setLocalCache(localCache)
                .setRepositories(repositories)
                .setMavenRepoFiles(mavenRepoFiles)
                .setOffline(offline)
                .setHeaders(headers);

        ListUpdatesAction listUpdatesCmd = listUpdatesCmdBuilder.build();
        ModelNode response = listUpdatesCmd.executeOp(ctx, this.host);

        if (response.hasDefined(Util.RESULT)) {
            final ModelNode result = response.get(Util.RESULT);
            final List<ModelNode> changesMn = result.get(InstMgrConstants.LIST_UPDATES_RESULT).asListOrEmpty();
            printListUpdatesResult(commandInvocation, changesMn);

            if (dryRun) {
                return CommandResult.SUCCESS;
            }

            Path lstUpdatesWorkDir = null;
            if (result.hasDefined(InstMgrConstants.LIST_UPDATES_WORK_DIR)) {
                lstUpdatesWorkDir = Paths.get(result.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).asString());
            }

            if (!changesMn.isEmpty()) {
                if (!confirm) {
                    String reply = null;

                    try {
                        while (reply == null) {
                            reply = commandInvocation.inputLine(new Prompt("\nWould you like to proceed with preparing this update? [y/N]:"));
                            if (reply != null && reply.equalsIgnoreCase("N")) {
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
                PrepareUpdateAction.Builder prepareUpdateActionBuilder = new PrepareUpdateAction.Builder()
                        .setNoResolveLocalCache(noResolveLocalCache)
                        .setLocalCache(localCache)
                        .setRepositories(repositories)
                        .setOffline(offline)
                        .setListUpdatesWorkDir(lstUpdatesWorkDir)
                        .setHeaders(headers);

                PrepareUpdateAction prepareUpdateAction = prepareUpdateActionBuilder.build();
                ModelNode prepareUpdateResult = prepareUpdateAction.executeOp(ctx, this.host);
                printUpdatesResult(ctx, prepareUpdateResult.get(Util.RESULT));
            }
        } else {
            ctx.printLine("Operation result is not available.");
        }

        return CommandResult.SUCCESS;
    }

    private void printListUpdatesResult(CLICommandInvocation commandInvocation, List<ModelNode> changesMn) {
        if (changesMn.isEmpty()) {
            commandInvocation.println("No updates found");
            return;
        }

        int maxLength = 0;
        for (ModelNode artifactChange : changesMn) {
            String channelName = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString();
            maxLength = maxLength < channelName.length() ? channelName.length() : maxLength;
        }
        maxLength += 1;

        commandInvocation.println("Updates found:");
        for (ModelNode change : changesMn) {
            String artifactName = change.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).asString();
            String oldVersion = change.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).asStringOrNull();
            String newVersion = change.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).asStringOrNull();

            oldVersion = oldVersion == null ? "[]" : oldVersion;
            newVersion = newVersion == null ? "[]" : newVersion;

            commandInvocation.println(String.format("    %1$-" + maxLength + "s %2$15s ==> %3$-15s", artifactName, oldVersion, newVersion));
        }
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
}
