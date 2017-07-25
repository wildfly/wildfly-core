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

import org.jboss.as.cli.Util;
import org.wildfly.core.embedded.EmbeddedManagedProcess;

/**
 * Simple data structure to record information related to a launch of an embedded process.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */

public class EmbeddedProcessLaunch {

    private final EmbeddedManagedProcess process;
    private final EnvironmentRestorer restorer;
    private final boolean hostController;
    private final AutoCloseable closeableModuleLoader;

    EmbeddedProcessLaunch(EmbeddedManagedProcess process, EnvironmentRestorer restorer, boolean hostController,
                          AutoCloseable closeableModuleLoader) {
        this.process = process;
        this.restorer = restorer;
        this.hostController = hostController;
        this.closeableModuleLoader = closeableModuleLoader;
    }

    EnvironmentRestorer getEnvironmentRestorer() {
        return restorer;
    }

    public void stop() {
        try {
            if (process == null)
                return;
            process.stop();
        } finally {
            try {
                Util.safeClose(closeableModuleLoader);
            } finally {
                restorer.restoreEnvironment();
            }
        }
    }

    public boolean isHostController() {
        return hostController;
    }

    public boolean isStandalone() {
        return !hostController;
    }

}
