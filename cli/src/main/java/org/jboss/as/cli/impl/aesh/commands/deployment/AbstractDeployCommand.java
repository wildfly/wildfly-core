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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.impl.aesh.completer.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.converter.HeadersConverter;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators.AllServerGroupsActivator;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators.ServerGroupsActivator;
import org.jboss.as.cli.impl.aesh.parser.HeadersParser;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.BatchCompliantCommand;

/**
 * XXX jfdenise, all fields are public to be accessible from legacy view. To be
 * made private when removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deployment-deploy", description = "")
public abstract class AbstractDeployCommand extends CommandWithPermissions implements BatchCompliantCommand {

    public static class ServerGroupsCompleter implements
            OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            CommandWithPermissions rc = (CommandWithPermissions) completerInvocation.getCommand();

            CommaSeparatedCompleter comp = new CommaSeparatedCompleter() {
                @Override
                protected Collection<String> getAllCandidates(CommandContext ctx) {
                    return rc.getPermissions().getServerGroupAddPermission().
                            getAllowedOn(ctx);
                }
            };
            List<String> candidates = new ArrayList<>();
            int offset = comp.complete(completerInvocation.getCommandContext(),
                    completerInvocation.getGivenCompleteValue(), 0, candidates);
            completerInvocation.addAllCompleterValues(candidates);
            completerInvocation.setOffset(offset);
        }
    }

    @Option(name = "server-groups", activator = ServerGroupsActivator.class,
            completer = ServerGroupsCompleter.class, required = false)
    public String serverGroups;

    @Option(name = "all-server-groups", activator = AllServerGroupsActivator.class,
            hasValue = false, required = false)
    public boolean allServerGroups;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class,
            parser = HeadersParser.class,
            required = false)
    public ModelNode headers;

    AbstractDeployCommand(CommandContext ctx, Function<CommandContext, AccessRequirement> acBuilder,
            Permissions permissions) {
        super(ctx, acBuilder, permissions);
    }

    @Override
    public BatchResponseHandler buildBatchResponseHandler(CommandContext commandContext,
            Attachments attachments) {
        return null;
    }
}
