/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.domain.management.notification.registrar;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_REGISTRAR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;

/**
 * @author Kabir Khan
 */
public class NotificationRegistrarsRootResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH = PathElement.pathElement(SERVICE, NOTIFICATION_REGISTRAR);
    static final String RESOLVER_KEY = "core.management.notification-registrars";

    public static final NotificationRegistrarsRootResourceDefinition INSTANCE = new NotificationRegistrarsRootResourceDefinition();

    private NotificationRegistrarsRootResourceDefinition() {
        super(PATH, DomainManagementResolver.getResolver(RESOLVER_KEY), new ModelOnlyAddStepHandler(), ModelOnlyRemoveStepHandler.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(NotificationRegistrarResourceDefinition.INSTANCE);
    }
}
