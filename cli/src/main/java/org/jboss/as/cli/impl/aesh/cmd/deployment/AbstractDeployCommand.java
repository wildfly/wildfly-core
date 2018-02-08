/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
