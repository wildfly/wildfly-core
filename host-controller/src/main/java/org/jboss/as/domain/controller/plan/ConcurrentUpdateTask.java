/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.plan;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A task that uses an executor service to concurrently execute other tasks
 */
class ConcurrentUpdateTask implements Runnable {

    private final List<Runnable> concurrentTasks;
    private final ExecutorService executorService;

    ConcurrentUpdateTask(final List<Runnable> concurrentTasks, final ExecutorService executorService) {
        this.concurrentTasks = concurrentTasks;
        this.executorService = executorService;
    }

    @Override
    public void run() {

        // Submit each task to the executor
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (Runnable r : concurrentTasks) {
            final Runnable task = r;
            futures.add(doPrivileged(new PrivilegedAction<Future<?>>() {

                @Override
                public Future<?> run() {
                    return executorService.submit(task);
                }
            }));
        }

        // Wait until all complete before returning
        boolean patient = true;
        for (int i = 0; i < futures.size(); i++) {
            Future<?> future = futures.get(i);
            try {
                if (patient) {
                    future.get();
                } else {
                    // We've been interrupted; see if this task is already done
                    future.get(0, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                patient = false;
                future.cancel(true);
            } catch (ExecutionException e) {
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                future.cancel(true);
            } catch (TimeoutException e) {
                // Task wasn't already done; cancel it
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                patient = false; // it should already be false if we got here, but just in case someone changes something
                future.cancel(true);
            }
        }

        if (!patient) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ConcurrentUpdateTask{tasks={");
        for (int i = 0; i < concurrentTasks.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(concurrentTasks.get(i).toString());
        }
        sb.append("}}");
        return sb.toString();
    }

}
