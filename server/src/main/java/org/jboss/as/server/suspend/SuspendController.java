/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;

/**
 * The graceful shutdown controller. This class co-ordinates the graceful shutdown and pause/resume of a
 * servers operations.
 * <p/>
 * <p/>
 * In most cases this work is delegated to the request controller subsystem.
 * however for workflows that do not correspond directly to a request model a {@link ServerActivity} instance
 * can be registered directly with this controller.
 *
 * @author Stuart Douglas
 */
public class SuspendController implements Service<SuspendController> {

    /**
     * Timer that handles the timeout. We create it on pause, rather than leaving it hanging round.
     */
    private Timer timer;

    private State state = State.SUSPENDED;

    private final NavigableMap<Integer, List<ServerActivity>> activitiesByGroup = new TreeMap<>();

    private final List<OperationListener> operationListeners = new ArrayList<>();

    private int groupsCount;

    private boolean startSuspended;

    private final ServerActivityCallback listener = this::activityPaused;

    public SuspendController() {
        this.startSuspended = false;
    }

    public void setStartSuspended(boolean startSuspended) {
        //TODO: it is not very clear what this boolean stands for now.
        this.startSuspended = startSuspended;
        state = State.SUSPENDED;
    }

    public synchronized void suspend(long timeoutMillis) {
        if(state == State.SUSPENDED) {
            return;
        }
        if (timeoutMillis > 0) {
            ServerLogger.ROOT_LOGGER.suspendingServer(timeoutMillis);
        } else if (timeoutMillis < 0) {
            ServerLogger.ROOT_LOGGER.suspendingServerWithNoTimeout();
        } else {
            ServerLogger.ROOT_LOGGER.suspendingServer();
        }
        state = State.PRE_SUSPEND;
        //we iterate a copy, in case a listener tries to register a new listener
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.suspendStarted();
        }
        groupsCount = activitiesByGroup.size();
        if (groupsCount == 0) {
            handlePause();
        } else {
            // Set up the logic that will handle the 'suspended' calls when all the preSuspend calls have reported 'done'
            CountingRequestCountCallback preSuspendGroupCallBack = new CountingRequestCountCallback(groupsCount, () -> {
                state = State.SUSPENDING;
                processGroups(activitiesByGroup.values().iterator(), (executionGroup, cb) -> {
                    for (ServerActivity activity : executionGroup) {
                        // TODO considering making this concurrent by passing this call as a task to an executor.
                        // This would allow each activity a "fair" share of the timeout budget
                        // Alternatively we could iterate executionGroup in reverse (LIFO) order.
                        // But the executionGroups themselves already provide an ability for that kind of ordering
                        activity.suspended(cb);
                    }
                }, SuspendController.this.listener);
            });

            // Invoke the preSuspend calls
            processGroups(activitiesByGroup.values().iterator(), (executionGroup, cb) -> {
                for (ServerActivity activity : executionGroup) {
                    // TODO see the 'suspended' section comment above re possible concurrent or LIFO execution
                    activity.preSuspend(cb);
                }
            }, preSuspendGroupCallBack);

            if (timeoutMillis > 0) {
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        timeout();
                    }
                }, timeoutMillis);
            } else if (timeoutMillis == 0) {
                timeout();
            }
        }
    }

    public void nonGracefulStart() {
        resume(false);
    }

    public void resume() {
        resume(true);
    }

    private synchronized void resume(boolean gracefulStart) {
        if (state == State.RUNNING) {
            return;
        }
        if (!gracefulStart) {
            ServerLogger.ROOT_LOGGER.startingNonGraceful();
        } else {
            ServerLogger.ROOT_LOGGER.resumingServer();
        }

        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        for (OperationListener listener : new ArrayList<>(operationListeners)) {
            listener.cancelled();
        }
        for (List<ServerActivity> executionGroup : activitiesByGroup.descendingMap().values()) {
            for (ServerActivity activity : executionGroup) {
                try {
                    activity.resume();
                } catch (Exception e) {
                    ServerLogger.ROOT_LOGGER.failedToResume(activity, e);
                }
            }
        }
        state = State.RUNNING;
    }

    /**
     * Registers the given {@link ServerActivity} with this controller
     * @param activity the activity. Cannot be {@code null}
     * @throws IllegalArgumentException if {@code activity} is {@code null} of if its
     *                                  {@link ServerActivity#getExecutionGroup() getExecutionGroup()} method
     *                                  returns a value outside of that method's documented legal range.
     */
    public synchronized void registerActivity(final ServerActivity activity) {
        Assert.checkNotNullParam("activity", activity);
        Assert.checkMinimumParameter("activity.getExecutionGroup()", ServerActivity.LOWEST_EXECUTION_GROUP, activity.getExecutionGroup());
        Assert.checkMaximumParameter("activity.getExecutionGroup()", ServerActivity.HIGHEST_EXECUTION_GROUP, activity.getExecutionGroup());
        List<ServerActivity> executionGroup = this.activitiesByGroup.computeIfAbsent(activity.getExecutionGroup(), ArrayList::new);
        executionGroup.add(activity);
        if(state != State.RUNNING) {
            //if the activity is added when we are not running we just immediately suspend it
            //this should only happen at boot, so there should be no outstanding requests anyway
            // note that this means there is no execution group grouping of these calls.
            activity.suspended(() -> {

            });
        }
    }

    public synchronized void unRegisterActivity(final ServerActivity activity) {
        List<ServerActivity> executionGroup = activitiesByGroup.get(activity.getExecutionGroup());
        if (executionGroup != null) {
            executionGroup.remove(activity);
            if (executionGroup.isEmpty()) {
                activitiesByGroup.remove(activity.getExecutionGroup());
            }
        }
    }

    @Override
    public synchronized void start(StartContext startContext) throws StartException {
        if(startSuspended) {
            ServerLogger.AS_ROOT_LOGGER.startingServerSuspended();
        }
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
    }

    public State getState() {
        return state;
    }

    private synchronized void activityPaused() {
        --groupsCount;
        handlePause();
    }

    private void handlePause() {
        if (groupsCount == 0) {
            state = State.SUSPENDED;
            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            for(OperationListener listener: new ArrayList<>(operationListeners)) {
                listener.complete();
            }
        }
    }

    private synchronized void timeout() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.timeout();
        }
    }


    public synchronized void addListener(final OperationListener listener) {
        operationListeners.add(listener);
    }

    public synchronized void removeListener(final OperationListener listener) {
        operationListeners.remove(listener);
    }

    @Override
    public SuspendController getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    private void processGroups(Iterator<List<ServerActivity>> iterator,
                               BiConsumer<List<ServerActivity>, ServerActivityCallback> groupFunction,
                               ServerActivityCallback groupsCallback) {
        // Take the first element from the iterator and apply the groupFunction, with a callback that
        // calls this again to take the next element when all activities from the current element are done.
        // When no elements are left, tell the groupsCallback we are done.
        if (iterator.hasNext()) {
            List<ServerActivity> activityList = iterator.next();
            CountingRequestCountCallback cb = new CountingRequestCountCallback(activityList.size(), () -> {
                processGroups(iterator, groupFunction, groupsCallback);
                groupsCallback.done();
            });
            groupFunction.accept(activityList, cb);
        }
    }

    public enum State {
        RUNNING,
        PRE_SUSPEND,
        SUSPENDING,
        SUSPENDED
    }
}
