/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * Provides control over the server's current {@link RunningMode}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RunningModeControl {

    private volatile RunningMode runningMode;
    private volatile boolean reloaded;
    private volatile boolean useCurrentConfig;
    private volatile String newBootFileName;
    private volatile Boolean suspend;

    public RunningModeControl(final RunningMode initialMode) {
        this.runningMode = initialMode;
    }

    public RunningMode getRunningMode() {
        return runningMode;
    }

    public void setRunningMode(RunningMode runningMode) {
        this.runningMode = runningMode;
    }

    public boolean isReloaded() {
        return reloaded;
    }

    public void setReloaded() {
        this.reloaded = true;
    }

    public void setUseCurrentConfig(boolean useCurrentConfig) {
        this.useCurrentConfig = useCurrentConfig;
    }

    public boolean isUseCurrentConfig() {
        return useCurrentConfig;
    }

    /**
     *
     * @return The suspend mode, or null if if has not been expliticly set
     */
    public Boolean getSuspend() {
        return suspend;
    }

    public void setSuspend(Boolean suspend) {
        this.suspend = suspend;
    }

    /**
     * Get the new boot file name. For a standalone server this will be the location of the server configuration
     * (i.e. the standalone.xml variety). For a host controller this will be the location of the host configuration
     * (i.e. the host.xml variety). Once called this method will clear the new boot file name.
     *
     * @return the new boot file name.
     */
    public String getAndClearNewBootFileName() {
        String newBootFileName = this.newBootFileName;
        this.newBootFileName = null;
        return newBootFileName;
    }

    /**
     * Set the new boot file name. For a standalone server this will be the location of the server configuration
     * (i.e. the standalone.xml variety). For a host controller this will be the location of the host configuration
     * (i.e. the host.xml variety).
     *
     * @param newBootFileName the name of the new boot file.
     */
    public void setNewBootFileName(String newBootFileName) {
        this.newBootFileName = newBootFileName;
    }

}
