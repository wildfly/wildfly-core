/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
