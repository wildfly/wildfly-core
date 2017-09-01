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
package org.jboss.as.cli.impl.aesh.commands.deployment;

import org.jboss.as.cli.impl.aesh.commands.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Permissions;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.commands.security.ControlledCommandActivator;
import org.jboss.as.cli.util.StrictSizeTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
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
        ctx.println(table.toString());
    }

    private static void printList(CommandContext ctx,
            Collection<String> list, boolean l) {
        if (l) {
            for (String item : list) {
                ctx.println(item);
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
            return null;
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
