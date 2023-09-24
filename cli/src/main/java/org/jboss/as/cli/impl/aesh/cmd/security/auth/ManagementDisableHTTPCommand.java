/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.dmr.ModelNode;

/**
 * Disable HTTP authentication applied to a management interface.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-http-auth-management", description = "", activator = SecurityCommandActivator.class)
public class ManagementDisableHTTPCommand extends AbstractMgmtDisableAuthenticationCommand {

    public ManagementDisableHTTPCommand() {
        super(AuthFactorySpec.HTTP);
    }

    @Override
    public String getEnabledFactory(CommandContext ctx) throws Exception {
        return ManagementInterfaces.getManagementInterfaceHTTPFactoryName(ctx);
    }

    @Override
    protected ModelNode disableFactory(CommandContext context) throws Exception {
        ModelNode request = ManagementInterfaces.disableHTTPAuth(context);
        return request;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        return "management " + Util.HTTP_INTERFACE;
    }

}
