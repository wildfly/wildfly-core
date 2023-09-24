/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.vfs.TempFileProvider;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.spi.MountHandle;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;

/**
 * Deploy using a CLI archive file (.cli file). All fields are public to be
 * accessible from legacy commands. To be made private when legacies are
 * removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deploy-cli-archive", description = "", activator = ControlledCommandActivator.class)
public class DeployArchiveCommand extends CommandWithPermissions implements Command<CLICommandInvocation>, BatchCompliantCommand, LegacyBridge {

    private static final String CLI_ARCHIVE_SUFFIX = ".cli";

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option(hasValue = true, required = false)
    public String script;

    @Argument(required = true)
    public File file;

    public DeployArchiveCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.deployArchiveAccess(permissions), permissions);
    }

    @Deprecated
    public DeployArchiveCommand(CommandContext ctx) {
        this(ctx, null);
    }

    protected String getAction() {
        return "deploy-cli-archive";
    }

    protected String getDefaultScript() {
        return "deploy.scr";
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment " + getAction()));
            return CommandResult.SUCCESS;
        }
        CommandContext ctx = commandInvocation.getCommandContext();
        return execute(ctx);
    }

    public CommandResult execute(CommandContext ctx) throws CommandException {
        checkArgument();

        Attachments attachments = new Attachments();
        try {
            ModelNode request = buildRequest(ctx,
                    attachments);

            // if script had no server-side commands, just return
            if (!request.get("steps").isDefined()) {
                return CommandResult.SUCCESS;
            }

            OperationBuilder op = new OperationBuilder(request, true);
            for (String f : attachments.getAttachedFiles()) {
                op.addFileAsAttachment(new File(f));
            }
            ModelNode result;
            try (Operation operation = op.build()) {
                result = ctx.getModelControllerClient().execute(operation);
            }
            if (!Util.isSuccess(result)) {
                throw new CommandException(Util.getFailureDescription(result));
            }
        } catch (IOException e) {
            throw new CommandException("Failed to deploy archive", e);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        } finally {
            attachments.done();
        }
        return CommandResult.SUCCESS;

    }

    private void checkArgument() throws CommandException {
        if (file == null) {
            throw new CommandException("No archive file");
        }
        if (!file.exists()) {
            throw new CommandException("Path " + file.getAbsolutePath()
                    + " doesn't exist.");
        }
        if (!isCliArchive(file)) {
            throw new CommandException("Not a CLI archive " + file.getAbsolutePath());
        }
    }

    @Override
    public ModelNode buildRequest(CommandContext context)
            throws CommandFormatException {
        return buildRequest(context, null);
    }

    /**
     * null attachments means that the command is in a batch, non null means
     * command executed.
     *
     * Inside a batch, the attachments must be added to the existing batch and
     * NOT to the temporary batch created to build the composite request.
     * Outside of a batch, the attachments MUST be added to the passed non null
     * attachments.
     *
     * @param context
     * @param attachments
     * @return
     * @throws CommandFormatException
     */
    @Override
    public ModelNode buildRequest(CommandContext context, Attachments attachments)
            throws CommandFormatException {
        CommandContext ctx = context;
        TempFileProvider tempFileProvider;
        MountHandle root;
        try {
            String name = "cli-" + System.currentTimeMillis();
            tempFileProvider = TempFileProvider.create(name,
                    Executors.newSingleThreadScheduledExecutor((r) -> new Thread(r, "CLI (un)deploy archive tempFile")),
                    true);
            root = extractArchive(file, tempFileProvider, name);
        } catch (IOException e) {
            e.printStackTrace();
            throw new OperationFormatException("Unable to extract archive '"
                    + file.getAbsolutePath() + "' to temporary location");
        }
        Consumer<Attachments> cl = (a) -> {
            VFSUtils.safeClose(root, tempFileProvider);
        };
        if (attachments != null) {
            attachments.addConsumer(cl);
        }
        final File currentDir = ctx.getCurrentDir();
        ctx.setCurrentDir(root.getMountSource());
        String holdbackBatch = activateNewBatch(ctx);

        try {
            if (script == null) {
                script = getDefaultScript();
            }

            File scriptFile = new File(ctx.getCurrentDir(), script);
            if (!scriptFile.exists()) {
                throw new CommandFormatException("ERROR: script '"
                        + script + "' not found.");
            }

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(scriptFile));
                String line = reader.readLine();
                while (!ctx.isTerminated() && line != null) {
                    context.handle(line);
                    line = reader.readLine();
                }
            } catch (FileNotFoundException e) {
                throw new CommandFormatException("ERROR: script '"
                        + script + "' not found.");
            } catch (IOException e) {
                throw new CommandFormatException("Failed to read the next command from "
                        + scriptFile.getName() + ": " + e.getMessage(), e);
            } catch (CommandLineException ex) {
                throw new CommandFormatException(ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
            return ctx.getBatchManager().getActiveBatch().toRequest();
        } catch (CommandFormatException cfex) {
            cl.accept(attachments);
            throw cfex;
        } finally {
            // reset current dir in context
            ctx.setCurrentDir(currentDir);
            discardBatch(ctx, holdbackBatch, attachments, cl);
        }
    }

    public static boolean isCliArchive(File f) {
        return f != null && !f.isDirectory()
                && f.getName().endsWith(CLI_ARCHIVE_SUFFIX);
    }

    static MountHandle extractArchive(File archive,
            TempFileProvider tempFileProvider, String name) throws IOException {
        return ((MountHandle) VFS.mountZipExpanded(archive, VFS.getChild(name),
                tempFileProvider));
    }

    static String activateNewBatch(CommandContext ctx) {
        String currentBatch = null;
        BatchManager batchManager = ctx.getBatchManager();
        Attachments attachments = null;
        if (batchManager.isBatchActive()) {
            Batch current = batchManager.getActiveBatch();
            attachments = current.getAttachments();
            currentBatch = "batch" + System.currentTimeMillis();
            batchManager.holdbackActiveBatch(currentBatch);
        }
        batchManager.activateNewBatch();
        Batch archiveBatch = batchManager.getActiveBatch();
        // transfer all attachments to new batch in order to have proper index
        // computation.
        if (attachments != null) {
            for (String f : attachments.getAttachedFiles()) {
                archiveBatch.getAttachments().addFileAttachment(f);
            }
        }
        return currentBatch;
    }

    static void discardBatch(CommandContext ctx, String holdbackBatch, Attachments attachments, Consumer<Attachments> listener) {
        BatchManager batchManager = ctx.getBatchManager();
        // Get the files attached by the CLI script.
        // Must then add them to the passed attachemnts if non null.
        // If null, then we should have an heldback batch to which we need to add them
        Attachments archiveAttachments = batchManager.getActiveBatch().getAttachments();
        if (attachments != null) {
            for (String f : archiveAttachments.getAttachedFiles()) {
                attachments.addFileAttachment(f);
            }
        }
        batchManager.discardActiveBatch();
        if (holdbackBatch != null) {
            batchManager.activateHeldbackBatch(holdbackBatch);
            Attachments activeAttachments = batchManager.getActiveBatch().getAttachments();
            // We must transfer all attachments that have been added in addition to the one
            // that have been transfered when creating the archive batch.
            for (int i = activeAttachments.getAttachedFiles().size();
                    i < archiveAttachments.getAttachedFiles().size(); i++) {
                activeAttachments.addFileAttachment(archiveAttachments.getAttachedFiles().get(i));
            }
            activeAttachments.addConsumer(listener);
        }

    }

    @Override
    public BatchResponseHandler buildBatchResponseHandler(CommandContext commandContext, Attachments attachments) {
        return null;
    }
}
