/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.option.Argument;
import org.jboss.as.cli.CommandContext.TIMEOUT_RESET_VALUE;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "reset", description = "")
public class CommandTimeoutReset implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Argument(completer = TimeoutCompleter.class)
    private String value;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("command-timeout reset"));
            return CommandResult.SUCCESS;
        }
        if (value == null || value.isEmpty()) {
            throw new CommandException("No value to reset");
        }
        TIMEOUT_RESET_VALUE resetValue = Enum.valueOf(TIMEOUT_RESET_VALUE.class, value.toUpperCase());
        commandInvocation.getCommandContext().resetTimeout(resetValue);
        return CommandResult.SUCCESS;
    }

    public static class TimeoutCompleter implements OptionCompleter {

        @Override
        public void complete(CompleterInvocation t) {
            String buffer = t.getGivenCompleteValue();
            List<String> candidates = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (TIMEOUT_RESET_VALUE tr : TIMEOUT_RESET_VALUE.values()) {
                values.add(tr.name().toLowerCase());
            }
            if (buffer == null || buffer.isEmpty()) {
                candidates.addAll(values);
            } else {
                for (String v : values) {
                    if (v.equals(buffer)) {
                        candidates.add(v + " ");
                        break;
                    } else if (v.startsWith(buffer)) {
                        candidates.add(v);
                    }
                }
            }
            Collections.sort(candidates);
            t.addAllCompleterValues(candidates);
        }
    }
}
