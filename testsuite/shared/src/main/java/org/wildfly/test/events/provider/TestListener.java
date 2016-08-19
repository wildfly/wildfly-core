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

import org.wildfly.extension.core.management.client.RuntimeConfigurationStateChangeEvent;
import org.wildfly.extension.core.management.client.RunningStateChangeEvent;
import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.client.ProcessStateListenerInitParameters;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class TestListener implements ProcessStateListener {

    private File fileRuntime;
    private File fileRunning;
    private FileWriter fileRuntimeWriter;
    private FileWriter fileRunningWriter;
    private ProcessStateListenerInitParameters parameters;

    @Override
    public void init(ProcessStateListenerInitParameters parameters) {
        this.parameters = parameters;
        fileRuntime = new File(parameters.getInitProperties().get("file") + File.separatorChar + "runtimeConfigurationState.txt");
        fileRunning = new File(parameters.getInitProperties().get("file") + File.separatorChar + "runningState.txt");
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
        try {
            fileRuntimeWriter.write(String.format("%s %s %s %s\n", parameters.getProcessType(), parameters.getRunningMode(), evt.getOldState(), evt.getNewState()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void runningStateChanged(RunningStateChangeEvent evt) {
        try {
            fileRunningWriter.write(String.format("%s %s %s %s\n", parameters.getProcessType(), parameters.getRunningMode(), evt.getOldState(), evt.getNewState()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
