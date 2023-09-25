/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.impl.internal.ParsedCommand;
import org.wildfly.core.cli.command.aesh.activator.AbstractCommandActivator;

/**
 * A command activator for command that require CLI to be connected.
 *
 * @author jdenise@redhat.com
 */
public class ConnectedActivator extends AbstractCommandActivator {

    @Override
    public boolean isActivated(ParsedCommand command) {
        return getCommandContext().getModelControllerClient() != null;
    }
}
