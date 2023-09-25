/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.registry.NotificationHandlerRegistration;

/**
 * The NotificationSupport can be used to emit notifications.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public interface NotificationSupport {

    /**
     * Get the notification registry to register/unregister notification handlers
     */
    NotificationHandlerRegistration getNotificationRegistry();

    /**
     * Emit {@link Notification}(s).
     *
     * @param notifications the notifications to emit
     */
    void emit(final Notification... notifications);

    class Factory {
        private Factory() {
        }

        /**
         * If the {@code executorService} parameter is null, the notifications will be emitted synchronously
         * and may be subject to handlers blocking the execution.
         *
         * @param executorService can be {@code null}.
         */
        public static NotificationSupport create(ExecutorService executorService) {
            NotificationHandlerRegistration registry = NotificationHandlerRegistration.Factory.create();
            if (executorService == null) {
                return new NotificationSupports.BlockingNotificationSupport(registry);
            } else {
                return new NotificationSupports.NonBlockingNotificationSupport(registry, executorService);
            }
        }
    }
}
