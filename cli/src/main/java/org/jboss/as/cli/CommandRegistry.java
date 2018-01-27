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
package org.jboss.as.cli;

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
        if(cmdName == null) {
            throw new IllegalArgumentException();
        }
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
