/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_ADD_HTTPS_LISTENER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_HTTPS_LISTENER_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_HTTPS_LISTENER_SOCKET_BINDING_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_OVERRIDE_SECURITY_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_OVERRIDE_SSL_CONTEXT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SERVER_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.SSLSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * Command to enable SSL for a given undertow server.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable-ssl-http-server", description = "", activator = HttpServerCommandActivator.class)
public class HTTPServerEnableSSLCommand extends AbstractEnableSSLCommand {

    @Option(name = OPT_SERVER_NAME, completer = OptionCompleters.ServerNameCompleter.class)
    String serverName;

    @Option(name = OPT_NO_OVERRIDE_SECURITY_REALM,
            activator = OptionActivators.NoOverrideSecurityRealmActivator.class,
            hasValue = false)
    boolean noOverride;

    @Option(name = OPT_OVERRIDE_SSL_CONTEXT,
            hasValue = false)
    boolean overrideSSLContext;

    @Option(name = OPT_ADD_HTTPS_LISTENER,
            hasValue = false)
    boolean addHttpsListener;

    @Option(name = OPT_HTTPS_LISTENER_NAME,
            completer= OptionCompleters.NewHTTPSListenerCompleter.class,
            hasValue = true, defaultValue = Util.HTTPS)
    String httpsListener;

    @Option(name = OPT_HTTPS_LISTENER_SOCKET_BINDING_NAME,
            activator = OptionActivators.DependsOnAddHttpsListenerActivator.class,
            hasValue = true, defaultValue = Util.HTTPS, completer = OptionCompleters.SocketBindingCompleter.class)
    String httpsListenerSocketBinding;

    public HTTPServerEnableSSLCommand(CommandContext ctx) {
        super(ctx);
    }

    public boolean hasAddHTTPSListener() {
        return addHttpsListener;
    }

    public String getServerName(CommandContext ctx) {
        String sName = serverName;
        if (sName == null) {
            sName = DefaultResourceNames.getDefaultServerName(ctx);
        }
        return sName;
    }

    @Override
    protected void secure(CommandContext ctx, SSLSecurityBuilder builder) throws CommandException {
        try {
            HTTPServer.enableSSL(serverName, addHttpsListener, httpsListener, httpsListenerSocketBinding, noOverride, ctx, builder);
        } catch (Exception ex) {
            throw new CommandException(ex);
        }
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandResult result = super.execute(commandInvocation);
         if (addHttpsListener) {
            commandInvocation.getCommandContext().printLine("HTTPS listener " + httpsListener + " has been added");
        }
         return result;
    }

    @Override
    protected boolean isSSLEnabled(CommandContext ctx) throws Exception {
        String target = getServerName(ctx);
        if (HTTPServer.hasHttpsListener(ctx, target, httpsListener)) {
            if (HTTPServer.getSSLContextName(target, httpsListener, ctx) != null) {
                if (!overrideSSLContext) {
                    throw new Exception("An SSL server context already exists on the HTTPS listener, use --" +
                            OPT_OVERRIDE_SSL_CONTEXT + " option to overwrite the existing SSL context");
                }
            }
        } else {
            if (!addHttpsListener) {
                throw new Exception("No HTTPS listener found, you must use --" + OPT_ADD_HTTPS_LISTENER +
                        " option to add an https listener to " + target + " server.");
            }
        }
        return false;
    }

    @Override
    protected String getTarget(CommandContext ctx) {
        String target = serverName;
        if (target == null) {
            target = DefaultResourceNames.getDefaultServerName(ctx);
        }
        return target;
    }

    @Override
    String getDefaultKeyStoreFileName(CommandContext ctx) {
        return getTarget(ctx) + ".keystore";
    }

    @Override
    String getDefaultTrustStoreFileName(CommandContext ctx) {
        return getTarget(ctx) + ".truststore";
    }

}
