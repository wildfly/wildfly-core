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
package org.jboss.as.cli.impl.aesh.commands.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Option;
import org.aesh.readline.Prompt;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.aesh.converter.ListConverter;
import org.jboss.as.cli.impl.aesh.converter.MapConverter;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.FileCompleter;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-load-plugins", description = "")
public abstract class AbstractLoadCommand implements Command<CLICommandInvocation> {

    public static class CommandNamesCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation t) {
            String buffer = t.getGivenCompleteValue();
            int length = buffer.length();
            int i = buffer.lastIndexOf(",");
            // Offset is from the end of the buffer.
            int offset = 0;
            if (i == -1) {
                offset = buffer.length();
            } else if (i == buffer.length() - 1) {
                buffer = "";
                offset = 0;
            } else {
                buffer = buffer.substring(i + 1);
                offset = length - i - 1;
            }

            List<String> candidates = new ArrayList<>();
            AbstractLoadCommand cmd = (AbstractLoadCommand) t.getCommand();
            String path = cmd.path;
            if (path == null) {
                return;
            }
            Set<String> values = null;
            try {
                values = cmd.ctx.listPlugins(new File(path), cmd.getModuleName());
            } catch (Exception ex) {
                // XXX OK, ignore.
                return;
            }

            if (buffer == null || buffer.isEmpty()) {
                candidates.addAll(values);
            } else {
                for (String v : values) {
                    if (v.equals(buffer)) {
                        candidates.add(v + ",");
                        break;
                    } else if (v.startsWith(buffer)) {
                        candidates.add(v);
                    }
                }
            }
            Collections.sort(candidates);
            t.addAllCompleterValues(candidates);
            t.setOffset(offset);
            t.setAppendSpace(false);
        }
    }

    @Option(hasValue = true, completer = FileCompleter.class, required = true)
    protected String path;

    protected final CommandContextImpl ctx;

    @Option(name = "command-names", required = false, converter = ListConverter.class,
            completer = CommandNamesCompleter.class)
    protected List<String> names;

    @Option(name = "name-mapping", required = false, converter = MapConverter.class)
    protected Map<String, String> nameMapping;

    AbstractLoadCommand(CommandContextImpl ctx) {
        this.ctx = ctx;
    }

    protected String getModuleName() {
        return null;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        try {
            Set<String> possiblePlugins;
            if (names == null || names.isEmpty()) {
                possiblePlugins = ctx.listPlugins(new File(path), null);
            } else {
                possiblePlugins = new HashSet<>(names);
            }
            Set<String> skip = new HashSet<>();
            if (nameMapping == null || nameMapping.isEmpty()) {
                nameMapping = new HashMap<>();
                for (String p : possiblePlugins) {
                    try {
                        commandInvocation.getCommandRegistry().getCommand(p, null);
                    } catch (CommandNotFoundException ex) {
                        continue;
                    }
                    boolean done = false;
                    while (!done) {
                        String res = commandInvocation.
                                inputLine(new Prompt("A command named " + p + " already exists, "
                                        + "do you want to rename the plugin [y/n]?"));
                        if (!res.equals("n") && !res.equals("y")) {
                            commandInvocation.getCommandContext().
                                    println("Invalid reply, must be y or n.");
                            continue;
                        }
                        done = true;
                        if (res.equalsIgnoreCase("y")) {
                            boolean okName = false;
                            String name = null;
                            while (!okName) {
                                name = commandInvocation.
                                        inputLine(new Prompt("Original name " + p
                                                + ", new name:"));
                                try {
                                    commandInvocation.getCommandRegistry().getCommand(name, null);
                                } catch (CommandNotFoundException ex) {
                                    okName = true;
                                    continue;
                                }
                                commandInvocation.getCommandContext().
                                        println("Name " + name + " already exists!");
                            }
                            nameMapping.put(p, name);
                        } else {
                            skip.add(p);
                        }
                    }
                }
            } else {

            }
            ctx.loadPlugins(new File(path), getModuleName(), names, skip, nameMapping);
        } catch (Exception ex) {
            throw new CommandException(ex);
        }
        return CommandResult.SUCCESS;
    }
}
