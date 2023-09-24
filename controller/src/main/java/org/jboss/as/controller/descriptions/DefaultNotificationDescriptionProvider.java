/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.NotificationDefinition.DataValueDescriptor;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_DATA_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_TYPE;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.dmr.ModelNode;

/**
 * Provides a default description of a notification.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat Inc.
 */
public class DefaultNotificationDescriptionProvider implements DescriptionProvider {

    private final String notificationType;
    private final ResourceDescriptionResolver descriptionResolver;
    private final DataValueDescriptor dataValueDescriptor;

    public DefaultNotificationDescriptionProvider(final String notificationType,
                                                  final ResourceDescriptionResolver descriptionResolver,
                                                  final DataValueDescriptor dataValueDescriptor) {
        this.notificationType = notificationType;
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

        return result;
    }
}
