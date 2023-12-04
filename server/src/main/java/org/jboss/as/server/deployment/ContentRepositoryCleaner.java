/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_HOST_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.operations.CleanObsoleteContentHandler;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * In charge with checking content references and syncing them with the content repository, removing to left over contents.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
class ContentRepositoryCleaner {

    private final LocalModelControllerClient client;
    private final ProcessStateNotifier processStateNotifier;
    private final ScheduledExecutorService scheduledExecutor;

    private long cleanInterval = 0L;
    private volatile boolean enabled;
    private final boolean server;
    private ScheduledFuture<?> cleanTask;

    private final ContentRepositoryCleanerTask cleanRunnable = new ContentRepositoryCleanerTask();

    private class ContentRepositoryCleanerTask implements Runnable {

        @Override
        public void run() {
            cleanObsoleteContent();
        }
    }

    public ContentRepositoryCleaner(LocalModelControllerClient client, ProcessStateNotifier processStateNotifier,
                                    ScheduledExecutorService scheduledExecutor, long interval, boolean server) {
        this.processStateNotifier = processStateNotifier;
        this.client = client;
        this.scheduledExecutor = scheduledExecutor;
        this.enabled = true;
        this.cleanInterval = interval;
        this.server = server;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getCleanInterval() {
        return cleanInterval;
    }

    /**
     * Invoke with the object monitor held
     */
    private void cancelScan() {
        if (cleanTask != null) {
            cleanTask.cancel(true);
            cleanTask = null;
        }
        client.close();
    }

    synchronized void startScan() {
        if (enabled) {
            cleanTask = scheduledExecutor.scheduleWithFixedDelay(cleanRunnable, cleanInterval, cleanInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * {@inheritDoc}
     */
    synchronized void stopScan() {
        this.enabled = false;
        cancelScan();
    }

    void cleanObsoleteContent() {
        if (processStateNotifier.getCurrentState() == ControlledProcessState.State.RUNNING) {
            PathAddress address = PathAddress.EMPTY_ADDRESS;
            if (!server) {
                ModelNode request = Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, LOCAL_HOST_NAME);
                ModelNode response = privilegedExecution().execute(client::execute, request);
                if (SUCCESS.equals(response.get(OUTCOME).asString()) && response.get(RESULT).isDefined()) {
                    address = address.append(HOST, response.get(RESULT).asString());
                } else if (CANCELLED.equals(response.get(OUTCOME).asString())) {
                    return;
                } else if (FAILED.equals(response.get(OUTCOME).asString())) {
                    error(response);
                    // if we can't read the local-host-name on a host controller, don't try and run the content cleaner
                    return;
                }
            }
            ModelNode request = Util.createOperation(CleanObsoleteContentHandler.OPERATION_NAME, address);
            ModelNode response = privilegedExecution().execute(client::execute, request);
            if (SUCCESS.equals(response.get(OUTCOME).asString())) {
                if(response.get(RESULT).isDefined()) {
                    ServerLogger.ROOT_LOGGER.debug(response.get(RESULT));
                }
            } else if (FAILED.equals(response.get(OUTCOME).asString())) {
                error(response);
            }
        }
    }

    private void error(ModelNode response) {
        if (response.hasDefined(FAILURE_DESCRIPTION)) {
            ServerLogger.ROOT_LOGGER.failedToCleanObsoleteContent(response.get(FAILURE_DESCRIPTION).asString());
        } else {
            ServerLogger.ROOT_LOGGER.failedToCleanObsoleteContent(response.asString());
        }
    }

    /** Provides function execution in a doPrivileged block if a security manager is checking privileges */
    private static Execution privilegedExecution() {
        return WildFlySecurityManager.isChecking() ? Execution.PRIVILEGED : Execution.NON_PRIVILEGED;
    }

    /** Executes a function */
    private interface Execution {
        <T, R> R execute(Function<T, R> function, T t);

        Execution NON_PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                return function.apply(t);
            }
        };

        Execution PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(final Function<T, R> function, final T t) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<R>) () -> NON_PRIVILEGED.execute(function, t) );
                } catch (PrivilegedActionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        // Not possible as Function doesn't throw any checked exception
                        throw new RuntimeException(cause);
                    }
                }
            }
        };

    }
}
