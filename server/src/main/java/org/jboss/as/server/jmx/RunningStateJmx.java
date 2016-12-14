/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.jmx;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_OK;
import static org.wildfly.extension.core.management.client.Process.Type.DOMAIN_SERVER;
import static org.wildfly.extension.core.management.client.Process.Type.EMBEDDED_SERVER;
import static org.wildfly.extension.core.management.client.Process.Type.STANDALONE_SERVER;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.wildfly.extension.core.management.client.Process.RunningState;
import org.wildfly.extension.core.management.client.Process.RuntimeConfigurationState;
import org.wildfly.extension.core.management.client.Process.RunningMode;
import org.wildfly.extension.core.management.client.Process.Type;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class RunningStateJmx extends NotificationBroadcasterSupport implements RunningStateJmxMBean {
    private final ObjectName objectName;
    private final AtomicLong sequence = new AtomicLong(0);
    private volatile RuntimeConfigurationState state = RuntimeConfigurationState.STOPPED;
    private volatile RunningState runningState = RunningState.STOPPED;
    private volatile RunningMode mode = null;
    private final boolean isServer;

    public static final String RUNTIME_CONFIGURATION_STATE = "RuntimeConfigurationState";
    public static final String RUNNING_STATE = "RunningState";

    private RunningStateJmx(ObjectName objectName, RunningMode mode, Type type) {
        this.objectName = objectName;
        this.mode = mode;
        this.isServer = type == DOMAIN_SERVER || type == EMBEDDED_SERVER || type == STANDALONE_SERVER;
    }

    @Override
    public String getProcessState() {
        return state.toString();
    }

    @Override
    public RunningState getRunningState() {
        return runningState;
    }

    @Override
    public RunningMode getRunningMode() {
        return mode;
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[]{
                new MBeanNotificationInfo(
                        new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE},
                        AttributeChangeNotification.class.getName(),
                        ServerLogger.ROOT_LOGGER.processStateChangeNotificationDescription())};
    }

    @Override
    public synchronized void setProcessState(ControlledProcessState.State oldState, ControlledProcessState.State newState) {
        final String stateString;
        if (newState == ControlledProcessState.State.RUNNING) {
            stateString = CONTROLLER_PROCESS_STATE_OK;
        } else {
            stateString = newState.toString();
        }
        final String oldStateString;
        if (oldState == ControlledProcessState.State.RUNNING) {
            oldStateString = CONTROLLER_PROCESS_STATE_OK;
        } else {
            oldStateString = oldState.toString();
        }
        this.state = RuntimeConfigurationState.valueOf(newState.name());
        AttributeChangeNotification notification = new AttributeChangeNotification(objectName, sequence.getAndIncrement(),
                System.currentTimeMillis(),
                ServerLogger.ROOT_LOGGER.jmxAttributeChange(RUNTIME_CONFIGURATION_STATE, oldStateString, stateString),
                RUNTIME_CONFIGURATION_STATE, String.class.getName(), oldStateString, stateString);
        sendNotification(notification);
        switch(newState) {
            case RUNNING:
                if (RunningState.NORMAL != runningState && RunningState.ADMIN_ONLY != runningState && !isServer) {
                    if (mode == RunningMode.NORMAL) {
                        setRunningState(runningState, RunningState.NORMAL);
                    } else {
                        setRunningState(runningState, RunningState.ADMIN_ONLY);
                    }
                }
                break;
            case STARTING:
                if (RunningState.STARTING != runningState) {
                    setRunningState(runningState, RunningState.STARTING);
                }
                break;
            case STOPPING:
                if (RunningState.STOPPING != runningState) {
                    setRunningState(runningState, RunningState.STOPPING);
                }
                break;
            case STOPPED:
                if (RunningState.STOPPED != runningState) {
                    setRunningState(runningState, RunningState.STOPPED);
                }
                break;
            case RELOAD_REQUIRED:
            case RESTART_REQUIRED:
            default:
        }
    }

    @Override
    public synchronized void setRunningState(RunningState oldState, RunningState newState) {
        if(oldState == null || oldState == newState || this.runningState == newState) {
            return;
        }
        this.runningState = newState;
        AttributeChangeNotification notification = new AttributeChangeNotification(objectName, sequence.getAndIncrement(),
                System.currentTimeMillis(),
                ServerLogger.ROOT_LOGGER.jmxAttributeChange(RUNNING_STATE, oldState.toString(), newState.toString()),
                RUNNING_STATE, String.class.getName(), oldState.toString(), newState.toString());
        sendNotification(notification);
    }

    public static void registerMBean(ControlledProcessStateService processStateService, SuspendController suspendController, RunningModeControl runningModeControl, Type type) {
        try {
            final ObjectName name = new ObjectName(OBJECT_NAME);
            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            final RunningMode mode = RunningMode.from(runningModeControl.getRunningMode().name());
            final RunningStateJmxMBean mbean = new RunningStateJmx(name, mode, type);
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
            server.registerMBean(mbean, name);
            registerStateListener(mbean, processStateService);
            if (suspendController != null) {
                suspendController.addListener(new OperationListener() {
                    @Override
                    public void suspendStarted() {
                        mbean.setRunningState(mbean.getRunningState(), RunningState.SUSPENDING);
                    }

                    @Override
                    public void complete() {
                        mbean.setRunningState(mbean.getRunningState(), RunningState.SUSPENDED);
                    }

                    @Override
                    public void cancelled() {
                        if(mbean.getRunningState() == null || mbean.getRunningState() == RunningState.STARTING) {
                            mbean.setRunningState(RunningState.STARTING, RunningState.SUSPENDED);
                        }
                        if (mbean.getRunningMode() == RunningMode.NORMAL) {
                            mbean.setRunningState(mbean.getRunningState(), RunningState.NORMAL);
                        } else {
                            mbean.setRunningState(mbean.getRunningState(),RunningState.ADMIN_ONLY);
                        }
                    }

                    @Override
                    public void timeout() {
                    }
                });
            } else {
                mbean.setRunningState(null, RunningState.STARTING);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void registerStateListener(RunningStateJmxMBean mbean, ControlledProcessStateService processStateService) {
        processStateService.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("currentState".equals(evt.getPropertyName())) {
                        ControlledProcessState.State oldState = (ControlledProcessState.State) evt.getOldValue();
                        ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
                        mbean.setProcessState(oldState, newState);
                    }
                }
            });
    }
}
