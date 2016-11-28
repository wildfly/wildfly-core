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
package org.wildfly.extension.core.management;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.server.Services;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.core.management.client.Process;
import org.wildfly.extension.core.management.client.RuntimeConfigurationStateChangeEvent;
import org.wildfly.extension.core.management.client.RunningStateChangeEvent;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;
import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.client.ProcessStateListenerInitParameters;

/**
 * Service that listens for process state changes and notifies the ProcessStateListener instance of those events.
 * That means when the running state or the runtime configuration state changes.
 * <strong>RunningStateChangeEvent for suspend / resume can only be sent for servers (somain or standalone) but not on HostControllers.</strong>
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2016 Red Hat, inc.
 */
public class ProcessStateListenerService implements Service<Void> {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("core", "management", "process-state-listener");

    private final InjectedValue<ControlledProcessStateService> controlledProcessStateService = new InjectedValue<>();
    private final InjectedValue<SuspendController> suspendControllerInjectedValue = new InjectedValue<>();
    private final InjectedValue<ExecutorService> executorServiceValue = new InjectedValue<>();
    private final PropertyChangeListener propertyChangeListener;
    private final OperationListener operationListener;
    private final ProcessStateListener listener;
    private final ProcessStateListenerInitParameters parameters;
    private final String name;
    private final int timeout;
    private final ProcessType processType;

    private volatile Process.RunningState runningState = null;

    public ProcessStateListenerService(ProcessType processType, RunningMode runningMode, String name, ProcessStateListener listener, Map<String, String> properties, int timeout) {
        this.listener = listener;
        this.name = name;
        this.timeout = timeout;
        this.processType = processType;
        this.parameters = new ProcessStateListenerInitParameters.Builder()
                .setInitProperties(properties)
                .setRunningMode(Process.RunningMode.from(runningMode.name()))
                .setProcessType(Process.Type.valueOf(processType.name()))
                .build();
        this.propertyChangeListener = (PropertyChangeEvent evt) -> {
            if ("currentState".equals(evt.getPropertyName())) {
                Process.RuntimeConfigurationState oldState = Process.RuntimeConfigurationState.valueOf(((ControlledProcessState.State) evt.getOldValue()).name());
                Process.RuntimeConfigurationState newState = Process.RuntimeConfigurationState.valueOf(((ControlledProcessState.State) evt.getNewValue()).name());
                transition(oldState, newState);
            }
        };
        if (processType != ProcessType.HOST_CONTROLLER && processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
            this.operationListener = new OperationListener() {
                @Override
                public void suspendStarted() {
                    suspendTransition(runningState, Process.RunningState.SUSPENDING);
                }

                @Override
                public void complete() {
                    suspendTransition(runningState, Process.RunningState.SUSPENDED);
                }

                @Override
                public void cancelled() {
                    if(runningState == null) {//gracefull startup
                         suspendTransition(Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
                        return;
                    }
                    switch (runningMode) {
                        case ADMIN_ONLY:
                            suspendTransition(runningState, Process.RunningState.ADMIN_ONLY);
                            break;
                        case NORMAL:
                            suspendTransition(runningState, Process.RunningState.NORMAL);
                            break;
                    }
                }

                @Override
                public void timeout() {
                }
            };
        } else {
            operationListener = null;
        }
    }

    private void transition(Process.RuntimeConfigurationState oldState, Process.RuntimeConfigurationState newState) {
        if(oldState == newState) {
            return;
        }
        if (runningState == null) {
            switch (oldState) {
                case RUNNING:
                    if (processType.isServer()) {
                        runningState = Process.RunningState.SUSPENDED;
                    } else {
                        if (parameters.getRunningMode() == Process.RunningMode.NORMAL) {
                            runningState = Process.RunningState.NORMAL;
                        } else {
                            runningState = Process.RunningState.ADMIN_ONLY;
                        }
                    }
                    break;
                case STARTING:
                    runningState = Process.RunningState.STARTING;
                    break;
                case STOPPING:
                    runningState = Process.RunningState.STOPPING;
                    break;
                case STOPPED:
                    runningState = Process.RunningState.STOPPED;
                    break;
                case RELOAD_REQUIRED:
                case RESTART_REQUIRED:
                default:
            }
        }
        Future<?> controlledProcessStateTransition = executorServiceValue.getValue().submit(() -> {
            listener.runtimeConfigurationStateChanged(new RuntimeConfigurationStateChangeEvent(oldState, newState));
        });
        try {
            controlledProcessStateTransition.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(ex, name);
        } catch (TimeoutException ex) {
            CoreManagementLogger.ROOT_LOGGER.processStateTimeoutError(ex, name);
        } catch (ExecutionException | RuntimeException t) {
            CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(t, name);
        }
        switch(newState) {
            case RUNNING:
                if (Process.RunningState.NORMAL != runningState && Process.RunningState.ADMIN_ONLY != runningState) {
                    if (parameters.getRunningMode()  == Process.RunningMode.NORMAL) {
                        suspendTransition(runningState, Process.RunningState.NORMAL);
                    } else {
                        suspendTransition(runningState, Process.RunningState.ADMIN_ONLY);
                    }
                }
                break;
            case STARTING:
                if (Process.RunningState.STARTING != runningState) {
                    suspendTransition(runningState, Process.RunningState.STARTING);
                }
                break;
            case STOPPING:
                if (Process.RunningState.STOPPING != runningState) {
                    suspendTransition(runningState, Process.RunningState.STOPPING);
                }
                break;
            case STOPPED:
                if (Process.RunningState.STOPPED != runningState) {
                    suspendTransition(runningState, Process.RunningState.STOPPED);
                }
                break;
            case RELOAD_REQUIRED:
            case RESTART_REQUIRED:
            default:
        }
    }

    /**
     * This will <strong>NEVER</strong> be called on a HostController.
     * @param newState the new running state.
     */
    private void suspendTransition(Process.RunningState oldState, Process.RunningState newState) {
        if(oldState == newState) {
            return;
        }
        this.runningState = newState;
        Future<?> suspendStateTransition = executorServiceValue.getValue().submit(() -> {
            listener.runningStateChanged(new RunningStateChangeEvent(oldState, newState));
        });
        try {
            suspendStateTransition.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(ex, name);
        } catch (TimeoutException ex) {
            CoreManagementLogger.ROOT_LOGGER.processStateTimeoutError(ex, name);
        } catch (ExecutionException | RuntimeException t) {
            CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(t, name);
        }
    }

    static void install(ServiceTarget serviceTarget, ProcessType processType, RunningMode runningMode, String listenerName, ProcessStateListener listener, Map<String, String> properties, int timeout) {
        ProcessStateListenerService service = new ProcessStateListenerService(processType, runningMode, listenerName, listener, properties, timeout);
        ServiceBuilder<Void> builder = serviceTarget.addService(SERVICE_NAME.append(listenerName), service)
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, service.controlledProcessStateService);
        if (processType != ProcessType.HOST_CONTROLLER && processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
            builder.addDependency(SuspendController.SERVICE_NAME, SuspendController.class, service.suspendControllerInjectedValue);
            builder.addDependency(Services.JBOSS_SERVER_EXECUTOR, ExecutorService.class, service.executorServiceValue);
        } else {
            builder.addDependency(ServiceName.JBOSS.append("host", "controller", "executor"), ExecutorService.class, service.executorServiceValue);
        }
        builder.install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        Runnable task = () -> {
            try {
                ProcessStateListenerService.this.listener.init(parameters);
                SuspendController controller = ProcessStateListenerService.this.suspendControllerInjectedValue.getOptionalValue();
                if (controller != null) {
                    switch (controller.getState()) {
                        case PRE_SUSPEND:
                            this.runningState = Process.RunningState.PRE_SUSPEND;
                            break;
                        case RUNNING:
                            if (parameters.getRunningMode() == Process.RunningMode.NORMAL) {
                                this.runningState = Process.RunningState.NORMAL;
                            } else {
                                this.runningState = Process.RunningState.ADMIN_ONLY;
                            }
                            break;
                        case SUSPENDED:
                            this.runningState = Process.RunningState.SUSPENDED;
                            break;
                        case SUSPENDING:
                            this.runningState = Process.RunningState.SUSPENDING;
                            break;
                    }
                    controller.addListener(operationListener);
                }
                controlledProcessStateService.getValue().addPropertyChangeListener(propertyChangeListener);
                context.complete();
            } catch (RuntimeException t) {
                context.failed(new StartException(CoreManagementLogger.ROOT_LOGGER.processStateInitError(t, name)));
            }
        };
        try {
            executorServiceValue.getValue().execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

    @Override
    public void stop(StopContext context) {
        Runnable task = () -> {
            controlledProcessStateService.getValue().removePropertyChangeListener(propertyChangeListener);
            runningState = null;
            try {
                listener.cleanup();
                SuspendController controller = suspendControllerInjectedValue.getOptionalValue();
                if (controller != null) {
                    controller.removeListener(operationListener);
                }
            } catch (RuntimeException t) {
                CoreManagementLogger.ROOT_LOGGER.processStateCleanupError(t, name);
            } finally {
                context.complete();
            }
        };
        final ExecutorService executorService = executorServiceValue.getValue();
        try {
            try {
                executorService.execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            }
        } finally {
            context.asynchronous();
        }
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}
