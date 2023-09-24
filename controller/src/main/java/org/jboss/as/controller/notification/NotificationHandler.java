/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

/**
 * A notification handler is used to be notified of events on the server.
 * Its {@code handleNotification} is called every time a notification is emitted by a resource it was registered for.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public interface NotificationHandler {
    void handleNotification(Notification notification);
}
