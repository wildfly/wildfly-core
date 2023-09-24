/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collection;

import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;

/**
 * The NotificationHandlerRegistration is used to register and unregister notification handlers.
 *
 * Notification handlers are registered against {@code PathAddress}.
 *
 * The source PathAddress can be a pattern if at least one of its element value is a wildcard ({@link org.jboss.as.controller.PathElement#getValue()} is {@code *}).
 * For example:
 * <ul>
 *     <li>{@code /subsystem=messaging/hornetq-server=default/jms-queue=&#42;} is an address pattern.</li>
 *     <li>{@code /subsystem=messaging/hornetq-server=&#42;/jms-queue=&#42;} is an address pattern.</li>
 *     <li>{@code /subsystem=messaging/hornetq-server=default/jms-queue=myQueue} is <strong>not</strong> an address pattern.</li>
 * </ul>
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public interface NotificationHandlerRegistration extends NotificationHandlerRegistry {

    /**
     * Return all the {@code NotificationHandler} that where registered to listen to the notification's source address (either directly
     * or though pattern addresses) after filtering them out using the {@code NotificationFilter} given at registration time.
     *
     * @param notification the source address of the notification must be a concrete address correspdonding to a resource
     *                     (and not a wildcard address)
     * @return all the filtered {@code NotificationHandler} that registered against the notification source.
     */
    Collection<NotificationHandler> findMatchingNotificationHandlers(Notification notification);

    /**
     * Factory to create a new {@code NotificationHandlerRegistration}
     */
    public static class Factory {
        /**
         * @return a new instance of {@code NotificationHandlerRegistration}
         */
        public static NotificationHandlerRegistration create() {
            return new ConcreteNotificationHandlerRegistration();
        }
    }
}
