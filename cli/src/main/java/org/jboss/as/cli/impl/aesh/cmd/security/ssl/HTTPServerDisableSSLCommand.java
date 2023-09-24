/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DEFAULT_SERVER_SSL_CONTEXT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_HTTPS_LISTENER_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_REMOVE_HTTPS_LISTENER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SERVER_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-ssl-http-server", description = "", activator = HttpServerCommandActivator.class)
public class HTTPServerDisableSSLCommand implements Command<CLICommandInvocation>, DMRCommand {
    @Option(name = OPT_NO_RELOAD, hasValue = false)
    boolean noReload;

    @Option(name = OPT_SERVER_NAME, completer = OptionCompleters.ServerNameCompleter.class)
    String serverName;

    @Option(name = OPT_REMOVE_HTTPS_LISTENER,
            hasValue = false)
    boolean removeHttpsListener;

    @Option(name = OPT_HTTPS_LISTENER_NAME,
            completer = OptionCompleters.HTTPSListenerCompleter.class,
            defaultValue = Util.HTTPS)
    String httpsListener;

    @Option(name = OPT_DEFAULT_SERVER_SSL_CONTEXT,
            hasValue = true, defaultValue = Util.APPLICATION_SERVER_SSL_CONTEXT)
    String defaultAppSSLContext;

    public String getServerName(CommandContext ctx) {
        String sName = serverName;
        if (sName == null) {
            sName = DefaultResourceNames.getDefaultServerName(ctx);
        }
        return sName;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        try {
            serverName = getServerName(ctx);
            if (!HTTPServer.hasHttpsListener(ctx, serverName, httpsListener)) {
                throw new CommandException("No HTTPS Listener named "+ httpsListener + " found in " + serverName);
            }
        } catch (OperationFormatException | IOException ex) {
            throw new CommandException(ex);
        }
        ModelNode request;
        try {
            request = buildRequest(ctx);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex.getLocalizedMessage(), ex);
        }
        SecurityCommand.execute(ctx, request, SecurityCommand.DEFAULT_FAILURE_CONSUMER, noReload);
        ctx.printLine("SSL disabled for " + serverName);
        if (removeHttpsListener) {
            ctx.printLine("HTTPS listener " + httpsListener + " has been removed");
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        try {
            // Check that we are not disabling the default one.
            if (!HTTPServer.isLegacySecurityRealmSupported(context) && !removeHttpsListener) {
                if (defaultAppSSLContext.equals(HTTPServer.getSSLContextName(serverName, httpsListener, context))) {
                    throw new CommandFormatException("The SSL Context " + defaultAppSSLContext + " is already set on "
                            + httpsListener + " HTTPS listener");
                }
            }
            serverName = HTTPServer.disableSSL(context,
                    serverName, removeHttpsListener, httpsListener, defaultAppSSLContext, composite.get(Util.STEPS));
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage(), ex);
        }
        return composite;
    }

}
