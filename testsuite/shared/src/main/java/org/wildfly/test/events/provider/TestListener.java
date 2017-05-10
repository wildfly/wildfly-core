/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.test.events.provider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.client.ProcessStateListenerInitParameters;
import org.wildfly.extension.core.management.client.RunningStateChangeEvent;
import org.wildfly.extension.core.management.client.RuntimeConfigurationStateChangeEvent;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class TestListener implements ProcessStateListener {
    // parameters
    public static final String RUNTIME_CONFIGURATION_STATE_CHANGE_FILE = "runtimeConfigurationStateFile";
    public static final String RUNNING_STATE_CHANGE_FILE = "runningStateFile";
    public static final String FAIL_RUNTIME_CONFIGURATION_STATE_CHANGED = "failRuntimeConfigurationStateChanged";
    public static final String FAIL_RUNNING_STATE_CHANGED = "failRunningStateChanged";
    public static final String TIMEOUT = "timeout";

    // default values
    public static final String DEFAULT_RUNTIME_CONFIGURATION_STATE_CHANGE_FILENAME = "runtimeConfigurationState.txt";
    public static final String DEFAULT_RUNNING_STATE_CHANGE_FILENAME = "runningState.txt";

    public static final String RUNTIME_CONFIGURATION_STATE_CHANGE_FILENAME = "runtimeConfigurationState.txt";
    public static final String RUNNING_STATE_CHANGE_FILENAME = "runningState.txt";

    private File fileRuntime;
    private File fileRunning;
    private FileWriter fileRuntimeWriter;
    private FileWriter fileRunningWriter;
    private ProcessStateListenerInitParameters parameters;

    @Override
    public void init(ProcessStateListenerInitParameters parameters) {
        this.parameters = parameters;
        Path dataDir = Paths.get(parameters.getInitProperties().get("file"));

        if (!parameters.getInitProperties().containsKey(RUNTIME_CONFIGURATION_STATE_CHANGE_FILE)) {
            fileRuntime = dataDir.resolve(DEFAULT_RUNTIME_CONFIGURATION_STATE_CHANGE_FILENAME).toFile();
        } else {
            fileRuntime = dataDir.resolve(parameters.getInitProperties().get(RUNTIME_CONFIGURATION_STATE_CHANGE_FILE)).toFile();
        }

        if (!parameters.getInitProperties().containsKey(RUNNING_STATE_CHANGE_FILE)) {
            fileRunning = dataDir.resolve(DEFAULT_RUNNING_STATE_CHANGE_FILENAME).toFile();
        } else {
            fileRunning = dataDir.resolve(parameters.getInitProperties().get(RUNNING_STATE_CHANGE_FILE)).toFile();

        }

        try {
            fileRuntimeWriter = new FileWriter(fileRuntime, true);
            fileRunningWriter = new FileWriter(fileRunning, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cleanup() {
        try {
            fileRuntimeWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fileRuntimeWriter = null;
        }
        try {
            fileRunningWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fileRunningWriter = null;
        }
    }

    @Override
    public void runtimeConfigurationStateChanged(RuntimeConfigurationStateChangeEvent evt) {
        if (parameters.getInitProperties().containsKey(FAIL_RUNTIME_CONFIGURATION_STATE_CHANGED)) {
            throw new ListenerFailureException(FAIL_RUNTIME_CONFIGURATION_STATE_CHANGED);
        }
        try {
            if (parameters.getInitProperties().containsKey(TIMEOUT)) {
                long timeout = Long.parseLong(parameters.getInitProperties().get(TIMEOUT));
                Thread.sleep(timeout);
            }
            fileRuntimeWriter.write(String.format("%s %s %s %s\n",
                    parameters.getProcessType(),
                    parameters.getRunningMode(),
                    evt.getOldState(),
                    evt.getNewState()));
            fileRuntimeWriter.flush();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void runningStateChanged(RunningStateChangeEvent evt) {
        if (parameters.getInitProperties().containsKey(FAIL_RUNNING_STATE_CHANGED)) {
            throw new ListenerFailureException(FAIL_RUNNING_STATE_CHANGED);
        }
        try {
            fileRunningWriter.write(String.format("%s %s %s %s\n",
                    parameters.getProcessType(),
                    parameters.getRunningMode(),
                    evt.getOldState(),
                    evt.getNewState()));
            fileRunningWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
