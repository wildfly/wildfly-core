/*
Copyright 2018 Red Hat, Inc.

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
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import java.util.HashSet;
import java.util.Set;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;

/**
 * Base class for any command that disables Auth.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-disable", description = "")
public abstract class AbstractDisableAuthenticationCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_NO_RELOAD, hasValue = false)
    boolean noReload;

    private final AuthFactorySpec factorySpec;

    public AbstractDisableAuthenticationCommand(AuthFactorySpec factorySpec) {
        this.factorySpec = factorySpec;
    }

    public AuthFactorySpec getFactorySpec() {
        return factorySpec;
    }

    public abstract String getEnabledFactory(CommandContext ctx) throws Exception;

    protected abstract ModelNode disableFactory(CommandContext context) throws Exception;

    protected abstract String getSecuredEndpoint(CommandContext ctx);

    protected abstract String getMechanism();

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        ModelNode request;
        try {
            request = buildSecurityRequest(ctx);
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }

        SecurityCommand.execute(ctx, request, SecurityCommand.DEFAULT_FAILURE_CONSUMER, noReload);
        commandInvocation.getCommandContext().printLine("Command success.");
        if (getMechanism() == null) {
            commandInvocation.getCommandContext().printLine(factorySpec.getName()
                    + " authentication disabled for " + getSecuredEndpoint(commandInvocation.getCommandContext()));
        } else {
            commandInvocation.getCommandContext().printLine("Mechanism removed from " + factorySpec.getName()
                    + " for " + getSecuredEndpoint(commandInvocation.getCommandContext()));
        }

        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        try {
            return buildSecurityRequest(context);
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage());
        }
    }

    public ModelNode buildSecurityRequest(CommandContext context) throws Exception {
        String authFactory = validateOptions(context);
        ModelNode mn = ElytronUtil.getAuthFactoryResource(authFactory, factorySpec, context);
        if (mn == null) {
            throw new CommandException("Invalid factory " + authFactory);
        }
        if (getMechanism() == null) {
            return disableFactory(context);
        }
        Set<String> set = new HashSet<>();
        set.add(getMechanism());
        return ElytronUtil.removeMechanisms(context, mn, authFactory, factorySpec, set);
    }

    private String validateOptions(CommandContext ctx) throws Exception {
        String factory = getEnabledFactory(ctx);
        if (factory == null) {
            throw new CommandException(factorySpec.getName() + " authentication is not enabled.");
        }
        return factory;
    }

}
