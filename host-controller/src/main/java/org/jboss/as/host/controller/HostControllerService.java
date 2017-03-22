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

package org.jboss.as.host.controller;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.AbstractControllerService.EXECUTOR_CAPABILITY;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.remoting.HttpListenerRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.BootstrapListener;
import org.jboss.as.server.ExternalManagementRequestExecutor;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.ProductConfig;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.InjectedValue;
import org.jboss.msc.value.Value;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.JBossThreadFactory;

/**
 * The root service for a HostController process.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostControllerService implements Service<AsyncFuture<ServiceContainer>> {

    public static final ServiceName HC_SERVICE_NAME = ServiceName.JBOSS.append("host", "controller");
    /** @deprecated Use the org.wildfly.management.executor capability */
    @Deprecated
    public static final ServiceName HC_EXECUTOR_SERVICE_NAME = HC_SERVICE_NAME.append("executor");
    public static final ServiceName HC_SCHEDULED_EXECUTOR_SERVICE_NAME = HC_SERVICE_NAME.append("scheduled", "executor");

    private final ThreadGroup threadGroup = new ThreadGroup("Host Controller Service Threads");
    private final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
        public JBossThreadFactory run() {
            return new JBossThreadFactory(threadGroup, Boolean.FALSE, null, "%G - %t", null, null);
        }
    });
    private final HostControllerEnvironment environment;
    private final HostRunningModeControl runningModeControl;
    private final ControlledProcessState processState;
    private final String authCode;
    private final CapabilityRegistry capabilityRegistry;
    private volatile FutureServiceContainer futureContainer;
    private volatile long startTime;

    public HostControllerService(final HostControllerEnvironment environment, final HostRunningModeControl runningModeControl,
                          final String authCode, final ControlledProcessState processState, FutureServiceContainer futureContainer) {
        this.environment = environment;
        this.runningModeControl = runningModeControl;
        this.authCode = authCode;
        this.processState = processState;
        this.startTime = environment.getStartTime();
        this.futureContainer = futureContainer;
        this.capabilityRegistry = new CapabilityRegistry(false);
    }

    public HostControllerService(final HostControllerEnvironment environment, final HostRunningModeControl runningModeControl,
                                 final String authCode, final ControlledProcessState processState) {
        this(environment, runningModeControl, authCode, processState, new FutureServiceContainer());
    }

    @Override
    public void start(StartContext context) throws StartException {
        //Moved to AbstractControllerService.start()
        // processState.setStarting();
        final ProductConfig config = environment.getProductConfig();
        final String prettyVersion = config.getPrettyVersionString();
        ServerLogger.AS_ROOT_LOGGER.serverStarting(prettyVersion);
        if (System.getSecurityManager() != null) {
            ServerLogger.AS_ROOT_LOGGER.securityManagerEnabled();
        }
        if (ServerLogger.CONFIG_LOGGER.isDebugEnabled()) {
            final Properties properties = System.getProperties();
            final StringBuilder b = new StringBuilder(8192);
            b.append("Configured system properties:");
            for (String property : new TreeSet<String>(properties.stringPropertyNames())) {
                String propVal = property.toLowerCase(Locale.getDefault()).contains("password") ? "<redacted>" : properties.getProperty(property, "<undefined>");
                b.append("\n\t").append(property).append(" = ").append(propVal);
            }
            ServerLogger.CONFIG_LOGGER.debug(b);
            ServerLogger.CONFIG_LOGGER.debugf("VM Arguments: %s", getVMArguments());
            if (ServerLogger.CONFIG_LOGGER.isTraceEnabled()) {
                b.setLength(0);
                final Map<String, String> env = System.getenv();
                b.append("Configured system environment:");
                for (String key : new TreeSet<String>(env.keySet())) {
                    String envVal = key.toLowerCase(Locale.getDefault()).contains("password") ? "<redacted>" : env.get(key);
                    b.append("\n\t").append(key).append(" = ").append(envVal);
                }
                ServerLogger.CONFIG_LOGGER.trace(b);
            }
        }
        final ServiceTarget serviceTarget = context.getChildTarget();
        final ServiceController<?> myController = context.getController();

        final ServiceContainer serviceContainer = myController.getServiceContainer();

        long startTime = this.startTime;
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
        } else {
            this.startTime = -1;
        }

        final BootstrapListener bootstrapListener = new BootstrapListener(serviceContainer, startTime, serviceTarget, futureContainer, prettyVersion + " (Host Controller)", environment.getDomainTempDir());
        bootstrapListener.getStabilityMonitor().addController(myController);

        // The first default services are registered before the bootstrap operations are executed.

        // Install the process controller client
        // if this is running embedded, then processcontroller is a noop, this can be extended later.
        final ProcessType processType = environment.getProcessType();
        if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER) {
            final ProcessControllerConnectionServiceNoop processControllerClient = new ProcessControllerConnectionServiceNoop(environment, authCode);
            serviceTarget.addService(ProcessControllerConnectionServiceNoop.SERVICE_NAME, processControllerClient).install();
        } else {
            final ProcessControllerConnectionService processControllerClient = new ProcessControllerConnectionService(environment, authCode);
            serviceTarget.addService(ProcessControllerConnectionService.SERVICE_NAME, processControllerClient).install();
        }

        // Executor Services
        final HostControllerExecutorService executorService = new HostControllerExecutorService(threadFactory);
        serviceTarget.addService(EXECUTOR_CAPABILITY.getCapabilityServiceName(), executorService)
                .addAliases(HC_EXECUTOR_SERVICE_NAME, ManagementRemotingServices.SHUTDOWN_EXECUTOR_NAME) // Use this executor for mgmt shutdown for now
                .install();

        final HostControllerScheduledExecutorService scheduledExecutorService = new HostControllerScheduledExecutorService(threadFactory);
        serviceTarget.addService(HC_SCHEDULED_EXECUTOR_SERVICE_NAME, scheduledExecutorService)
                .addDependency(EXECUTOR_CAPABILITY.getCapabilityServiceName(), ExecutorService.class, scheduledExecutorService.executorInjector)
                .install();

        ExternalManagementRequestExecutor.install(serviceTarget, threadGroup, EXECUTOR_CAPABILITY.getCapabilityServiceName());

        // Install required path services. (Only install those identified as required)
        HostPathManagerService hostPathManagerService = new HostPathManagerService(capabilityRegistry);
        HostPathManagerService.addService(serviceTarget, hostPathManagerService, environment);

        HttpListenerRegistryService.install(serviceTarget);

        // Add product config service
        final Value<ProductConfig> productConfigValue = new ImmediateValue<ProductConfig>(config);
        serviceTarget.addService(Services.JBOSS_PRODUCT_CONFIG_SERVICE, new ValueService<ProductConfig>(productConfigValue))
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
        DomainModelControllerService.addService(serviceTarget, environment, runningModeControl, processState,
                bootstrapListener, hostPathManagerService, capabilityRegistry);
    }

    @Override
    public void stop(StopContext context) {
        String prettyVersion = environment.getProductConfig().getPrettyVersionString();
        //Moved to AbstractControllerService.stop()
        //processState.setStopping();
        ServerLogger.AS_ROOT_LOGGER.serverStopped(prettyVersion, Integer.valueOf((int) (context.getElapsedTime() / 1000000L)));
        BootstrapListener.deleteStartupMarker(environment.getDomainTempDir());
    }

    @Override
    public AsyncFuture<ServiceContainer> getValue() throws IllegalStateException, IllegalArgumentException {
        return futureContainer;
    }

    public HostControllerEnvironment getEnvironment() {
        return environment;
    }

    private String getVMArguments() {
        final StringBuilder result = new StringBuilder(1024);
        final RuntimeMXBean rmBean = ManagementFactory.getRuntimeMXBean();
        final List<String> inputArguments = rmBean.getInputArguments();
        for (String arg : inputArguments) {
            result.append(arg).append(" ");
        }
        return result.toString();
    }

    static final class HostControllerExecutorService implements Service<ExecutorService> {
        final ThreadFactory threadFactory;
        private ExecutorService executorService;

        private HostControllerExecutorService(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        @Override
        public synchronized void start(final StartContext context) throws StartException {
            executorService = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                    5L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    threadFactory);
        }

        @Override
        public synchronized void stop(final StopContext context) {
            Thread executorShutdown = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        executorService.shutdown();
                    } finally {
                        executorService = null;
                        context.complete();
                    }
                }
            }, "HostController ExecutorService Shutdown Thread");
            executorShutdown.start();
            context.asynchronous();
        }

        @Override
        public synchronized ExecutorService getValue() throws IllegalStateException {
            return executorService;
        }
    }

    static final class HostControllerScheduledExecutorService implements Service<ScheduledExecutorService> {
        private final ThreadFactory threadFactory;
        private ScheduledThreadPoolExecutor scheduledExecutorService;
        private final InjectedValue<ExecutorService> executorInjector = new InjectedValue<>();

        private HostControllerScheduledExecutorService(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        @Override
        public synchronized void start(final StartContext context) throws StartException {
            scheduledExecutorService = new ScheduledThreadPoolExecutor(4 , threadFactory);
            scheduledExecutorService.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public synchronized void stop(final StopContext context) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        scheduledExecutorService.shutdown();
                    } finally {
                        scheduledExecutorService = null;
                        context.complete();
                    }
                }
            };
            try {
                executorInjector.getValue().execute(r);
            } catch (RejectedExecutionException e) {
                r.run();
            } finally {
                context.asynchronous();
            }
        }

        @Override
        public synchronized ScheduledExecutorService getValue() throws IllegalStateException {
            return scheduledExecutorService;
        }
    }
}
