/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHandlerProvider;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
public class DuplicateExtCommandHandlerProvider implements CommandHandlerProvider {
    @Override
    public CommandHandler createCommandHandler(CommandContext ctx) {
        return new DuplicateExtCommandHandler();
    }

    @Override
    public boolean isTabComplete() {
        return false;
    }

    @Override
    public String[] getNames() {
        return new String[]{DuplicateExtCommandHandler.NAME};
    }
}
