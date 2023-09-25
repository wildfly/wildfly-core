/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandRegistry {

    private final Map<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
    private final Set<String> tabCompletionCommands = new HashSet<String>();

    public void registerHandler(CommandHandler handler, String... names) throws CommandLineException {
        registerHandler(handler, true, names);
    }

    public void registerHandler(CommandHandler handler, boolean tabComplete, String... names) throws RegisterHandlerException {
        String tabCompleteName = null;
        RegisterHandlerException error = null;
        for(String name : names) {
            if(handlers.containsKey(name)) {
                if(error == null) {
                    error = new RegisterHandlerException(name);
                } else {
                    error.addCommand(name);
                }
            } else {
                if(tabCompleteName == null) {
                    tabCompleteName = name;
                }
                handlers.put(name, handler);
            }
        }

        if(tabComplete && tabCompleteName != null) {
            tabCompletionCommands.add(tabCompleteName);
        }

        if(error != null) {
            throw error;
        }
    }

    public Set<String> getTabCompletionCommands() {
        return tabCompletionCommands;
    }

    public CommandHandler getCommandHandler(String command) {
        return handlers.get(command);
    }

    public CommandHandler remove(String cmdName) {
        checkNotNullParam("cmdName", cmdName);
        CommandHandler handler = handlers.remove(cmdName);
        if(handler != null) {
            tabCompletionCommands.remove(cmdName);
        }
        return handler;
    }

    public static class RegisterHandlerException extends CommandLineException {

        private static final long serialVersionUID = 1L;

        private List<String> names;

        public RegisterHandlerException(String commandName) {
            super("");
            names = Collections.singletonList(commandName);
        }

        public void addCommand(String name) {
            if(names.size() == 1) {
                names = new ArrayList<String>(names);
            }
            names.add(name);
        }

        public List<String> getNotAddedNames() {
            return Collections.unmodifiableList(names);
        }

        @Override
        public String getMessage() {
            final StringBuilder buf = new StringBuilder("The following command names could not be registered since they conflict with the already registered ones: ");
            buf.append(names.get(0));
            for(int i = 1; i < names.size(); ++i) {
                buf.append(", ").append(names.get(i));
            }
            return buf.toString();
        }
    }
}
