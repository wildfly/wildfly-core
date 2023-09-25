/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.NotificationDefinition.DataValueDescriptor.NO_DATA;

import java.util.ResourceBundle;

import org.jboss.as.controller.descriptions.DefaultNotificationDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;

/**
 * Defining characteristics of notification in a {@link org.jboss.as.controller.registry.Resource}
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class NotificationDefinition {

    private final String type;
    private final ResourceDescriptionResolver resolver;
    private final DataValueDescriptor dataValueDescriptor;

    private NotificationDefinition(final String type, final ResourceDescriptionResolver resolver, final DataValueDescriptor dataValueDescriptor) {
        this.type = type;
        this.resolver = resolver;
        this.dataValueDescriptor = dataValueDescriptor;
    }

    public String getType() {
        return type;
    }

    public DescriptionProvider getDescriptionProvider() {
        return new DefaultNotificationDescriptionProvider(type, resolver, dataValueDescriptor);
    }

    public static class Builder {
        private final String type;
        private final ResourceDescriptionResolver resolver;
        private DataValueDescriptor dataValueDescriptor = NO_DATA;

        private Builder(String type, ResourceDescriptionResolver resolver) {
            this.type = type;
            this.resolver = resolver;
        }

        public static Builder create(String type, ResourceDescriptionResolver resolver) {
            return new Builder(type, resolver);
        }

        public Builder setDataValueDescriptor(DataValueDescriptor dataValueDescriptor) {
            this.dataValueDescriptor = dataValueDescriptor;
            return this;
        }

        public NotificationDefinition build() {
            return new NotificationDefinition(type, resolver, dataValueDescriptor);
        }
    }

    public interface DataValueDescriptor {
        ModelNode describe(ResourceBundle bundle);

        DataValueDescriptor NO_DATA = new DataValueDescriptor() {
            @Override
            public ModelNode describe(ResourceBundle bundle) {
                return null;
            }
        };
    }
}
