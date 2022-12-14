/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.server;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Provides an executor for handling external management requests.
 *
 * @deprecated may be removed at any time.
 *
 * @author Brian Stansberry
 */
@Deprecated
public class ExternalManagementRequestExecutor implements Service<ExecutorService> {

    /**
     * The service name for this service.
     *
     * @deprecated may be removed at any time
     */
    @Deprecated
    public static final ServiceName SERVICE_NAME = Services.JBOSS_AS.append("external-mgmt-executor");

    // The Executor settings.
    // We limit concurrent requests to a small number to avoid overloading a server.
    // For the native interface we limit to 4 (see AbstractModelControllerOperationHandlerFactoryService)
    // but for HTTP EAP 6 had no limit (not great) and WildFly < 10.1 had 10 with no queue, so we'll go
    // with 10 to be more like what was out there getting bake in WildFly < 10.1.
    // We provide a fairly large but not unlimited queue to avoid rejecting requests.
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int WORK_QUEUE_SIZE = 512;
    private static final String POOL_SIZE_PROP = "org.wildfly.unsupported.external.management.pool-size";

    private static int getPoolSize() {
        int defaultThreads = DEFAULT_POOL_SIZE;
        String maxThreads = WildFlySecurityManager.getPropertyPrivileged(POOL_SIZE_PROP, null);
        if (maxThreads != null && maxThreads.length() > 0) {
            try {
                int max = Integer.decode(maxThreads);
                defaultThreads = Math.max(max, 1);
            } catch (NumberFormatException ex) {
                ServerLogger.ROOT_LOGGER.failedToParseCommandLineInteger(POOL_SIZE_PROP, maxThreads);
            }
        }
        return defaultThreads;
    }

    private final InjectedValue<ExecutorService> injectedExecutor = new InjectedValue<>();
    private final ThreadGroup threadGroup;
    private ExecutorService executorService;

    @SuppressWarnings("deprecation")
    public static void install(ServiceTarget target, ThreadGroup threadGroup, ServiceName cleanupExecutor,
                               StabilityMonitor monitor) {
        final ExternalManagementRequestExecutor service = new ExternalManagementRequestExecutor(threadGroup);
        ServiceController<?> controller = target.addService(SERVICE_NAME, service)
                .addDependency(cleanupExecutor, ExecutorService.class, service.injectedExecutor)
                .setInitialMode(ServiceController.Mode.ON_DEMAND).install();
        monitor.addController(controller);
    }

    private ExternalManagementRequestExecutor(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final String namePattern = "External Management Request Threads -- %t";
        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<ThreadFactory>() {
            public ThreadFactory run() {
                return new JBossThreadFactory(threadGroup, Boolean.FALSE, null, namePattern, null, null);
            }
        });

        int poolSize = getPoolSize();
        if (EnhancedQueueExecutor.DISABLE_HINT) {
            final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(WORK_QUEUE_SIZE);
            executorService = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                    workQueue, threadFactory);
        } else {
            executorService = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(poolSize)
                .setMaximumPoolSize(poolSize)
                .setKeepAliveTime(60L, TimeUnit.SECONDS)
                .setMaximumQueueSize(WORK_QUEUE_SIZE)
                .setThreadFactory(threadFactory)
                .build();
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {

        if (executorService != null) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        executorService.shutdown();
                    } finally {
                        executorService = null;
                        context.complete();
                    }
                }
            };
            final ExecutorService executorService = injectedExecutor.getValue();
            try {
                try {
                    executorService.execute(r);
                } catch (RejectedExecutionException e) {
                    r.run();
                }
            } finally {
                context.asynchronous();
            }
        }
    }

    @Override
    public synchronized ExecutorService getValue() throws IllegalStateException, IllegalArgumentException {
        return executorService;
    }
}
