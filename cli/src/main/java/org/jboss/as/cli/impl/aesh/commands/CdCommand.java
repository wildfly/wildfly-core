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
package org.jboss.as.cli.impl.aesh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.converter.Converter;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.LsHandler;
import org.jboss.as.cli.impl.aesh.commands.activator.ConnectedActivator;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 * A Command to change the current node path.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "cd", description = "", aliases = {"cn"}, activator = ConnectedActivator.class)
public class CdCommand implements Command<CLICommandInvocation> {

    public static class PathOptionCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation cliCompleterInvocation) {
            List<String> candidates = new ArrayList<>();
            int pos = 0;
            if (cliCompleterInvocation.getGivenCompleteValue() != null) {
                pos = cliCompleterInvocation.getGivenCompleteValue().length();
            }

            int cursor = OperationRequestCompleter.ARG_VALUE_COMPLETER.
                    complete(cliCompleterInvocation.getCommandContext(),
                            cliCompleterInvocation.getGivenCompleteValue(), pos, candidates);
            cliCompleterInvocation.addAllCompleterValues(candidates);
            cliCompleterInvocation.setOffset(cliCompleterInvocation.getGivenCompleteValue().length() - cursor);
            cliCompleterInvocation.setAppendSpace(false);
        }
    }

    public static class OperationRequestAddressConverter implements Converter<OperationRequestAddress, CLIConverterInvocation> {

        @Override
        public OperationRequestAddress convert(CLIConverterInvocation converterInvocation) throws OptionValidatorException {
            return convert(converterInvocation.getInput(),
                    converterInvocation.getCommandContext());
        }

        public static OperationRequestAddress convert(String path, CommandContext ctx) throws OptionValidatorException {
            final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();
            try {
                parsedOp.parseOperation(ctx.getCurrentNodePath(), path);
                return parsedOp.getAddress();
            } catch (CommandFormatException e) {
                throw new OptionValidatorException(e.toString());
            }
        }
    }

    @Argument(completer = PathOptionCompleter.class,
            converter = OperationRequestAddressConverter.class)
    private OperationRequestAddress nodePath;

    @Option(name = "no-validation", hasValue = false)
    private boolean noValidation;

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("cd"));
            return CommandResult.SUCCESS;
        }
        CommandContext ctx = commandInvocation.getCommandContext();
        OperationRequestAddress prefix = ctx.getCurrentNodePath();

        if (nodePath == null) {
            ctx.printLine(ctx.getNodePathFormatter().format(prefix));
            return CommandResult.SUCCESS;
        }
        final OperationRequestAddress tmp = new DefaultOperationRequestAddress(prefix);
        try {
            String args = LsHandler.getNodePath(ctx.getArgumentsString());
            ctx.getCommandLineParser().parse(args, new DefaultCallbackHandler(tmp));
            if (!noValidation) {
                assertValid(ctx, tmp);
            }
        } catch (CommandLineException ex) {
            throw new CommandException(ex);
        }

        prefix.reset();
        for (OperationRequestAddress.Node node : tmp) {
            if (node.getName() != null) {
                prefix.toNode(node.getType(), node.getName());
            } else {
                prefix.toNodeType(node.getType());
            }
        }
        return CommandResult.SUCCESS;
    }

    public static void assertValid(CommandContext ctx, OperationRequestAddress addr) throws CommandLineException {
        ModelNode req = new ModelNode();
        req.get(Util.ADDRESS).setEmptyList();
        req.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
        final ModelNode addressValue = req.get(Util.VALUE);
        String lastType = null;
        if (addr.isEmpty()) {
            addressValue.setEmptyList();
        } else {
            for (OperationRequestAddress.Node node : addr) {
                if (node.getName() != null) {
                    addressValue.add(node.getType(), node.getName());
                } else {
                    lastType = node.getType();
                }
            }
        }
        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(req);
        } catch (IOException e) {
            throw new CommandLineException("Failed to validate address.", e);
        }
        ModelNode result = response.get(Util.RESULT);
        if (!result.isDefined()) {
            throw new CommandLineException("Failed to validate address: the response from the controller doesn't contain result.");
        }
        final ModelNode valid = result.get(Util.VALID);
        if (!valid.isDefined()) {
            throw new CommandLineException("Failed to validate address: the result doesn't contain 'valid' property.");
        }
        if (!valid.asBoolean()) {
            final String msg;
            if (result.hasDefined(Util.PROBLEM)) {
                msg = result.get(Util.PROBLEM).asString();
            } else {
                msg = "Invalid target address.";
            }
            throw new CommandLineException(msg);
        }

        if (lastType != null) {
            req = new ModelNode();
            req.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            final ModelNode addrNode = req.get(Util.ADDRESS);
            if (addr.isEmpty()) {
                addrNode.setEmptyList();
            } else {
                for (OperationRequestAddress.Node node : addr) {
                    if (node.getName() != null) {
                        addrNode.add(node.getType(), node.getName());
                    }
                }
            }
            try {
                response = ctx.getModelControllerClient().execute(req);
            } catch (IOException e) {
                throw new CommandLineException("Failed to validate address.", e);
            }
            result = response.get(Util.RESULT);
            if (!result.isDefined()) {
                throw new CommandLineException("Failed to validate address: the response from the controller doesn't contain result.");
            }
            for (ModelNode type : result.asList()) {
                if (lastType.equals(type.asString())) {
                    return;
                }
            }
            throw new CommandLineException("Invalid target address: " + lastType + " doesn't exist.");
        }
    }
}
