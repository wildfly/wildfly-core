/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.domain.http.server.ConsoleAvailabilityService;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.remoting.HttpListenerRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.BootstrapListener;
import org.jboss.as.server.ElapsedTime;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.ProductConfig;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.EnhancedQueueExecutor;
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

    private static final ThreadGroup THREAD_GROUP = new ThreadGroup("Host Controller Service Threads");
    private final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
        public JBossThreadFactory run() {
            return new JBossThreadFactory(THREAD_GROUP, Boolean.FALSE, null, "%G - %t", null, null);
        }
    });
    private final HostControllerEnvironment environment;
    private final HostRunningModeControl runningModeControl;
    private final ControlledProcessState processState;
    private final String authCode;
    private final CapabilityRegistry capabilityRegistry;
    private final ElapsedTime elapsedTime;
    private volatile FutureServiceContainer futureContainer;
    private volatile boolean everStopped;

    public HostControllerService(final HostControllerEnvironment environment, final HostRunningModeControl runningModeControl,
                          final String authCode, final ControlledProcessState processState, FutureServiceContainer futureContainer) {
        this.environment = environment;
        this.runningModeControl = runningModeControl;
        this.authCode = authCode;
        this.processState = processState;
        this.elapsedTime = environment.getElapsedTime();
        this.futureContainer = futureContainer;
        this.capabilityRegistry = new CapabilityRegistry(false);
    }

    public HostControllerService(final HostControllerEnvironment environment, final HostRunningModeControl runningModeControl,
                                 final String authCode, final ControlledProcessState processState) {
        this(environment, runningModeControl, authCode, processState, new FutureServiceContainer());
    }

    @Override
    public void start(StartContext context) throws StartException {

        // If this is a reload, track start time independently of the overall process elapsed time
        ElapsedTime startupTime = everStopped ? elapsedTime.checkpoint() : elapsedTime;

        final ProductConfig config = environment.getProductConfig();
        final String prettyVersion = config.getPrettyVersionString();
        final String banner = environment.getStability() == org.jboss.as.version.Stability.EXPERIMENTAL ? config.getBanner() : "";
        ServerLogger.AS_ROOT_LOGGER.serverStarting(prettyVersion, banner);
        if (System.getSecurityManager() != null) {
            ServerLogger.AS_ROOT_LOGGER.securityManagerEnabled();
        }
        if (ServerLogger.CONFIG_LOGGER.isDebugEnabled()) {
            final Properties properties = System.getProperties();
            final StringBuilder b = new StringBuilder(8192);
            b.append("Configured system properties:");
            for (String property : new TreeSet<String>(properties.stringPropertyNames())) {
                String propVal = property.toLowerCase(Locale.ROOT).contains("password") ? "<redacted>" : properties.getProperty(property, "<undefined>");
                if (property.toLowerCase(Locale.ROOT).equals("sun.java.command") && !propVal.isEmpty()) {
                    Pattern pattern = Pattern.compile("(-D(?:[^ ])+=)((?:[^ ])+)");
                    Matcher matcher = pattern.matcher(propVal);
                    StringBuffer sb = new StringBuffer(propVal.length());
                    while (matcher.find()) {
                        if (matcher.group(1).contains("password")) {
                            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + "<redacted>"));
                        }
                    }
                    matcher.appendTail(sb);
                    propVal = sb.toString();
                }
                if (property.toLowerCase(Locale.ROOT).equals("wildfly.config.url") && !propVal.isEmpty()) {
                    ServerLogger.CONFIG_LOGGER.wildflyConfigUrlIsSet(property + " = " + propVal);
                }
                b.append("\n\t").append(property).append(" = ").append(propVal);
            }
            ServerLogger.CONFIG_LOGGER.debug(b);
            ServerLogger.CONFIG_LOGGER.debugf("VM Arguments: %s", getVMArguments());
            if (ServerLogger.CONFIG_LOGGER.isTraceEnabled()) {
                b.setLength(0);
                final Map<String, String> env = System.getenv();
                b.append("Configured system environment:");
                for (String key : new TreeSet<String>(env.keySet())) {
                    String envVal = key.toLowerCase(Locale.ROOT).contains("password") ? "<redacted>" : env.get(key);
                    b.append("\n\t").append(key).append(" = ").append(envVal);
                }
                ServerLogger.CONFIG_LOGGER.trace(b);
            }
        }
        final ServiceTarget serviceTarget = context.getChildTarget();
        final ServiceController<?> myController = context.getController();

        final ServiceContainer serviceContainer = myController.getServiceContainer();

        final BootstrapListener bootstrapListener = new BootstrapListener(serviceContainer, startupTime, serviceTarget, futureContainer, prettyVersion + " (Host Controller)", environment.getDomainTempDir());
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


        // Install required path services. (Only install those identified as required)
        HostPathManagerService hostPathManagerService = new HostPathManagerService(capabilityRegistry);
        HostPathManagerService.addService(serviceTarget, hostPathManagerService, environment);

        HttpListenerRegistryService.install(serviceTarget);

        // Add product config service
        final ServiceBuilder<?> sb = serviceTarget.addService(Services.JBOSS_PRODUCT_CONFIG_SERVICE);
        final Consumer<ProductConfig> productConfigConsumer = sb.provides(Services.JBOSS_PRODUCT_CONFIG_SERVICE);
        sb.setInstance(org.jboss.msc.Service.newInstance(productConfigConsumer, config));
        sb.install();

        ConsoleAvailabilityService.addService(serviceTarget, bootstrapListener::logAdminConsole);

        DomainModelControllerService.addService(serviceTarget, environment, runningModeControl, processState,
                bootstrapListener, hostPathManagerService, capabilityRegistry, THREAD_GROUP);
    }

    @Override
    public void stop(StopContext context) {
        String prettyVersion = environment.getProductConfig().getPrettyVersionString();
        //Moved to AbstractControllerService.stop()
        //processState.setStopping();
        ServerLogger.AS_ROOT_LOGGER.serverStopped(prettyVersion, (int) (context.getElapsedTime() / 1000000L));
        BootstrapListener.deleteStartupMarker(environment.getDomainTempDir());
        everStopped = true;
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
            if (EnhancedQueueExecutor.DISABLE_HINT) {
                executorService = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                    5L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    threadFactory);
            } else {
                executorService = new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(1)
                    .setMaximumPoolSize(4096)
                    .setKeepAliveTime(5L, TimeUnit.SECONDS)
                    .setThreadFactory(threadFactory)
                    .build();
            }
        }

        @Override
        public synchronized void stop(final StopContext context) {
            Thread executorShutdown = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean interrupted = false;
                    try {
                        executorService.shutdown();
                        executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    } finally {
                        try {
                            List<Runnable> tasks = executorService.shutdownNow();
                            executorService = null;
                            if (!interrupted) {
                                for (Runnable task : tasks) {
                                    HostControllerLogger.ROOT_LOGGER.debugf("%s -- Discarding unexecuted task %s", getClass().getSimpleName(), task);
                                }
                            }
                        } finally {
                            context.complete();
                        }
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
