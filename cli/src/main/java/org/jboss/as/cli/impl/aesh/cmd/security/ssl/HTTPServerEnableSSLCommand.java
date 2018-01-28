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
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_OVERRIDE_SECURITY_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SERVER_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.SSLSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;

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

    public HTTPServerEnableSSLCommand(CommandContext ctx) {
        super(ctx);
    }

    @Override
    protected void secure(CommandContext ctx, SSLSecurityBuilder builder) throws CommandException {
        try {
            HTTPServer.enableSSL(serverName, noOverride, ctx, builder);
        } catch (Exception ex) {
            throw new CommandException(ex);
        }
    }

    @Override
    protected boolean isSSLEnabled(CommandContext ctx) throws Exception {
        String target = serverName;
        if (target == null) {
            target = DefaultResourceNames.getDefaultServerName(ctx);
        }
        return HTTPServer.getSSLContextName(target, ctx) != null;
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
