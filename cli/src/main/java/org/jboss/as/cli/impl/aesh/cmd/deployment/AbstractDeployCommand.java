/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.util.List;
import java.util.function.Function;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.jboss.as.cli.impl.aesh.cmd.AbstractCommaCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.AllServerGroupsActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.ServerGroupsActivator;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.BatchCompliantCommand;

/**
 * Base class for deployment deploy commands that have an header, server-groups
 * and all-server-groups options. All fields are public to be accessible from
 * legacy commands. To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deployment-deploy", description = "")
public abstract class AbstractDeployCommand extends CommandWithPermissions implements BatchCompliantCommand {

    public static class ServerGroupsCompleter extends AbstractCommaCompleter {

        @Override
        protected List<String> getItems(CLICompleterInvocation completerInvocation) {
            CommandWithPermissions rc = (CommandWithPermissions) completerInvocation.getCommand();
            return rc.getPermissions().getServerGroupAddPermission().
                    getAllowedOn(completerInvocation.getCommandContext());
        }
    }

    @Option(name = "server-groups", activator = ServerGroupsActivator.class,
            completer = ServerGroupsCompleter.class, required = false)
    public String serverGroups;

    @Option(name = "all-server-groups", activator = AllServerGroupsActivator.class,
            hasValue = false, required = false)
    public boolean allServerGroups;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class,
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
