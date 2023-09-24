/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * NotificationHandler that stores its notifications in a list and countdown a latch every time it handles a notification.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public class CountdownListBackedNotificationHandler implements NotificationHandler {

    private final CountDownLatch latch;
    final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public CountdownListBackedNotificationHandler(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void handleNotification(Notification notification) {
        notifications.add(notification);
        latch.countDown();
    }

    public List<Notification> getNotifications() {
        return notifications;
    }
}
