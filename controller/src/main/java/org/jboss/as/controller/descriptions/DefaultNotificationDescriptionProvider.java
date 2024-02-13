/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.NotificationDefinition.DataValueDescriptor;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_DATA_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;

/**
 * Provides a default description of a notification.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat Inc.
 */
public class DefaultNotificationDescriptionProvider implements DescriptionProvider {

    private final String notificationType;
    private final Stability stability;
    private final ResourceDescriptionResolver descriptionResolver;
    private final DataValueDescriptor dataValueDescriptor;

    public DefaultNotificationDescriptionProvider(final NotificationDefinition definition,
                                                  final ResourceDescriptionResolver descriptionResolver,
                                                  final DataValueDescriptor dataValueDescriptor) {
        this.notificationType = definition.getType();
        this.stability = definition.getStability();
        this.descriptionResolver = descriptionResolver;
        this.dataValueDescriptor = dataValueDescriptor;
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        ModelNode result = new ModelNode();

        final ResourceBundle bundle = descriptionResolver.getResourceBundle(locale);
        result.get(NOTIFICATION_TYPE).set(notificationType);
        result.get(DESCRIPTION).set(descriptionResolver.getNotificationDescription(notificationType, locale, bundle));
        if (dataValueDescriptor != null) {
            ModelNode dataDescription = dataValueDescriptor.describe(bundle);
            if (dataDescription != null && dataDescription.isDefined()) {
                result.get(NOTIFICATION_DATA_TYPE).set(dataDescription);
            }
        }
        result.get(STABILITY).set(this.stability.toString());
        return result;
    }
}
