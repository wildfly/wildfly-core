package org.jboss.as.server.suspend;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

    //TODO: should this notification handling be placed into its own class
    private static final PathAddress NOTIFICATION_ADDRESS = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT).append(SERVICE, MANAGEMENT_OPERATIONS);

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("server", "suspend-controller");

    /**
     * Timer that handles the timeout. We create it on pause, rather than leaving it hanging round.
     */
    private Timer timer;

    private State state = State.SUSPENDED;

    private final List<ServerActivity> activities = new ArrayList<>();

    private final List<OperationListener> operationListeners = new ArrayList<>();

    private final InjectedValue<ModelController> modelControllerInjectedValue = new InjectedValue<>();

    private int outstandingCount;

    private boolean startSuspended;

    private final ServerActivityCallback listener = () -> activityPaused();

    public SuspendController() {
        this.startSuspended = false;
    }

    public void setStartSuspended(boolean startSuspended) {
        this.startSuspended = startSuspended;
        state = State.SUSPENDED;
    }

    public synchronized void suspend(long timeoutMillis) {
        if(state == State.SUSPENDED) {
            return;
        }
        if (timeoutMillis > 0) {
            ServerLogger.ROOT_LOGGER.suspendingServer(timeoutMillis);
        } else {
            ServerLogger.ROOT_LOGGER.suspendingServerWithNoTimeout();
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
            timer = new Timer();
            if (timeoutMillis > 0) {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        timeout();
                    }
                }, timeoutMillis);
            }
        }
    }

    public synchronized void resume() {
        if (state == State.RUNNING) {
            return;
        }
        ServerLogger.ROOT_LOGGER.resumingServer();
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
                ServerLogger.ROOT_LOGGER.failedToResume(activity);
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
        if(!startSuspended) {
            final NotificationFilter filter = notification -> notification.getType().equals(ModelDescriptionConstants.BOOT_COMPLETE_NOTIFICATION);
            NotificationHandler handler = new NotificationHandler() {
                @Override
                public void handleNotification(Notification notification) {
                    resume();
                    modelControllerInjectedValue.getValue().getNotificationRegistry().unregisterNotificationHandler(NOTIFICATION_ADDRESS, this, filter);
                }
            };
            modelControllerInjectedValue.getValue().getNotificationRegistry().registerNotificationHandler(NOTIFICATION_ADDRESS, handler, filter);
            //if the service bounces we don't want to auto resume if we are suspended
            startSuspended = true;
        } else {
            ServerLogger.AS_ROOT_LOGGER.startingServerSuspended();
        }
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
    }

    public State getState() {
        return state;
    }

    synchronized void activityPaused() {
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

    synchronized void timeout() {
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

    public InjectedValue<ModelController> getModelControllerInjectedValue() {
        return modelControllerInjectedValue;
    }

    public enum State {
        RUNNING,
        PRE_SUSPEND,
        SUSPENDING,
        SUSPENDED
    }
}
