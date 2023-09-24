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
 * Handler for the "stop-embedded-host-controller" command.
 *
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
class StopEmbeddedHostControllerHandler extends CommandHandlerWithHelp {

    private final AtomicReference<EmbeddedProcessLaunch> hostControllerReference;


    StopEmbeddedHostControllerHandler(final AtomicReference<EmbeddedProcessLaunch> hostControllerReference) {
        super("stop-embedded-host-controller", false);
        assert hostControllerReference != null;
        this.hostControllerReference = hostControllerReference;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return (hostControllerReference.get() != null && hostControllerReference.get().isHostController());
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        EmbeddedProcessLaunch hostControllerLaunch = hostControllerReference.get();
        if (hostControllerLaunch != null) {
            ctx.disconnectController();
        }
    }

    static void cleanup(final AtomicReference<EmbeddedProcessLaunch> hostControllerReference) {
        EmbeddedProcessLaunch hostControllerLaunch = hostControllerReference.get();
        if (hostControllerLaunch != null) {
            try {
                hostControllerLaunch.stop();
            } finally {
                try {
                    hostControllerLaunch.getEnvironmentRestorer().restoreEnvironment();
                } finally {
                    hostControllerReference.compareAndSet(hostControllerLaunch, null);
                }
            }
        }
    }
}
