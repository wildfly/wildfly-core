/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.util.StrictSizeTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 * List deployments.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "list", description = "", activator = ControlledCommandActivator.class)
public class ListCommand extends CommandWithPermissions {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option(name = "l", hasValue = false, shortName = 'l')
    private boolean l;

    ListCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.listAccess(permissions), permissions);
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment list"));
            return CommandResult.SUCCESS;
        }
        listDeployments(commandInvocation.getCommandContext(), l);
        return CommandResult.SUCCESS;
    }

    public static void listDeployments(CommandContext ctx,
            boolean l) throws CommandException {
        if (!l) {
            printList(ctx, Util.getDeployments(ctx.
                    getModelControllerClient()), l);
            return;
        }
        final ModelControllerClient client = ctx.getModelControllerClient();
        final List<String> names = Util.getDeployments(client);
        if (names.isEmpty()) {
            return;
        }

        final StrictSizeTable table = new StrictSizeTable(names.size());
        final List<Property> descriptions = getDeploymentDescriptions(ctx, names).
                asPropertyList();
        for (Property prop : descriptions) {
            final ModelNode step = prop.getValue();
            if (step.hasDefined(Util.RESULT)) {
                final ModelNode result = step.get(Util.RESULT);
                table.addCell(Util.NAME, result.get(Util.NAME).asString());
                table.addCell(Util.RUNTIME_NAME, result.get(Util.RUNTIME_NAME).
                        asString());
                if (result.has(Util.ENABLED)) {
                    table.addCell(Util.ENABLED, result.get(Util.ENABLED).asString());
                }
                if (result.has(Util.PERSISTENT)) {
                    table.addCell(Util.PERSISTENT, result.get(Util.PERSISTENT).asString());
                }
                if (result.has(Util.STATUS)) {
                    table.addCell(Util.STATUS, result.get(Util.STATUS).asString());
                }
            }
            if (!table.isAtLastRow()) {
                table.nextRow();
            }
        }
        ctx.printLine(table.toString());
    }

    private static void printList(CommandContext ctx,
            Collection<String> list, boolean l) {
        if (l) {
            for (String item : list) {
                ctx.printLine(item);
            }
        } else {
            ctx.printColumns(list);
        }
    }

    private static ModelNode getDeploymentDescriptions(CommandContext ctx,
            List<String> names) throws CommandException {
        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);
        for (String name : names) {
            final ModelNode deploymentResource = buildReadDeploymentResourceRequest(name);
            if (deploymentResource != null) {
                steps.add(deploymentResource);
            }// else it's illegal state
        }
        ModelNode result;
        try {
            result = ctx.getModelControllerClient().execute(composite);
        } catch (IOException e) {
            throw new CommandException("Failed to execute operation request.", e);
        }
        if (!result.hasDefined(Util.RESULT)) {
            throw new CommandException("Failed to read deployment information.");
        }
        return result.get(Util.RESULT);
    }

    private static ModelNode buildReadDeploymentResourceRequest(String name) {
        ModelNode request = new ModelNode();
        ModelNode address = request.get(Util.ADDRESS);
        address.add(Util.DEPLOYMENT, name);
        request.get(Util.OPERATION).set(Util.READ_RESOURCE);
        request.get(Util.INCLUDE_RUNTIME).set(true);
        return request;
    }
}
