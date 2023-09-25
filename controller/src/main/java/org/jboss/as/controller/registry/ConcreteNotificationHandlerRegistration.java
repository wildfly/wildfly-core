/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collection;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;

/**
 * A concrete implementation of {@code NotificationHandlerRegistration}.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
*/
class ConcreteNotificationHandlerRegistration implements NotificationHandlerRegistration {

    /**
     * The root registry.
     */
    NotificationHandlerNodeRegistry rootRegistry = new NotificationHandlerNodeRegistry(null, null);

    /**
     * All the {@link org.jboss.as.controller.registry.ConcreteNotificationHandlerRegistration.NotificationHandlerEntry} registered against {@link org.jboss.as.controller.registry.NotificationHandlerRegistration#ANY_ADDRESS}
     * that are added to {#findMatchingNotificationHandlers} (after filtering them out).
     */
    Set<NotificationHandlerEntry> anyAddressEntries = new CopyOnWriteArraySet<NotificationHandlerEntry>();

    @Override
    public void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {
        NotificationHandlerEntry entry = new NotificationHandlerEntry(handler, filter);
        if (source == ANY_ADDRESS) {
            anyAddressEntries.add(entry);
            return;
        }

        ListIterator<PathElement> iterator = source.iterator();
        rootRegistry.registerEntry(iterator, entry);
    }

    @Override
    public void unregisterNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {
        NotificationHandlerEntry entry = new NotificationHandlerEntry(handler, filter);
        if (source == ANY_ADDRESS) {
            anyAddressEntries.remove(entry);
            return;
        }

        ListIterator<PathElement> iterator = source.iterator();
        rootRegistry.unregisterEntry(iterator, entry);
    }

    @Override
    public Collection<NotificationHandler> findMatchingNotificationHandlers(Notification notification) {
        Collection<NotificationHandler> handlers = new HashSet<>();
        // collect all the handlers that match the notifications for the registry tree...
        ListIterator<PathElement> iterator = notification.getSource().iterator();
        rootRegistry.findEntries(iterator, handlers, notification);

        // ... and also the filtered handlers registered against ANY_ADRESS
        for (NotificationHandlerEntry anyAddressEntry : anyAddressEntries) {
            if (anyAddressEntry.getFilter().isNotificationEnabled(notification)) {
                handlers.add(anyAddressEntry.getHandler());
            }
        }
        return handlers;
    }

    /**
     * A class to represent a single entry for both a notification handler and filter.
     */
    static class NotificationHandlerEntry {
        private final NotificationHandler handler;
        private final NotificationFilter filter;

        NotificationHandlerEntry(NotificationHandler handler, NotificationFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }

        NotificationHandler getHandler() {
            return handler;
        }

        NotificationFilter getFilter() {
            return filter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NotificationHandlerEntry that = (NotificationHandlerEntry) o;

            if (!filter.equals(that.filter)) return false;
            if (!handler.equals(that.handler)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = handler.hashCode();
            result = 31 * result + filter.hashCode();
            return result;
        }
    }
}
