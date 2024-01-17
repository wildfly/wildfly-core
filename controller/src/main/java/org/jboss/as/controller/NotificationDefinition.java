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
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;

/**
 * Defining characteristics of notification in a {@link org.jboss.as.controller.registry.Resource}
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class NotificationDefinition implements Feature {

    private final String type;
    private final ResourceDescriptionResolver resolver;
    private final DataValueDescriptor dataValueDescriptor;
    private final Stability stability;

    private NotificationDefinition(Builder builder) {
        this.type = builder.type;
        this.resolver = builder.resolver;
        this.dataValueDescriptor = builder.dataValueDescriptor;
        this.stability = builder.stability;
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    public String getType() {
        return type;
    }

    public DescriptionProvider getDescriptionProvider() {
        return new DefaultNotificationDescriptionProvider(this, resolver, dataValueDescriptor);
    }

    public static class Builder {
        private final String type;
        private final ResourceDescriptionResolver resolver;
        private DataValueDescriptor dataValueDescriptor = NO_DATA;
        private Stability stability = Stability.DEFAULT;

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

        public Builder setStability(Stability stability) {
            this.stability = stability;
            return this;
        }

        public NotificationDefinition build() {
            return new NotificationDefinition(this);
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
