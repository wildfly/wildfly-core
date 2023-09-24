/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProcessInfo {
    private final String processName;
    private final String pcAuthKey;
    private final boolean running;
    private final boolean stopping;

    ProcessInfo(final String processName, final String pcAuthKey, final boolean running, final boolean stopping) {
        this.processName = processName;
        this.pcAuthKey = pcAuthKey;
        this.running = running;
        this.stopping = stopping;
    }

    public String getProcessName() {
        return processName;
    }

    public String getPCAuthKey() {
        return pcAuthKey;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStopping() {
        return stopping;
    }
}
