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
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SECURITY_DOMAIN;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 * Enable authentication for a given http-server security domain.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable-http-auth-http-server", description = "", activator = HttpServerCommandActivator.class)
public class HTTPServerEnableAuthCommand extends AbstractEnableAuthenticationCommand {

    @Option(name = OPT_SECURITY_DOMAIN, required = true, completer = OptionCompleters.SecurityDomainCompleter.class)
    String securityDomain;

    public HTTPServerEnableAuthCommand() {
        super(AuthFactorySpec.HTTP);
    }

    @Override
    protected void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception {
        if (getEnabledFactory(ctx) == null) {
            HTTPServer.enableHTTPAuthentication(builder, securityDomain, ctx);
        }
    }

    @Override
    protected String getEnabledFactory(CommandContext ctx) throws IOException, OperationFormatException {
        return HTTPServer.getSecurityDomainFactoryName(securityDomain, ctx);
    }

    @Override
    protected String getOOTBFactory(CommandContext ctx) throws Exception {
        return ElytronUtil.OOTB_APPLICATION_HTTP_FACTORY;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        return "security domain " + securityDomain;
    }

}
