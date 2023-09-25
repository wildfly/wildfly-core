/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.HttpServerCommandActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_REFERENCED_SECURITY_DOMAIN;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SECURITY_DOMAIN;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters.MechanismCompleter;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters.ReferencedSecurityDomainCompleter;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ApplicationSecurityDomain;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.jboss.as.cli.operation.OperationFormatException;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;

/**
 * Enable authentication for a given http-server security domain.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable-http-auth-http-server", description = "", activator = HttpServerCommandActivator.class)
public class HTTPServerEnableAuthCommand extends AbstractEnableAuthenticationCommand {

    public static class FactoryMechanismCompleter extends MechanismCompleter {

        @Override
        protected List<String> getItems(CLICompleterInvocation completerInvocation) {
            HTTPServerEnableAuthCommand cmd = (HTTPServerEnableAuthCommand) completerInvocation.getCommand();
            try {
                if (cmd.securityDomain != null) {
                    ApplicationSecurityDomain secDomain = HTTPServer.getSecurityDomain(cmd.ctx, cmd.securityDomain);
                    if (secDomain != null
                            && secDomain.getFactory() == null) {
                        return Collections.emptyList();
                    }
                    return super.getItems(completerInvocation);
                } else {
                    return Collections.emptyList();
                }
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    public static class ReferencedSecurityDomainActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.add(OPT_MECHANISM);
            EXPECTED.add(OPT_SECURITY_DOMAIN);
        }

        public ReferencedSecurityDomainActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand pc) {
            HTTPServerEnableAuthCommand cmd = (HTTPServerEnableAuthCommand) pc.command();
            try {
                if (!HTTPServer.isReferencedSecurityDomainSupported(cmd.ctx)) {
                    return false;
                }
                if (cmd.securityDomain != null) {
                    ApplicationSecurityDomain secDomain = HTTPServer.getSecurityDomain(cmd.ctx, cmd.securityDomain);
                    if (secDomain != null && secDomain.getSecurityDomain() == null) {
                        return false;
                    }
                }
                return super.isActivated(pc);
            } catch (OperationFormatException | IOException ex) {
                return false;
            }
        }
    }

    public static class MechanismActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.add(OPT_REFERENCED_SECURITY_DOMAIN);
            EXPECTED.add(OPT_SECURITY_DOMAIN);
        }

        public MechanismActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!super.isActivated(processedCommand)) {
                return false;
            }
            HTTPServerEnableAuthCommand cmd = (HTTPServerEnableAuthCommand) processedCommand.command();
            try {
                if (cmd.securityDomain != null) {
                    ApplicationSecurityDomain secDomain = HTTPServer.getSecurityDomain(cmd.ctx, cmd.securityDomain);
                    if (secDomain != null
                            && secDomain.getFactory() == null) {
                        return false;
                    }
                }
            } catch (IOException | OperationFormatException ex) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    @Option(name = OPT_SECURITY_DOMAIN, required = true, completer = OptionCompleters.SecurityDomainCompleter.class)
    String securityDomain;

    @Option(name = OPT_MECHANISM,
            completer = FactoryMechanismCompleter.class, activator = MechanismActivator.class)
    String factoryMechanism;

    @Option(name = OPT_REFERENCED_SECURITY_DOMAIN, completer = ReferencedSecurityDomainCompleter.class,
            activator = ReferencedSecurityDomainActivator.class)
    String referencedSecurityDomain;

    private final CommandContext ctx;

    public HTTPServerEnableAuthCommand(CommandContext ctx) {
        super(AuthFactorySpec.HTTP);
        this.ctx = ctx;
    }

    @Override
    protected String getMechanism() {
        return factoryMechanism;
    }

    @Override
    protected void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception {
        ApplicationSecurityDomain secDomain = HTTPServer.getSecurityDomain(ctx, securityDomain);
        if (secDomain != null) {
            if (secDomain.getSecurityDomain() != null && builder.getReferencedSecurityDomain() != null
                    && !secDomain.getSecurityDomain().equals(builder.getReferencedSecurityDomain())) {
                // re-write the existing security domain
                HTTPServer.writeReferencedSecurityDomain(builder, securityDomain, ctx);
            }
        } else {
            // add a new security domain resource
            HTTPServer.enableHTTPAuthentication(builder, securityDomain, ctx);
        }
    }

    @Override
    protected AuthSecurityBuilder buildSecurityRequest(CommandContext context) throws Exception {
        // No support for security-domain, fallback on legacy http authentication factory.
        if (!HTTPServer.isReferencedSecurityDomainSupported(context)) {
            return super.buildSecurityRequest(context);
        }
        ApplicationSecurityDomain existingDomain = HTTPServer.getSecurityDomain(ctx, securityDomain);
        AuthSecurityBuilder builder = null;
        if (getMechanism() == null) {
            if (referencedSecurityDomain == null) {
                referencedSecurityDomain = getOOTBSecurityDomain(context);
            }
            if (!ElytronUtil.securityDomainExists(context, referencedSecurityDomain)) {
                throw new CommandException("Can't enable HTTP Authentication, security domain "
                        + referencedSecurityDomain + " doesn't exist");
            }
            if (existingDomain != null && existingDomain.getFactory() != null) {
                throw new CommandException("Can't mix mechanism and referenced security domain");
            }
            builder = new AuthSecurityBuilder(referencedSecurityDomain);
        } else {
            if (referencedSecurityDomain != null || (existingDomain != null && existingDomain.getSecurityDomain() != null)) {
                throw new CommandException("Can't mix mechanism and referenced security domain");
            }
            return super.buildSecurityRequest(context);
        }
        secure(context, builder);
        return builder;
    }

    @Override
    protected String getEnabledFactory(CommandContext ctx) throws IOException, OperationFormatException {
        return HTTPServer.getSecurityDomainFactoryName(securityDomain, ctx);
    }

    @Override
    protected String getOOTBFactory(CommandContext ctx) throws Exception {
        return ElytronUtil.OOTB_APPLICATION_HTTP_FACTORY;
    }

    protected String getOOTBSecurityDomain(CommandContext ctx) throws Exception {
        return ElytronUtil.OOTB_APPLICATION_DOMAIN;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        return "security domain " + securityDomain;
    }

}
