/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

/**
 * Encapsulates information about the deprecation of a management resource, attribute or operation.
 * <p>
 * <strong>Notifying users about deprecated items:</strong> Some code that uses this class may choose
 * to proactively notify users (e.g. with a log message) when the deprecated item is used. The
 * {@link #isNotificationUseful()} method should be checked before emitting any such notification.
 * Notifying the user should only be done if the user can take some action in response. Advising that
 * something will be removed in a later release is not useful if there is no alternative in the
 * current release. If the {@link #isNotificationUseful()} method returns {@code true} the text
 * description of the deprecated item available from the relevant {@code read-XXX-description}
 * management operation should provide useful information about how the user can avoid using
 * the deprecated item.
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public final class DeprecationData {
    private final ModelVersion since;
    private final boolean notificationUseful;

    /**
     * Creates a new DeprecationData which will return {@code false} from {@link #isNotificationUseful()}.
     * @param since the version since which the attribute has been deprecated. Cannot be {@code null}
     */
    public DeprecationData(ModelVersion since) {
        this(since, false);
    }

    /**
     * Creates a new DeprecationData, with an option to disable notifications to users when
     * the use the deprecated item.
     *
     * @param since the version since which the attribute has been deprecated. Cannot be {@code null}
     * @param notificationUseful whether actively advising the user about the deprecation is useful
     */
    public DeprecationData(ModelVersion since, boolean notificationUseful) {
        assert since != null;
        this.since = since;
        this.notificationUseful = notificationUseful;
    }

    /**
     * Gets the version since which the attribute has been deprecated.
     * @return  the version
     */
    public ModelVersion getSince() {
        return since;
    }

    /**
     * Gets whether actively advising the user about the deprecation is useful. Code that
     * proactively notifies a user (e.g. with a log message) when a deprecated item is
     * used should check this method before producing such a notification.
     *
     * @return {@code true} if advising the user is useful
     */
    public boolean isNotificationUseful()  {
        return notificationUseful;
    }
}
