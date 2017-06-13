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
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.FileCompleter;
import org.wildfly.core.cli.command.aesh.FileConverter;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.RUN_FILE, description = "", activator = NoBatchActivator.class)
public class BatchRunFileCommand extends BatchRunCommand {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    public boolean help;

    @Argument(required = true, converter = FileConverter.class,
            completer = FileCompleter.class) // required = true
    public File file;

    @Override
    public ModelNode newRequest(CommandContext ctx) throws CommandLineException {
        final BatchManager batchManager = ctx.getBatchManager();
        if (batchManager.isBatchActive()) {
            throw new CommandLineException("Batch already active, can't start new batch");
        }

        if (file == null) {
            throw new CommandLineException("No batch file to run");
        }

        if (!file.exists()) {
            throw new CommandLineException("File " + file.getAbsolutePath() + " does not exist.");
        }

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
            while (line != null) {
                ctx.handle(line.trim());
                line = reader.readLine();
            }
            final ModelNode request = batchManager.getActiveBatch().toRequest();
            if (headers != null) {
                request.get(Util.OPERATION_HEADERS).set(headers);
            }
            return request;
        } catch (IOException e) {
            throw new CommandLineException("Failed to read file "
                    + file.getAbsolutePath(), e);
        } catch (CommandLineException e) {
            throw new CommandLineException("Failed to create batch from "
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
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.RUN_FILE));
            return CommandResult.SUCCESS;
        }
        //Legacy compliance
        CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.RUN_FILE);
        if (res != null) {
            return res;
        }
        try {
            return super.execute(commandInvocation.getCommandContext());
        } finally {
            BatchManager batchManager = commandInvocation.getCommandContext().
                    getBatchManager();
            batchManager.discardActiveBatch();
        }
    }

}
