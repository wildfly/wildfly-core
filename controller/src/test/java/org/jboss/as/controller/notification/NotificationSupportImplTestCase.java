/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.notification.NotificationFilter.ALL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.as.controller.registry.NotificationHandlerRegistration;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public class NotificationSupportImplTestCase {

    @Before
    public void setUp() {
    }

    @Test
    public void testNotificationOrderingWithExecutor() throws Exception {
        doNotificationOrdering(Executors.newFixedThreadPool(12));
    }

    @Test
    public void testNotificationOrderingWithoutExecutor() throws Exception {
        doNotificationOrdering(null);
    }

    private void  doNotificationOrdering(ExecutorService executor) throws Exception {
        int numberOfNotificationsEmitted = 12;
        final CountDownLatch latch = new CountDownLatch(numberOfNotificationsEmitted);

        NotificationSupport notificationSupport = NotificationSupport.Factory.create(executor);

        CountdownListBackedNotificationHandler handler = new CountdownListBackedNotificationHandler(latch);

        notificationSupport.getNotificationRegistry().registerNotificationHandler(NotificationHandlerRegistration.ANY_ADDRESS, handler, ALL);

        List<Notification> notifications1 = new ArrayList<Notification>();
        notifications1.add(new Notification("foo", pathAddress("resource", "foo"), "foo"));
        notifications1.add(new Notification("foo", pathAddress("resource", "foo"), "bar"));
        notifications1.add(new Notification("foo", pathAddress("resource", "foo"), "baz"));

        List<Notification> notifications2 = new ArrayList<Notification>();
        notifications2.add(new Notification("bar", pathAddress("resource", "bar"), "foo"));
        notifications2.add(new Notification("bar", pathAddress("resource", "bar"), "bar"));
        notifications2.add(new Notification("bar", pathAddress("resource", "bar"), "baz"));

        final Notification[] notifs1 = notifications1.toArray(new Notification[notifications1.size()]);
        final Notification[] notifs2 = notifications2.toArray(new Notification[notifications2.size()]);

        // emit the notifications1 a 1st time
        notificationSupport.emit(notifs1);
        // emit the notifications2 a 1st time
        notificationSupport.emit(notifs2);
        // emit the notifications1 a 2nd time
        notificationSupport.emit(notifs1);
        // emit the notifications2 a 2nd time
        notificationSupport.emit(notifs2);

        assertTrue(latch.await(5, SECONDS));

        assertEquals(handler.getNotifications().toString(), numberOfNotificationsEmitted, handler.getNotifications().size());

        for (Notification notification : handler.getNotifications()) {
            assertNotNull(notification);
        }

        // handled the 1st notifications1 that were emitted
        assertEquals(notifications1, handler.getNotifications().subList(0, 3));
        // handled the 1st notifications2 that were emitted
        assertEquals(notifications2, handler.getNotifications().subList(3, 6));
        // handled the 2nd notifications1 that were emitted
        assertEquals(notifications1, handler.getNotifications().subList(6, 9));
        // handled the 2nd notifications2 that were emitted
        assertEquals(notifications2, handler.getNotifications().subList(9, 12));
    }
}
