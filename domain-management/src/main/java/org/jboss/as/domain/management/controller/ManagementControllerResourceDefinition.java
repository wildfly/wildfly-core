/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_COMPLETE_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_BEGUN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_COMPLETE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;

/**
 * {@code ResourceDefinition} for the management of operation execution.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ManagementControllerResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS);

    private static final ResourceDescriptionResolver RESOLVER = DomainManagementResolver.getResolver(CORE, MANAGEMENT_OPERATIONS);

    private static final NotificationDefinition NOTIFICATION_BEGIN_RUNTIME_MODIFICATION = NotificationDefinition.Builder.create(RUNTIME_MODIFICATION_BEGUN, RESOLVER).build();
    private static final NotificationDefinition NOTIFICATION_COMPLETE_RUNTIME_MODIFICATION = NotificationDefinition.Builder.create(RUNTIME_MODIFICATION_COMPLETE, RESOLVER).build();
    private static final NotificationDefinition NOTIFICATION_BOOT_COMPLETE = NotificationDefinition.Builder.create(BOOT_COMPLETE_NOTIFICATION, RESOLVER).build();

    public static final ResourceDefinition INSTANCE = new ManagementControllerResourceDefinition();

    private ManagementControllerResourceDefinition() {
        super(new Parameters(PATH_ELEMENT, RESOLVER)
                        .setRuntime()
        );
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(FindNonProgressingOperationHandler.DEFINITION, FindNonProgressingOperationHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(CancelNonProgressingOperationHandler.DEFINITION, CancelNonProgressingOperationHandler.INSTANCE);
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        super.registerNotifications(resourceRegistration);
        resourceRegistration.registerNotification(NOTIFICATION_BEGIN_RUNTIME_MODIFICATION);
        resourceRegistration.registerNotification(NOTIFICATION_COMPLETE_RUNTIME_MODIFICATION);
        resourceRegistration.registerNotification(NOTIFICATION_BOOT_COMPLETE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(ActiveOperationResourceDefinition.INSTANCE);
    }
}
