/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import java.io.IOException;
import org.aesh.command.CommandDefinition;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 * Enable HTTP authentication for a management interface.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable-http-auth-management", description = "", activator = SecurityCommandActivator.class)
public class ManagementEnableHTTPCommand extends AbstractMgmtEnableAuthenticationCommand {

    public ManagementEnableHTTPCommand() {
        super(AuthFactorySpec.HTTP);
    }

    @Override
    protected void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception {
        ManagementInterfaces.enableHTTPAuthentication(builder, ctx);
    }

    @Override
    protected String getEnabledFactory(CommandContext ctx) throws IOException, OperationFormatException {
        return ManagementInterfaces.getManagementInterfaceHTTPFactoryName(ctx);
    }

    @Override
    protected String getOOTBFactory(CommandContext ctx) throws Exception {
        return ElytronUtil.OOTB_MANAGEMENT_HTTP_FACTORY;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        return "management " + Util.HTTP_INTERFACE;
    }

}
