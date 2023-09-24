/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_DATA_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class ResourceWithNotificationDefinitionTestCase extends AbstractControllerTestBase {

    private static final String MY_TYPE = "my-notification-type";
    private static final String NOTIFICATION_DESCRIPTION = "My Notification Description";
    private static final ModelNode DATA_TYPE_DESCRIPTION;

    static {
        DATA_TYPE_DESCRIPTION = new ModelNode();
        DATA_TYPE_DESCRIPTION.get("foo", DESCRIPTION).set("description of foo");
        DATA_TYPE_DESCRIPTION.get("bar", DESCRIPTION).set("description of bar");

    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, ProcessType.STANDALONE_SERVER);
        NotificationDefinition notificationDefinition = NotificationDefinition.Builder.create(MY_TYPE,
                new NonResolvingResourceDescriptionResolver() {
                    @Override
                    public String getNotificationDescription(String notificationType, Locale locale, ResourceBundle bundle) {
                        return NOTIFICATION_DESCRIPTION;
                    }
                })
                .setDataValueDescriptor(new NotificationDefinition.DataValueDescriptor() {
                    @Override
                    public ModelNode describe(ResourceBundle bundle) {
                        return DATA_TYPE_DESCRIPTION;
                    }
                })
                .build();

        registration.registerNotification(notificationDefinition);
    }

    @Test
    public void testReadResourceDescriptionWithNotification() throws Exception {
        ModelNode readResourceDescription = createOperation(READ_RESOURCE_DESCRIPTION_OPERATION);
        readResourceDescription.get(NOTIFICATIONS).set(true);

        ModelNode description = executeForResult(readResourceDescription);
        assertTrue(description.hasDefined(NOTIFICATIONS));

        List<Property> notifications = description.require(NOTIFICATIONS).asPropertyList();
        assertEquals(1, notifications.size());
        Property notification = notifications.get(0);
        assertEquals(MY_TYPE, notification.getName());
        assertEquals(MY_TYPE, notification.getValue().get(NOTIFICATION_TYPE).asString());
        assertEquals(NOTIFICATION_DESCRIPTION, notification.getValue().get(DESCRIPTION).asString());
        assertEquals(DATA_TYPE_DESCRIPTION, notification.getValue().get(NOTIFICATION_DATA_TYPE));
    }
}
