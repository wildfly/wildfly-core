/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security;

import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;

/**
 * Activator to control security related commands visibility and executability.
 *
 * @author jdenise@redhat.com
 */
public class SecurityCommandActivator extends ConnectedActivator {
    @Override
    public boolean isActivated(ParsedCommand command) {
        if (!super.isActivated(command)) {
            return false;
        }
        if (getCommandContext().isDomainMode()) {
            return false;
        }
        try {
            return ElytronUtil.isElytronSupported(getCommandContext());
        } catch (Exception ex) {
            return false;
        }
    }
}
