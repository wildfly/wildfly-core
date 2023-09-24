/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * A notification handler that stores its notifications in a list.
 *
* @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
*/
class ListBackedNotificationHandler implements NotificationHandler {

    final List<Notification> notifications = new ArrayList<>();

    @Override
    public void handleNotification(Notification notification) {
        notifications.add(notification);
    }

    List<Notification> getNotifications() {
        return notifications;
    }
}
