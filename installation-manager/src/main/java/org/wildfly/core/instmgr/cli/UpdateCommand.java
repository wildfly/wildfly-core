/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CERTIFICATE_INFO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERTIFICATE_CONTENT;
import static org.wildfly.core.instmgr.InstMgrConstants.OFFLINE;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
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
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCertificateImportHandler;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrUnacceptedCertificateHandler;

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

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        final Boolean optNoResolveLocalCache = cmdParser.hasProperty("--" + NO_RESOLVE_LOCAL_CACHE_OPTION) ? noResolveLocalCache : null;
        final Boolean optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? useDefaultLocalCache : null;

        // call the download handler
        Collection<ModelNode> pendingCertificates = getPendingCertificates(ctx);
        // call the import handler
        if (!pendingCertificates.isEmpty() && !importPendingCertificates(pendingCertificates, ctx, commandInvocation)) {
            return CommandResult.SUCCESS;
        }

        ListUpdatesAction.Builder listUpdatesCmdBuilder = new ListUpdatesAction.Builder()
                .setNoResolveLocalCache(optNoResolveLocalCache)
                .setUseDefaultLocalCache(optUseDefaultLocalCache)
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
                        .setNoResolveLocalCache(optNoResolveLocalCache)
                        .setUseDefaultLocalCache(optUseDefaultLocalCache)
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

    private boolean importPendingCertificates(Collection<ModelNode> pendingCertificates, CommandContext ctx, CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        commandInvocation.println("The update is configured to verify the integrity of updated components, but following certificates need to be trusted:");

        for (ModelNode pendingCertificate : pendingCertificates) {
            final ModelNode certificateInfo = pendingCertificate.get(CERTIFICATE_INFO);

            commandInvocation.println("key-id: " + certificateInfo.get(InstMgrConstants.CERT_KEY_ID));
            commandInvocation.println("fingerprint: " + certificateInfo.get(InstMgrConstants.CERT_FINGERPRINT));
            commandInvocation.println("description: " + certificateInfo.get(InstMgrConstants.CERT_DESCRIPTION));
            commandInvocation.println("");

        }
        final String input = commandInvocation.inputLine(new Prompt("Import these certificates y/N "));
        if ("y".equals(input)) {
            commandInvocation.print("Importing a trusted certificate");

            for (ModelNode pendingCertificate : pendingCertificates) {
                this.executeOp(buildImportOperation(pendingCertificate.get(CERTIFICATE_CONTENT).asString()), ctx, host);
            }

            return true;
        } else {
            commandInvocation.print("Importing canceled.");
            return false;
        }
    }

    protected Operation buildImportOperation(String certificateContent) {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrCertificateImportHandler.DEFINITION.getName());
        op.get(CERTIFICATE_CONTENT).set(certificateContent);

        return operationBuilder.build();
    }

    private Collection<ModelNode> getPendingCertificates(CommandContext ctx) throws CommandException {
        if (confirm || dryRun) {
            // skip the check in non-interactive runs because the certificate cannot be accepted either way
            // the update will fail if certificate is required and will print error message
            return Collections.emptyList();
        }

        final ModelNode op = new ModelNode();

        op.get(OP).set(InstMgrUnacceptedCertificateHandler.DEFINITION.getName());
        op.get(OFFLINE).set(offline);

        final ModelNode modelNode = executeOp(OperationBuilder.create(op).build(), ctx, this.host);

        // certificate-content & certificate-info
        return modelNode.get(RESULT).asListOrEmpty();
    }

    private void printListUpdatesResult(CLICommandInvocation commandInvocation, List<ModelNode> changesMn) {
        if (changesMn.isEmpty()) {
            commandInvocation.println("No updates found");
            return;
        }

        int maxLength = 0;
        for (ModelNode artifactChange : changesMn) {
            String channelName = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString();
            maxLength = Math.max(maxLength, channelName.length());
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
