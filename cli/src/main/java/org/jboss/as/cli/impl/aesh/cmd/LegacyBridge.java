/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;

/**
 * Interface exposed to legacy commands by Aesh based command. This interface
 * allows to simplify legacy command refactoring.
 *
 * @author jdenise@redhat.com
 */
@Deprecated
public interface LegacyBridge {
    CommandResult execute(CommandContext ctx) throws CommandException;
}
