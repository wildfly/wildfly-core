/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestHeader;


/**
 *
 * @author Alexey Loubyansky
 */
public class CommandCandidatesProvider implements OperationCandidatesProvider {

    private final CommandRegistry registry;

    public CommandCandidatesProvider(CommandRegistry registry) {
        this.registry = checkNotNullParam("registry", registry);
    }

    @Override
    public List<String> getNodeNames(CommandContext ctx, OperationRequestAddress prefix) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getNodeTypes(CommandContext ctx, OperationRequestAddress prefix) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix) {
        final List<String> commands = new ArrayList<String>();
        for(String command : registry.getTabCompletionCommands()) {
            CommandHandler handler = registry.getCommandHandler(command);
            if(handler.isAvailable(ctx)) {
                commands.add(command);
            }
        }
        return commands;
    }

    @Override
    public Collection<CommandArgument> getProperties(CommandContext ctx, String operationName, OperationRequestAddress address) {
        CommandHandler handler = registry.getCommandHandler(operationName);
        if(handler == null) {
            return Collections.emptyList();
        }
        return handler.isAvailable(ctx) ? handler.getArguments(ctx) : Collections.emptyList();
    }

    @Override
    public Map<String, OperationRequestHeader> getHeaders(CommandContext ctx) {
        return Collections.emptyMap(); // TODO need to implement this for commands
    }
}
