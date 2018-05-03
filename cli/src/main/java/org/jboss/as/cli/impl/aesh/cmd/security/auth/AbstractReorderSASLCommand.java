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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISMS_ORDER;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;

/**
 * SASL mechanism re-ordering. SASL protocol use the first realm present in a
 * factory. Order is of importance. NB: HTTP Auth is not subject to realm
 * ordering, all realms are sent to the client.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-sasl-re-order", description = "")
public abstract class AbstractReorderSASLCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_MECHANISMS_ORDER, required = true,
            completer = OptionCompleters.MechanismsCompleter.class)
    String mechanismsOrder;

    @Option(name = OPT_NO_RELOAD, hasValue = false)
    boolean noReload;

    public abstract String getSASLFactoryName(CommandContext ctx) throws IOException, OperationFormatException;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        try {
            SecurityCommand.execute(commandInvocation.getCommandContext(),
                    buildRequest(commandInvocation.getCommandContext()), SecurityCommand.DEFAULT_FAILURE_CONSUMER, noReload);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        if (mechanismsOrder != null) {
            String[] mecs = mechanismsOrder.split(",");
            List<String> lst = new ArrayList<>();
            for (String mec : mecs) {
                lst.add(mec.trim());
            }
            try {
                AuthSecurityBuilder builder = buildSecurityBuilder(context, lst);
                builder.buildRequest(context);
                return builder.getRequest();
            } catch (Exception ex) {
                throw new CommandFormatException(ex.getLocalizedMessage());
            }
        } else {
            throw new CommandFormatException("Mechanism order must be provided.");
        }
    }

    private AuthSecurityBuilder buildSecurityBuilder(CommandContext ctx, List<String> order) throws Exception {
        String existingFactory = getSASLFactoryName(ctx);
        if (existingFactory == null) {
            throw new CommandFormatException("No SASL Factory to re-order.");
        }
        AuthSecurityBuilder builder = new AuthSecurityBuilder(order);
        builder.setActiveFactoryName(existingFactory);
        return builder;
    }

}
