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
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.internal.ParsedOption;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SECURITY_DOMAIN;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters.MechanismDisableCompleter;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;

/**
 * Disable authentication applied to an http-server security-domain. Complexity
 * comes from the fact that an undertow application-security-domain can
 * references a factory or a security domain.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-http-auth-http-server", description = "", activator = HttpServerCommandActivator.class)
public class HTTPServerDisableAuthCommand extends AbstractDisableAuthenticationCommand {

    public static class MechanismCompleter extends MechanismDisableCompleter {

        @Override
        protected List<String> getItems(CLICompleterInvocation completerInvocation) {
            HTTPServerDisableAuthCommand cmd = (HTTPServerDisableAuthCommand) completerInvocation.getCommand();
            try {
                if (!HTTPServer.hasAuthFactory(cmd.ctx, cmd.securityDomain)) {
                    return Collections.emptyList();
                }
                return super.getItems(completerInvocation);
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    public static class MechanismActivator extends AbstractDependOptionActivator {

        public MechanismActivator() {
            super(false, OPT_SECURITY_DOMAIN);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!super.isActivated(processedCommand)) {
                return false;
            }
            HTTPServerDisableAuthCommand cmd = (HTTPServerDisableAuthCommand) processedCommand.command();
            ParsedOption opt = processedCommand.findLongOptionNoActivatorCheck(OPT_SECURITY_DOMAIN);
            if (opt != null && opt.value() != null) {
                try {
                    return HTTPServer.hasAuthFactory(cmd.ctx, opt.value());
                } catch (IOException | OperationFormatException ex) {
                    return false;
                }
            }
            return false;
        }
    }

    @Option(name = OPT_SECURITY_DOMAIN, required = true, completer = OptionCompleters.SecurityDomainCompleter.class)
    String securityDomain;

    @Option(name = OPT_MECHANISM,
            completer = MechanismCompleter.class, activator = MechanismActivator.class)
    String factoryMechanism;

    private final CommandContext ctx;

    public HTTPServerDisableAuthCommand(CommandContext ctx) {
        super(AuthFactorySpec.HTTP);
        this.ctx = ctx;
    }

    @Override
    protected String getMechanism() {
        return factoryMechanism;
    }

    @Override
    public ModelNode buildSecurityRequest(CommandContext context) throws Exception {
        if (HTTPServer.hasAuthFactory(ctx, securityDomain)) {
            return super.buildSecurityRequest(context);
        } else {
            return disableFactory(context);
        }
    }

    @Override
    public String getEnabledFactory(CommandContext ctx) throws Exception {
        // Special case for undertow security domain, can be a security-domain or a factory
        if (HTTPServer.hasAuthFactory(ctx, securityDomain)) {
            return HTTPServer.getSecurityDomainFactoryName(securityDomain, ctx);
        } else {
            return HTTPServer.getReferencedSecurityDomainName(securityDomain, ctx);
        }
    }

    @Override
    protected ModelNode disableFactory(CommandContext context) throws Exception {
        // In the undertow case, the undertow application-security-domain is simply removed.
        // Whatever the fact that the domain references a factory or a domain.
        return HTTPServer.disableHTTPAuthentication(securityDomain, context);
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        return "security domain " + securityDomain;
    }

}
