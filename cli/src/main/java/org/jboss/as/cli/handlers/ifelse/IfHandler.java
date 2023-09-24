/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.impl.DefaultCompleter.CandidatesProvider;
import org.jboss.as.cli.operation.ParsedCommandLine;


/**
 *
 * @author Alexey Loubyansky
 */
public class IfHandler extends CommandHandlerWithHelp {

    private final ConditionArgument condition;
    private final ArgumentWithValue of;

    public IfHandler() {
        super("if", true);

            condition = new ConditionArgument(this);
            condition.addCantAppearAfter(helpArg);

            of = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){
                @Override
                public Collection<String> getAllCandidates(CommandContext ctx) {
                    return Collections.singletonList("of");
                }}), 1, "--of");
            of.addRequiredPreceding(condition);

            final ArgumentWithValue line = new ArgumentWithValue(this, new CommandLineCompleter() {
                @Override
                public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                    final ParsedCommandLine args = ctx.getParsedCommandLine();
                    final String lnStr = of.getValue(args);
                    if(lnStr == null) {
                        return -1;
                    }

                    final String substitutedLine = args.getSubstitutedLine();
                    String conditionStr;
                    try {
                        conditionStr = condition.getValue(args, true);
                    } catch (CommandFormatException e) {
                        return -1;
                    }
                    int i = substitutedLine.indexOf(conditionStr);
                    if(i < 0) {
                        return -1;
                    }
                    i = substitutedLine.indexOf("of ", i + conditionStr.length());
                    if(i < 0) {
                        return -1;
                    }

                    final String cmd = substitutedLine.substring(i + 3);
/*                    final DefaultCallbackHandler parsedLine = new DefaultCallbackHandler();
                    try {
                        parsedLine.parse(ctx.getCurrentNodePath(), cmd);
                    } catch (CommandFormatException e) {
                        return -1;
                    }
                    int cmdResult = OperationRequestCompleter.INSTANCE.complete(ctx, parsedLine, cmd, 0, candidates);
*/
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
            line.addRequiredPreceding(of);
    }

    public ConditionArgument getConditionArgument() {
        return condition;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return IfElseControlFlow.get(ctx) == null && !ctx.getBatchManager().isBatchActive();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        String argsStr = ctx.getArgumentsString();
        if(argsStr == null) {
            throw new CommandFormatException("The command is missing arguments.");
        }

        final BatchManager batchManager = ctx.getBatchManager();
        if(batchManager.isBatchActive()) {
            throw new CommandFormatException("if is not allowed while in batch mode.");
        }

        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final String conditionStr = this.condition.getOriginalValue(args, true);
        int i = argsStr.indexOf(conditionStr);
        if(i < 0) {
            throw new CommandFormatException("Failed to locate '" + conditionStr + "' in '" + argsStr + "'");
        }
        i = argsStr.indexOf("of", i + conditionStr.length());
        if(i < 0) {
            throw new CommandFormatException("Failed to locate 'of' in '" + argsStr + "'");
        }

        final String requestStr = argsStr.substring(i + 2);
        ctx.registerRedirection(new IfElseControlFlow(ctx, condition.resolveOperation(args), requestStr));
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
