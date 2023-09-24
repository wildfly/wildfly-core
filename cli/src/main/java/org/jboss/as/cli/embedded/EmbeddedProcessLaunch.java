/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

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

    EmbeddedProcessLaunch(EmbeddedManagedProcess process, EnvironmentRestorer restorer, boolean hostController) {
        this.process = process;
        this.restorer = restorer;
        this.hostController = hostController;
    }

    EnvironmentRestorer getEnvironmentRestorer() {
        return restorer;
    }

    public void stop() {
        if (process == null)
            return;
        process.stop();
    }

    public boolean isHostController() {
        return hostController;
    }

    public boolean isStandalone() {
        return !hostController;
    }

}
