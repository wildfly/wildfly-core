/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.FileCompleter;
import org.wildfly.core.cli.command.aesh.FileConverter;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.LOAD_FILE, description = "", activator = NoBatchActivator.class)
public class BatchLoadFileCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Argument(converter = FileConverter.class,
            completer = FileCompleter.class, required = true)
    private File file;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.LOAD_FILE));
            return CommandResult.SUCCESS;
        }
        if (file == null) {
            //Legacy compliance
            CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.LOAD_FILE);
            if (res != null) {
                return res;
            }
        }
        if (commandInvocation.getCommandContext().getBatchManager().isBatchActive()) {
            throw new CommandException("Can't start a new batch while in batch mode.");
        }
        if (file == null) {
            throw new CommandException("File name must be provided");
        }
        return handle(commandInvocation, file);
    }

    // Should be private but package for BatchCommand to support Deprecated arg
    static CommandResult handle(CLICommandInvocation commandInvocation, File file) throws CommandException {
        if (commandInvocation.getCommandContext().getBatchManager().isBatchActive()) {
            throw new CommandException("Can't start a new batch while in batch mode.");
        }

        CommandContext ctx = commandInvocation.getCommandContext();
        BatchManager batchManager = ctx.getBatchManager();
        final File currentDir = ctx.getCurrentDir();
        final File baseDir = file.getParentFile();
        if (baseDir != null) {
            ctx.setCurrentDir(baseDir);
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            batchManager.activateNewBatch();
            final Batch batch = batchManager.getActiveBatch();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    batch.add(ctx.toBatchedCommand(line));
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            batchManager.discardActiveBatch();
            throw new CommandException("Failed to read file "
                    + file.getAbsolutePath(), e);
        } catch (CommandFormatException e) {
            batchManager.discardActiveBatch();
            throw new CommandException("Failed to create batch from "
                    + file.getAbsolutePath(), e);
        } finally {
            if (baseDir != null) {
                ctx.setCurrentDir(currentDir);
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return CommandResult.SUCCESS;
    }
}
