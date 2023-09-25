/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security;

import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;

/**
 * Activator to control undertow related commands visibility and executability.
 *
 * @author jdenise@redhat.com
 */
public class HttpServerCommandActivator extends SecurityCommandActivator {

    @Override
    public boolean isActivated(ParsedCommand command) {
        if (!super.isActivated(command)) {
            return false;
        }
        try {
            return HTTPServer.isUnderowSupported(getCommandContext());
        } catch (Exception ex) {
            return false;
        }
    }
}
