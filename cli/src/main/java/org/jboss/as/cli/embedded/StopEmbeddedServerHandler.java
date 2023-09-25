/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 * Handler for the "stop-embedded-server" command.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class StopEmbeddedServerHandler extends CommandHandlerWithHelp {

    private final AtomicReference<EmbeddedProcessLaunch> serverReference;


    StopEmbeddedServerHandler(final AtomicReference<EmbeddedProcessLaunch> serverReference) {
        super("stop-embedded-server", false);
        assert serverReference != null;
        this.serverReference = serverReference;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return (serverReference.get() != null && serverReference.get().isStandalone());
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        EmbeddedProcessLaunch serverLaunch = serverReference.get();
        if (serverLaunch != null) {
            ctx.disconnectController();
        }
    }

    static void cleanup(final AtomicReference<EmbeddedProcessLaunch> serverReference) {
        EmbeddedProcessLaunch serverLaunch = serverReference.get();
        if (serverLaunch != null) {
            try {
                serverLaunch.stop();
            } finally {
                try {
                    serverLaunch.getEnvironmentRestorer().restoreEnvironment();
                } finally {
                    serverReference.compareAndSet(serverLaunch, null);
                }
            }
        }
    }
}
