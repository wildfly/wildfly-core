/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
public class DuplicateExtCommandHandler extends CommandHandlerWithHelp {
    public static final String NAME = "echo";
    public static final String OUTPUT = "hello from " + DuplicateExtCommandHandler.class.getSimpleName();

    public DuplicateExtCommandHandler() {
        super(NAME, false);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        ctx.printLine(OUTPUT);
    }
}
