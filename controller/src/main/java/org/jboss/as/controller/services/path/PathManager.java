/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import org.jboss.as.controller.services.path.PathManager.Callback.Handle;


/**
 * The client interface for the {@link PathManagerService}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface PathManager {

    /**
     * Resolves a relative path
     *
     * @param path an absolute path if {@code relativeTo} is {@code null}, the relative path to {@code relativeTo} otherwise
     * @param relativeTo the name of the path this is relative to, may be {@code null}
     * @return the resolved path
     * @throws IllegalStateException if there is no path registered under {@code relativeTo}
     */
    String resolveRelativePathEntry(String path, String relativeTo);

    /**
     * Gets a path entry
     *
     * @param name the name of the path
     * @return the path
     * @throws IllegalStateException if there is no path registered under {@code path}
     */
    PathEntry getPathEntry(String name);

    /**
     * Registers a callback for when a path is added/changed/removed
     *
     * @param name the name of the path
     * @param callback the callback instance that will be called when one of the events occur
     * @param events the events we are interested in
     * @return a handle to unregister the callback
     */
    Handle registerCallback(String name, Callback callback, Event...events);

    /**
     * A callback, see {@link PathManager#registerCallback(String, Callback, Event...)}
     */
    interface Callback {

        /**
         * Called when a path is modified in the model. This happens before any changes are made to the path
         * in the path manager. If {@link PathEventContext#reloadRequired()} or {@link PathEventContext#restartRequired()}
         * are called the path will not get updated in the path manager, and {@code pathEvent) does not get
         * called.
         *
         * @param eventContext the event
         * @param the name of the path being modified
         */
        void pathModelEvent(PathEventContext eventContext, String name);

        /**
         * Called once the model has been successfully updated, and the path has been updated in the path manager.
         *
         * @param event the event
         * @param pathEntry the path entry after the event takes place
         */
        void pathEvent(Event event, PathEntry pathEntry);

        /**
         * A handle to a callback
         */
        interface Handle {
            /**
             * Removes the callback
             */
            void remove();
        }
    }

    /**
     * An event triggered when changes are made to a path entry
     */
    enum Event {
        /** A path entry was added */
        ADDED,
        /** A path entry was removed */
        REMOVED,
        /** A path entry was updated */
        UPDATED
    }

    interface PathEventContext {
        /**
         * @see org.jboss.as.controller.OperationContext#isBooting()
         */
        boolean isBooting();

        /**
         * @see org.jboss.as.controller.OperationContext#isNormalServer()
         */
        boolean isNormalServer();

        /**
         * @see org.jboss.as.controller.OperationContext#isResourceServiceRestartAllowed()
         */
        boolean isResourceServiceRestartAllowed();

        /**
         * @see org.jboss.as.controller.OperationContext#reloadRequired()
         */
        void reloadRequired();

        /**
         * @see org.jboss.as.controller.OperationContext#restartRequired()
         */
        void restartRequired();

        /**
         * Get the event triggered
         *
         * @return the event
         */
        Event getEvent();
    }

    /**
     * Factory for a {@link Callback} that always calls {@link PathEventContext#reloadRequired()} from its
     * {@link Callback#pathModelEvent(PathEventContext, String)} method.
     */
    class ReloadServerCallback {
        public static Callback create() {
            return  new Callback() {
                @Override
                public void pathModelEvent(PathEventContext eventContext, String name) {
                    eventContext.reloadRequired();
                }

                @Override
                public void pathEvent(Event event, PathEntry pathEntry) {
                }
            };
        }
    }
}
