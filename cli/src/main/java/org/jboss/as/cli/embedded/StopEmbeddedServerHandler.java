/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
