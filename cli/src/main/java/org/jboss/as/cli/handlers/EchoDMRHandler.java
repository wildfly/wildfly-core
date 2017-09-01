/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.handlers;

import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.ArgumentWithValue;


/**
 *
 * @author Alexey Loubyansky
 */
public class EchoDMRHandler extends CommandHandlerWithHelp {

    private static final String CMD = "echo-dmr";
    public EchoDMRHandler() {
        super(CMD);

        new ArgumentWithValue(this, new CommandLineCompleter() {
                @Override
                public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

                    final String originalLine = ctx.getParsedCommandLine().getOriginalLine();
                    int i = originalLine.indexOf(CMD);
                    i = originalLine.indexOf(" ", i);
                    String cmd = "";
                    if (i != -1) {
                        for (; i < originalLine.length(); i++) {
                            if (originalLine.charAt(i) != ' ') {
                                break;
                            }
                        }
                        cmd = originalLine.substring(i);
                    }

                    int cmdResult = ctx.getDefaultCommandCompleter().complete(ctx, cmd, cmd.length(), candidates);
                    if(cmdResult < 0) {
                        return cmdResult;
                    }

                    // escaping index correction
                    int escapeCorrection = 0;
                    int start = originalLine.length() - 1 - buffer.length();
                    while(start - escapeCorrection >= 0) {
                        final char ch = originalLine.charAt(start - escapeCorrection);
                        if(Character.isWhitespace(ch) || ch == '=') {
                            break;
                        }
                        ++escapeCorrection;
                    }

                    /*
                    The CLI completer parses the command (echo-dmr is still an handler).
                    It calls echo-dmr completer with the last word and cursor at the end of this word.
                        - Received buffer is the last word. eg:
                            echo-dmr patch<TAB> ==> patch, cursor is 5
                            echo-dmr patch <TAB> ==> "", cursor is 0
                    The CLI completer will return the offset being the index of the start
                    of the passed buffer + the offset inside the passed buffer (computed by echo-dmr). E,.g.:
                            echo-dmr patch app<TAB> ==> 15 + 0. It means that the app is rewritten with the full value "apply"
                            echo-dmr :read-resource(pro<TAB> ==> 9 + 15 (index of 'p')
                     */
                    /*
                    * Sub command don't comply with the contract. Subcommands that are returned with the parent comment as prefix.
                    * Must remove the parent prefix if the cmd already contains the parent fully.
                    * For example echo-dmr d<TAB> ==> deployment XXX, echo-dmr deployment <TAB> ==> XXX
                     */
                    if (!cmd.isEmpty()) { // Empty command, means no sub command case
                        String sc = cmd.split(" ")[0];
                        boolean foundSubCommand = false;
                        for (int index = 0; index < candidates.size(); index++) {
                            String s = candidates.get(index);
                            String[] split = s.split(" ");
                            if (split.length > 1 && sc.equals(split[0])) {
                                if (split.length > 1) {
                                    s = split[split.length - 1];
                                    candidates.set(index, s);
                                    foundSubCommand = true;
                                }
                            }
                        }
                        if (foundSubCommand) {
                            return 0;
                        }
                    }

                    return buffer.length() + escapeCorrection - (cmd.length() - cmdResult);
                }}, Integer.MAX_VALUE, "--line") {
            };
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        // allow arbitrary arguments, it's up to the command or operation handler to validate them
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {
        String argsStr = ctx.getArgumentsString();
        if(argsStr == null) {
            throw new CommandFormatException("Missing the command or operation to translate to DMR.");
        }
        ctx.printLine(ctx.buildRequest(argsStr).toString());
    }
}
