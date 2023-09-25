/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * The overall state of a process that is being managed by a {@link ModelController}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ControlledProcessState {

    public enum State {
        /**
         * The process is starting and its runtime state is being made consistent with its persistent configuration.
         */
        STARTING("starting", false),
        /**
         * The process is started, is running normally and has a runtime state consistent with its persistent configuration.
         */
        RUNNING("running", true),
        /**
         * The process requires a stop and re-start of its root service (but not a full process restart) in order to
         * ensure stable operation and/or to bring its running state in line with its persistent configuration. A
         * stop and restart of the root service (also known as a 'reload') will result in the removal of all other
         * services and creation of new services based on the current configuration, so its affect on availability to
         * handle external requests is similar to that of a full process restart. However, a reload can execute more
         * quickly than a full process restart.
         */
        RELOAD_REQUIRED("reload-required", true),
        /**
         * The process must be terminated and replaced with a new process in order to ensure stable operation and/or to bring
         * the running state in line with the persistent configuration.
         */
        RESTART_REQUIRED("restart-required", true),
        /** The process is stopping. */
        STOPPING("stopping", false),
        /** The process is stopped */
        STOPPED("stopped", false);

        private final String stringForm;
        private final boolean running;

        State(final String stringForm, final boolean running) {
            this.stringForm = stringForm;
            this.running = running;
        }

        /**
         * Gets whether this state represents a process that is fully 'running'; i.e. it is not still starting
         * and has not begun or completed stopping. The {@link #RUNNING}, {@link #RELOAD_REQUIRED} and {@link #RESTART_REQUIRED}
         * states are all 'running', just with different relationships between the currently running process
         * configuration and its persistent configuration.
         *
         * @return {@code true} if the state indicates the process if fully running.
         */
        public boolean isRunning() {
            return running;
        }

        @Override
        public String toString() {
            return stringForm;
        }

    }

    private final AtomicInteger stamp = new AtomicInteger(0);
    private final AtomicStampedReference<State> state = new AtomicStampedReference<>(State.STARTING, 0);
    private final boolean reloadSupported;
    private final ControlledProcessStateService service;

    private boolean restartRequiredFlag = false;

    private boolean reloadRequiredOnStarting = false;
    private boolean restartRequiredOnStarting = false;

    public ControlledProcessState(final boolean reloadSupported) {
        this.reloadSupported = reloadSupported;
        service = new ControlledProcessStateService(State.STOPPED);
    }

    public State getState() {
        return state.getReference();
    }

    public boolean isReloadSupported() {
        return reloadSupported;
    }


    public void setStarting() {
        synchronized (service) {
            state.set(State.STARTING, stamp.incrementAndGet());
            service.stateChanged(State.STARTING);
        }
    }

    public void setRunning() {
        int newStamp = stamp.incrementAndGet();
        int[] receiver = new int[1];
        // Keep trying until state is set with our stamp
        for (;;) {
            State was = state.get(receiver);
            if (was != State.STARTING) { // AS7-1103 only transition to running from STARTING
                break;
            }
            synchronized (service) {
                State newState =  restartRequiredOnStarting ? State.RESTART_REQUIRED
                        : reloadRequiredOnStarting ? State.RELOAD_REQUIRED
                        : restartRequiredFlag ? State.RESTART_REQUIRED : State.RUNNING;
                // If we require reload or restart coming out of STARTING, leave the stamp that was
                // associated with 'starting'. That's the stamp that would have been returned from
                // setRe[load|start]Required so leaving 'state' with that stamp allows whoever
                // called that to successfully call revertRe[load|start]Required.
                int stamp = restartRequiredOnStarting || reloadRequiredOnStarting ? receiver[0] : newStamp;
                if (state.compareAndSet(was, newState, receiver[0], stamp)) {
                    restartRequiredOnStarting = false;
                    reloadRequiredOnStarting = false;
                    service.stateChanged(newState);
                    break;
                }
            }
        }
    }

    public void setStopping() {
        synchronized (service) {
            restartRequiredOnStarting = false;
            reloadRequiredOnStarting = false;
            state.set(State.STOPPING, stamp.incrementAndGet());
            service.stateChanged(State.STOPPING);
        }
    }

    public void setStopped() {
        synchronized (service) {
            restartRequiredOnStarting = false;
            reloadRequiredOnStarting = false;
            state.set(State.STOPPED, stamp.incrementAndGet());
            service.stateChanged(State.STOPPED);
        }
    }

    public Object setReloadRequired() {
        if (!reloadSupported) {
            return setRestartRequired();
        }
        int newStamp = stamp.incrementAndGet();
        int[] receiver = new int[1];

        // The following block assumes state.compareAndSet is not used to change
        // the State outside a synchronized block that uses the same "service" monitor.
        // Otherwise, this block should be run in a loop until state is set with our stamp
        int result;
        synchronized (service) {
            State was = state.get(receiver);

            if (was == State.STARTING) {
                reloadRequiredOnStarting = true;
                // return the current 'starting' stamp. That is what we'll set state to
                // in setRunning if reloadRequiredOnStarting remains true, so if a
                // revertReloadRequired() call comes in with this stamp
                // after setRunning is called, it can match the then current stamp and revert.
                result = receiver[0];
            } else if (was != State.STOPPING && was != State.RESTART_REQUIRED // ignore reload required when stopping or requiring restart
                    && state.compareAndSet(was, State.RELOAD_REQUIRED, receiver[0], newStamp)) {
                service.stateChanged(State.RELOAD_REQUIRED);
                result = newStamp;
            } else {
                // We're in a situation where moving to RELOAD_REQUIRED didn't happen.
                // So, return the newStamp value, which is a throwaway value not recorded
                // in 'state'. So a revertReloadRequired call with this stamp correctly can't do anything.
                result = newStamp;
            }
        }
        return result;
    }

    public Object setRestartRequired() {
        int newStamp = stamp.incrementAndGet();
        int[] receiver = new int[1];

        // The following block assumes state.compareAndSet is not used to change
        // the State outside a synchronized block that uses the same "service" monitor.
        // Otherwise, this block should be run in a loop until state is set with our stamp
        int result;
        synchronized (service) {
            State was = state.get(receiver);
            if (was == State.STARTING) {
                restartRequiredOnStarting = true;
                restartRequiredFlag = true;
                // return the current 'starting' stamp. That is what we'll set state to
                // in setRunning if restartRequiredOnStarting remains true, so if a
                // revertRestartRequired() call comes in with this stamp
                // after setRunning is called, it can match the then current stamp and revert.
                result = receiver[0];
            } else if (was != State.STOPPING // ignore reload required when stopping
                    && state.compareAndSet(was, State.RESTART_REQUIRED, receiver[0], newStamp)) {
                restartRequiredFlag = true;
                service.stateChanged(State.RESTART_REQUIRED);
                result = newStamp;
            } else {
                // We're in a situation where moving to RELOAD_REQUIRED didn't happen.
                // So, return the newStamp value, which is a throwaway value not recorded
                // in 'state'. So a revertReloadRequired call with this stamp correctly can't do anything.
                result = newStamp;
            }
        }

        return result;
    }

    public void revertReloadRequired(Object stamp) {
        if (!reloadSupported) {
            revertRestartRequired(stamp);
        }

        // If 'state' still has the state we last set in restartRequired(), change to RUNNING
        Integer theirStamp = Integer.class.cast(stamp);
        synchronized (service) {
            if (reloadRequiredOnStarting) {
                // setRunning hasn't been called yet, so just unset the flag so we go to RUNNING when it is called.
                // TODO consider checking that stamp equals the current stamp stored in 'state'
                reloadRequiredOnStarting = false;
            } else {
                if (state.compareAndSet(State.RELOAD_REQUIRED, State.RUNNING, theirStamp, this.stamp.incrementAndGet())) {
                    service.stateChanged(State.RUNNING);
                }
            }
        }
    }

    public void revertRestartRequired(Object stamp) {
        // If 'state' still has the state we last set in restartRequired(), change to RUNNING
        Integer theirStamp = Integer.class.cast(stamp);
        synchronized (service) {
            if (restartRequiredOnStarting) {
                // setRunning hasn't been called yet, so just unset the flag so we go to RUNNING when it is called.
                // TODO consider checking that stamp equals the current stamp stored in 'state'
                restartRequiredOnStarting = false;
                restartRequiredFlag = false;
            } else {
                if (state.compareAndSet(State.RESTART_REQUIRED, State.RUNNING, theirStamp, this.stamp.incrementAndGet())) {
                    restartRequiredFlag = false;
                    service.stateChanged(State.RUNNING);
                }
            }
        }
    }

    ControlledProcessStateService getService() {
        return service;
    }

    boolean checkRestartRequired() {
        return restartRequiredFlag;
    }
}
