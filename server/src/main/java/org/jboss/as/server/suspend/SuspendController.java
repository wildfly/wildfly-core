package org.jboss.as.server.suspend;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * The graceful shutdown controller. This class co-ordinates the graceful shutdown and pause/resume of a
 * servers operations.
 * <p/>
 * <p/>
 * In most cases this work is delegated to the request controller subsystem.
 * however for workflows that do no correspond directly to a request model a {@link ServerActivity} instance
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

    private final List<ServerActivity> activities = new ArrayList<>();

    private final List<OperationListener> operationListeners = new ArrayList<>();

    private final InjectedValue<NotificationHandlerRegistry> notificationHandlerRegistry = new InjectedValue<>();

    private int outstandingCount;

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
        outstandingCount = activities.size();
        if (outstandingCount == 0) {
            handlePause();
        } else {
            CountingRequestCountCallback cb = new CountingRequestCountCallback(outstandingCount, () -> {
                state = State.SUSPENDING;
                for (ServerActivity activity : activities) {
                    activity.suspended(SuspendController.this.listener);
                }
            });

            for (ServerActivity activity : activities) {
                activity.preSuspend(cb);
            }
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
        for(OperationListener listener: new ArrayList<>(operationListeners)) {
            listener.cancelled();
        }
        for (ServerActivity activity : activities) {
            try {
                activity.resume();
            } catch (Exception e) {
                ServerLogger.ROOT_LOGGER.failedToResume(activity, e);
            }
        }
        state = State.RUNNING;
    }

    public synchronized void registerActivity(final ServerActivity activity) {
        this.activities.add(activity);
        if(state != State.RUNNING) {
            //if the activity is added when we are not running we just immediately suspend it
            //this should only happen at boot, so there should be no outstanding requests anyway
            activity.suspended(() -> {

            });
        }
    }

    public synchronized void unRegisterActivity(final ServerActivity activity) {
        this.activities.remove(activity);
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
        --outstandingCount;
        handlePause();
    }

    private void handlePause() {
        if (outstandingCount == 0) {
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

    public InjectedValue<NotificationHandlerRegistry> getNotificationHandlerRegistry() {
        return notificationHandlerRegistry;
    }

    public enum State {
        RUNNING,
        PRE_SUSPEND,
        SUSPENDING,
        SUSPENDED
    }
}
