/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.batch;

import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithValue;


/**
 *
 * @author Alexey Loubyansky
 */
public class BatchEditLineHandler extends CommandHandlerWithHelp {

    private ArgumentWithValue ln;

    public BatchEditLineHandler() {
        super("batch-edit-line");

            ln = new ArgumentWithValue(this, 0, "--line-number");
            ln.addCantAppearAfter(helpArg);

            ArgumentWithValue line = new ArgumentWithValue(this, new CommandLineCompleter() {
                @Override
                public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                    final String lnStr = ln.getValue(ctx.getParsedCommandLine());
                    if(lnStr == null) {
                        return -1;
                    }

                    final String substitutedLine = ctx.getParsedCommandLine().getSubstitutedLine();
                    boolean skipWS;
                    int wordCount;
                    if(Character.isWhitespace(substitutedLine.charAt(0))) {
                        skipWS = true;
                        wordCount = 0;
                    } else {
                        skipWS = false;
                        wordCount = 1;
                    }
                    int cmdStart = 1;
                    while(cmdStart < substitutedLine.length()) {
                        if(skipWS) {
                            if(!Character.isWhitespace(substitutedLine.charAt(cmdStart))) {
                                skipWS = false;
                                ++wordCount;
                                if(wordCount == 3) {
                                    break;
                                }
                            }
                        } else if(Character.isWhitespace(substitutedLine.charAt(cmdStart))) {
                            skipWS = true;
                        }
                        ++cmdStart;
                    }

                    final String cmd;
                    if(wordCount == 2) {
                        cmd = "";
                    } else if(wordCount != 3) {
                        return -1;
                    } else {
                        cmd = substitutedLine.substring(cmdStart);
                    }

                    int cmdResult = ctx.getDefaultCommandCompleter().complete(ctx, cmd, cmd.length(), candidates);
                    if(cmdResult < 0) {
                        return cmdResult;
                    }

                    // escaping index correction
                    int escapeCorrection = 0;
                    int start = substitutedLine.length() - 1 - buffer.length();
                    while(start - escapeCorrection >= 0) {
                        final char ch = substitutedLine.charAt(start - escapeCorrection);
                        if(Character.isWhitespace(ch) || ch == '=') {
                            break;
                        }
                        ++escapeCorrection;
                    }

                    return buffer.length() + escapeCorrection - (cmd.length() - cmdResult);
                }}, Integer.MAX_VALUE, "--line") {
            };
            line.addRequiredPreceding(ln);
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        // argument validation is performed during request construction
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(!super.isAvailable(ctx)) {
            return false;
        }
        return ctx.isBatchMode();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {

        BatchManager batchManager = ctx.getBatchManager();
        if(!batchManager.isBatchActive()) {
            throw new CommandFormatException("No active batch.");
        }

        Batch batch = batchManager.getActiveBatch();
        final int batchSize = batch.size();
        if(batchSize == 0) {
            throw new CommandFormatException("The batch is empty.");
        }

        String argsStr = ctx.getArgumentsString();
        if(argsStr == null) {
            throw new CommandFormatException("Missing line number.");
        }

        int i = 0;
        while(i < argsStr.length()) {
            if(Character.isWhitespace(argsStr.charAt(i))) {
                break;
            }
            ++i;
        }

        if(i == argsStr.length()) {
            throw new CommandFormatException("Missing the new command line after the index.");
        }

        String intStr = argsStr.substring(0, i);
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(intStr);
        } catch(NumberFormatException e) {
            throw new CommandFormatException("Failed to parse line number '" + intStr + "': " + e.getLocalizedMessage());
        }

        if(lineNumber < 1 || lineNumber > batchSize) {
            throw new CommandFormatException(lineNumber + " isn't in range [1.." + batchSize + "].");
        }

        String editedLine = argsStr.substring(i).trim();
        if(editedLine.length() == 0) {
            throw new CommandFormatException("Missing the new command line after the index.");
        }

        if(editedLine.charAt(0) == '"') {
            if(editedLine.length() > 1 && editedLine.charAt(editedLine.length() - 1) == '"') {
                editedLine = editedLine.substring(1, editedLine.length() - 1);
            }
        }

        BatchedCommand newCmd = ctx.toBatchedCommand(editedLine);
        batch.set(lineNumber - 1, newCmd);
        ctx.printLine("#" + lineNumber + " " + newCmd.getCommand());
    }

    /**
     * It has to accept everything since we don't know what kind of command will be edited.
     */
    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return true;
    }

    @Override
    public boolean hasArgument(CommandContext ctx, String name) {
        return true;
    }
}
