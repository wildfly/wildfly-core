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

package org.jboss.as.cli.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.aesh.command.Command;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.parser.CommandLineParserException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 *
 * @author Alexey Loubyansky
 */
class ExtensionsLoader {

    private final ModuleLoader moduleLoader;
    private final CommandContext ctx;
    private final CommandRegistry registry;
    private final CLICommandRegistry aeshRegistry;

    /** current address from which the commands are loaded, this could be null */
    private ControllerAddress currentAddress;
    /**
     * handlers loaded from the current address
     */
    private List<String> loadedHandlers = Collections.emptyList();
    /**
     * commands loaded from the current address
     */
    private List<String> loadedCommands = Collections.emptyList();
    /** error messages collected during loading the commands */
    private List<String> errors = Collections.emptyList();

    ExtensionsLoader(CommandRegistry registry, CLICommandRegistry aeshRegistry,
            CommandContext ctx) throws CommandLineException {
        assert registry != null : "command registry is null";
        assert ctx != null : "command context is null";

        if(getClass().getClassLoader() instanceof ModuleClassLoader) {
            moduleLoader = ModuleLoader.forClassLoader(getClass().getClassLoader());
        } else {
            moduleLoader = null;
        }

        this.ctx = ctx;
        this.registry = registry;
        this.aeshRegistry = aeshRegistry;

        registry.registerHandler(new ExtensionCommandsHandler(), false, ExtensionCommandsHandler.NAME);
    }

    void resetHandlers() {
        for (String cmd : loadedHandlers) {
            registry.remove(cmd);
        }
        for (String cmd : loadedCommands) {
            aeshRegistry.removeCommand(cmd);
        }
        currentAddress = null;
        errors = Collections.emptyList();
        loadedHandlers = Collections.emptyList();
        loadedCommands = Collections.emptyList();
    }

    Set<String> getExtensions() {
        Set<String> all = new HashSet<>(loadedHandlers);
        all.addAll(loadedCommands);
        return all;
    }

    List<String> getExtensionsErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Using the client, iterates through the available domain management model extensions
     * and tries to load CLI command handlers from their modules.
     *
     * @param registry
     * @param address
     * @param client
     */
    void loadHandlers(ControllerAddress address) throws CommandLineException, CommandLineParserException {

        ModelControllerClient client = ctx.getModelControllerClient();
        assert client != null : "client is null";

        if(moduleLoader == null) {
            ctx.printLine("Warning! The CLI is running in a non-modular environment and cannot load commands from management extensions.");
            return;
        }

        if(address != null && currentAddress != null && address.equals(currentAddress)) {
            return;
        }

        // remove previously loaded commands
        resetHandlers();
        currentAddress = address;

        final ModelNode req = new ModelNode();
        req.get(Util.ADDRESS).setEmptyList();
        req.get(Util.OPERATION).set(Util.READ_CHILDREN_RESOURCES);
        req.get(Util.CHILD_TYPE).set(Util.EXTENSION);

        final ModelNode response;
        try {
            response = client.execute(req);
        } catch (IOException e) {
            throw new CommandLineException("Extensions loader failed to read extensions", e);
        }

        if(!Util.isSuccess(response)) {
            throw new CommandLineException("Extensions loader failed to read extensions: " + Util.getFailureDescription(response));
        }

        final ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            throw new CommandLineException("Extensions loader failed to read extensions: " + result.asString());
        }

        for(Property ext : result.asPropertyList()) {
            ModelNode module = ext.getValue().get(Util.MODULE);
            if(!module.isDefined()) {
                addError("Extension " + ext.getName() + " is missing module attribute");
            } else {
                final ModuleIdentifier moduleId = ModuleIdentifier.fromString(module.asString());
                ModuleClassLoader cl;
                try {
                    cl = moduleLoader.loadModule(moduleId).getClassLoader();
                    ServiceLoader<CommandHandlerProvider> loader = ServiceLoader.load(CommandHandlerProvider.class, cl);
                    for (CommandHandlerProvider provider : loader) {
                        try {
                            registry.registerHandler(provider.createCommandHandler(ctx), provider.isTabComplete(), provider.getNames());
                            addHandlers(Arrays.asList(provider.getNames()));
                        } catch(CommandRegistry.RegisterHandlerException e) {
                            addError(e.getLocalizedMessage());
                            final List<String> addedCommands = new ArrayList<String>(Arrays.asList(provider.getNames()));
                            addedCommands.removeAll(e.getNotAddedNames());
                            addHandlers(addedCommands);
                        }
                    }
                    ServiceLoader<Command> loader2 = ServiceLoader.load(Command.class, cl);
                    for (Command provider : loader2) {
                        try {
                            CommandContainer container = aeshRegistry.addCommand(provider);
                            addCommand(container.getParser().getProcessedCommand().name());
                        } catch (CommandLineException e) {
                            addError(e.getLocalizedMessage());
                        }
                    }
                } catch (ModuleLoadException e) {
                    addError("Module " + module.asString() + " from extension " + ext.getName() +
                            " available on the server couldn't be loaded locally: " + e.getLocalizedMessage());
                }
            }
        }

        if(!errors.isEmpty()) {
            ctx.printLine("Warning! There were errors trying to load extensions. For more details, please, execute 'extension-commands --errors'");
        }
    }

    private void addHandlers(List<String> names) {
        if (loadedHandlers.isEmpty()) {
            loadedHandlers = new ArrayList<>();
        }
        loadedHandlers.addAll(names);
    }

    private void addCommand(String name) {
        if (loadedCommands.isEmpty()) {
            loadedCommands = new ArrayList<>();
        }
        loadedCommands.add(name);
    }

    private void addError(String msg) {
        switch(errors.size()) {
            case 0:
                errors = Collections.singletonList(msg);
                break;
            case 1:
                errors = new ArrayList<String>(errors);
            default:
                errors.add(msg);
        }
    }

    class ExtensionCommandsHandler extends CommandHandlerWithHelp {

        private static final String NAME = "extension-commands";

        private final ArgumentWithoutValue errorsArg = new ArgumentWithoutValue(this, "--errors");

        ExtensionCommandsHandler() {
            super(NAME);
        }

        @Override
        protected void doHandle(CommandContext ctx) throws CommandLineException {
            if (errorsArg.isPresent(ctx.getParsedCommandLine()) && !errors.isEmpty()) {
                final StringBuilder buf = new StringBuilder();
                buf.append("The following problems were encountered while looking for additional commands in extensions:\n");
                for (int i = 0; i < errors.size(); ++i) {
                    final String error = errors.get(i);
                    buf.append(i + 1).append(") ").append(error).append(Util.LINE_SEPARATOR);
                }
                ctx.printLine(buf.toString());
            }
        }
    }
}
