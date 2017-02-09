/*
Copyright 2017 Red Hat, Inc.

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

package org.jboss.as.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StabilityMonitor;
import org.wildfly.common.Assert;

/**
 * Integrates the restart (i.e. stop and then start) of an {@link org.jboss.msc.service.Service}
 * with any concurrently executing management operation.
 *
 * @author Brian Stansberry
 */
public final class ServiceRestartController {

    private static final Logger log = Logger.getLogger(ServiceRestartController.class);

    private final Set<ServiceName> controllersToStop = new HashSet<ServiceName>();
    private final Set<ServiceName> controllersToStart = new HashSet<ServiceName>();
    // GuardedBy this
    private boolean inManagementWriteOp;

    /**
     * Notification that a service needs to be restarted. This object then takes responsibility for the restart.
     *
     * @param toRestart the name of the service. Cannot be {@code null}
     * @param serviceRegistry registry to use for finding the service controller for services that need restart
     *
     * @deprecated this is largely a workaround to issues like MSC-155 and MSC-156 and may be removed once those are fixed
     */
    @Deprecated
    public synchronized void serviceRequiresRestart(ServiceName toRestart, ServiceRegistry serviceRegistry) {
        Assert.assertNotNull(toRestart);
        if (inManagementWriteOp) {
            log.debugf("Restart of service %s has been requested " +
                    "during the execution of a writing management operation", toRestart);
            synchronized (controllersToStop) {
                controllersToStop.add(toRestart);
            }
        } else {
            // We're not in a management op so we are not deferring restart until stable.
            // Just restart immediately. This is really just here for safety.
            ControllerLogger.ROOT_LOGGER.serviceRestartRequestedOutsideManagementOp(toRestart);
            restartService(toRestart, serviceRegistry, true);
        }
    }

    /** Notification that a management write op has begun, so restart requests should be integrated with it */
    synchronized void enterManagementOperation() {
        inManagementWriteOp = true;
    }

    /** Notification that the management write op is no longer executing, so restart requests should be handled immediately */
    synchronized void exitManagementOperation() {
        inManagementWriteOp = false;
    }

    /**
     * Await stability and then process any pending restarts, looping until no more restarts are queued following
     * stability.
     *
     * @param blockingTimeout timeout control. Will be {@link BlockingTimeout#timeoutDetected() signalled}
     *                        if stability cannot be achieved within its
     *                        {@link BlockingTimeout#getLocalBlockingTimeout() local timeout}
     * @param serviceRegistry registry to use for finding the service controller for services that need restart
     * @param stabilityMonitor monitor to use for checking for stability
     * @throws InterruptedException if execution is interrupted while awaiting stability
     * @throws StabilityTimeoutException if stability could not be reached within the timeout
     */
    void processRestarts(BlockingTimeout blockingTimeout, ServiceRegistry serviceRegistry, StabilityMonitor stabilityMonitor) throws InterruptedException, StabilityTimeoutException {

        // If any timeout occurs awaiting stability, we still want to process any configured restarts
        // so we just hold onto it to throw at the end
        long failedTimeout = -1;
        long origFailedTimeout = -1; // if we fail then succeed then fail, report the first timeout as it is more likely a value meaningful to the user

        boolean loop;
        int loopCount = 0;
        do {
            loop = false;
            loopCount++;

            long timeout = blockingTimeout.getLocalBlockingTimeout();
            if (!stabilityMonitor.awaitStability(timeout, TimeUnit.MILLISECONDS)) {
                blockingTimeout.timeoutDetected();
                if (failedTimeout < 0) {
                    failedTimeout = timeout;
                    if (origFailedTimeout < 0) {
                        // This is the first failure so we report this value
                        origFailedTimeout = timeout;
                    }
                }
                log.debugf("Failed to achieve initial stability after %d ms in process restart loop %d", timeout, loopCount);
            } else {
                // We're stable now even if we were not before
                failedTimeout = -1;
            }

            Set<ServiceName> stopClone = null;
            synchronized (controllersToStop) {
                if (!controllersToStop.isEmpty()) {
                    stopClone = new HashSet<ServiceName>(controllersToStop);
                    controllersToStop.clear();
                }
            }
            if (stopClone != null) {
                loop = true;
                for (ServiceName serviceName : stopClone) {
                    restartService(serviceName, serviceRegistry, false);
                }
                timeout = blockingTimeout.getLocalBlockingTimeout();
                if (!stabilityMonitor.awaitStability(timeout, TimeUnit.MILLISECONDS)) {
                    blockingTimeout.timeoutDetected();
                    if (failedTimeout < 0) {
                        failedTimeout = timeout;
                        if (origFailedTimeout < 0) {
                            // This is the first failure so we report this value
                            origFailedTimeout = timeout;
                        }
                    }
                    log.debugf("Failed to achieve post-service stop stability after %d ms in process restart loop %d", timeout, loopCount);
                }
            }

            Set<ServiceName> startClone = null;
            synchronized (controllersToStart) {
                if (!controllersToStart.isEmpty()) {
                    startClone = new HashSet<ServiceName>(controllersToStart);
                    controllersToStart.clear();
                }
            }

            if (startClone != null) {
                loop = true;
                for (ServiceName serviceName : startClone) {
                    ServiceController<?>  svcController = serviceRegistry.getService(serviceName);
                    if (svcController != null && svcController.getState() != ServiceController.State.REMOVED) {
                        log.debugf("Starting service %s as part of a requested restart", serviceName);
                        svcController.setMode(ServiceController.Mode.ACTIVE);
                    } else {
                        log.debugf("Service %s has been removed; will not start again", serviceName);
                    }
                }
            }
        } while (loop);

        if (failedTimeout > -1) {
            throw new StabilityTimeoutException(origFailedTimeout);
        }
    }

    private void restartService(final ServiceName serviceName, final ServiceRegistry serviceRegistry,
                                final boolean immediateActivate) {
        ServiceController<?> serviceController = serviceRegistry.getService(serviceName);
        if (serviceController != null && serviceController.getState() != ServiceController.State.REMOVED) {
            serviceController.addListener(new AbstractServiceListener<Object>() {
                @Override
                public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                    if(transition.getAfter() == ServiceController.Substate.DOWN) {
                        if (immediateActivate) {
                            log.debugf("Service %s is DOWN; immediately restarting", serviceName);
                            controller.setMode(ServiceController.Mode.ACTIVE);
                        } else {
                            log.debugf("Service %s is DOWN; recording it for subsequent start once stability is obtained", serviceName);
                            synchronized (controllersToStart) {
                                controllersToStart.add(controller.getName());
                            }
                        }
                        controller.removeListener(this);
                    }
                }
            });
            log.debugf("Stopping service %s as part of a requested restart", serviceName);
            serviceController.setMode(ServiceController.Mode.NEVER);
        } else {
            log.debugf("Service %s has been removed; ignoring restart instruction", serviceName);
        }
    }
}
