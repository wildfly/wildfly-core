/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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

    private static void checkTccl() {
        // search for this same class as a resource, finding it means the TCCL is assigned to our module
        String classFile = TestListener.class.getName().replaceAll("\\.", "/") + ".class";
        if (Thread.currentThread().getContextClassLoader().getResource(classFile) == null) {
            throw new ListenerFailureException("Incorrect TCCL assigned to the listener: " + Thread.currentThread().getContextClassLoader());
        }
    }

    public TestListener() {
        checkTccl();
    }

    @Override
    public void init(ProcessStateListenerInitParameters parameters) {
        checkTccl();
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
        checkTccl();
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
        checkTccl();
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
        checkTccl();
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
