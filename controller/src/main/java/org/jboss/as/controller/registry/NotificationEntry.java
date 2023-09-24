/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import org.jboss.as.controller.descriptions.DescriptionProvider;

/**
 * Information about a registered notification.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class NotificationEntry {

    private final DescriptionProvider descriptionProvider;
    private final boolean inherited;

    public NotificationEntry(final DescriptionProvider descriptionProvider, final boolean inherited) {
        this.descriptionProvider = descriptionProvider;
        this.inherited = inherited;
    }

    public DescriptionProvider getDescriptionProvider() {
        return descriptionProvider;
    }

    public boolean isInherited() {
        return inherited;
    }

}
