/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import org.jboss.as.controller.PathAddress;

/**
 * The NotificationHandlerRegistry is used to register and unregister notification handlers.
 *
 * Notification handlers are registered against a {@code PathAddress}.
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
public interface NotificationHandlerRegistry {

    /**
     * Special path address to register a notification handler for <em>any</em> source.
     *
     * A handler registered with this address will receive <em>all</em> notifications emitted by <em>any</em> source.
     * It is advised to use a suitable {@code NotificationFilter} to constrain the received notifications (e.g. by their types).
     */
    PathAddress ANY_ADDRESS = PathAddress.EMPTY_ADDRESS;

    /**
     * Register the given NotificationHandler to receive notifications emitted by the resource at the given source address.
     * The {@link NotificationHandler#handleNotification(Notification)} method will only be called on the registered handler if the filter's {@link NotificationFilter#isNotificationEnabled(Notification)}
     * returns {@code true} for the given notification.
     * <br />
     *
     * @param source the path address of the resource that emit notifications.
     * @param handler the notification handler
     * @param filter the notification filter. Use {@link NotificationFilter#ALL} to let the handler always handle notifications
     */
    void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter);

    /**
     * Unregister the given NotificationHandler to stop receiving notifications emitted by the resource at the given source address.
     *
     * The source, handler and filter must match the values that were used during registration to be effectively unregistered.
     *
     * @param source the path address of the resource that emit notifications.
     * @param handler the notification handler
     * @param filter the notification filter
     */
    void unregisterNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter);

}
