/*
* JBoss, Home of Professional Open Source.
* Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.remote;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.ModelController;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;

/**
 * Service used to create operation handlers per incoming channel
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractModelControllerOperationHandlerFactoryService implements Service<AbstractModelControllerOperationHandlerFactoryService>, ManagementChannelInitialization {

    public static final ServiceName OPERATION_HANDLER_NAME_SUFFIX = ServiceName.of("operation", "handler");

    // The defaults if no executor was defined
    private static final int WORK_QUEUE_SIZE = 512;
    private static final int POOL_CORE_SIZE = 4;
    private static final int POOL_MAX_SIZE = 4;

    private final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer;
    private final Supplier<ModelController> modelControllerSupplier;
    private final Supplier<ExecutorService> executorSupplier;
    private final Supplier<ScheduledExecutorService> scheduledExecutorSupplier;

    private ResponseAttachmentInputStreamSupport responseAttachmentSupport;
    private ExecutorService clientRequestExecutor;

    protected AbstractModelControllerOperationHandlerFactoryService(
            final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
            final Supplier<ModelController> modelControllerSupplier,
            final Supplier<ExecutorService> executorSupplier,
            final Supplier<ScheduledExecutorService> scheduledExecutorSupplier
    ) {
        this.serviceConsumer = serviceConsumer;
        this.modelControllerSupplier = modelControllerSupplier;
        this.executorSupplier = executorSupplier;
        this.scheduledExecutorSupplier = scheduledExecutorSupplier;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        MGMT_OP_LOGGER.debugf("Starting operation handler service %s", context.getController().provides());
        responseAttachmentSupport = new ResponseAttachmentInputStreamSupport(scheduledExecutorSupplier.get());

        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
            public JBossThreadFactory run() {
                return new JBossThreadFactory(new ThreadGroup("management-handler-thread"), Boolean.FALSE, null, "%G - %t", null, null);
            }
        });
        if (EnhancedQueueExecutor.DISABLE_HINT) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(POOL_CORE_SIZE, POOL_MAX_SIZE,
                600L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(WORK_QUEUE_SIZE),
                threadFactory);
            // Allow the core threads to time out as well
            executor.allowCoreThreadTimeOut(true);
            this.clientRequestExecutor = executor;
        } else {
            this.clientRequestExecutor = new EnhancedQueueExecutor.Builder()
            .setCorePoolSize(POOL_CORE_SIZE)
            .setMaximumPoolSize(POOL_MAX_SIZE)
            .setKeepAliveTime(600L, TimeUnit.SECONDS)
            .setMaximumQueueSize(WORK_QUEUE_SIZE)
            .setThreadFactory(threadFactory)
            .allowCoreThreadTimeOut(true)
            .build();
        }
        serviceConsumer.accept(this);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(final StopContext stopContext) {
        serviceConsumer.accept(null);
        final ExecutorService executorService = executorSupplier.get();
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    responseAttachmentSupport.shutdown();
                    // Shut down new requests to the client request executor,
                    // but don't mess with currently running tasks
                    clientRequestExecutor.shutdown();
                } finally {
                    stopContext.complete();
                }
            }
        };
        try {
            executorService.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            stopContext.asynchronous();
        }

    }

    /** {@inheritDoc} */
    @Override
    public synchronized AbstractModelControllerOperationHandlerFactoryService getValue() throws IllegalStateException {
        return this;
    }

    protected ModelController getController() {
        return modelControllerSupplier.get();
    }

    protected ExecutorService getExecutor() {
        return executorSupplier.get();
    }

    protected ResponseAttachmentInputStreamSupport getResponseAttachmentSupport() {
        return responseAttachmentSupport;
    }

    protected final ExecutorService getClientRequestExecutor() {
        return clientRequestExecutor;
    }

}
