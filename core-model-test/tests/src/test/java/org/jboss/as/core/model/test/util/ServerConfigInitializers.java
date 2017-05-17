/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.core.model.test.util;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;

/**
 * Model initialization logic for server-config testing.
 *
 * @author Brian Stansberry
 */
public final class ServerConfigInitializers {

    private static final String TEST_MAIN_SERVER_GROUP_CAPABILITY = ServerGroupResourceDefinition.SERVER_GROUP_CAPABILITY.getDynamicName("main-server-group");
    private static final PathAddress TEST_MAIN_SERVER_GROUP_ADDRESS = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    private static final CapabilityScope TEST_SERVER_GROUP_CONTEXT = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, TEST_MAIN_SERVER_GROUP_ADDRESS);

    private static final String TEST_OTHER_SERVER_GROUP_CAPABILITY = ServerGroupResourceDefinition.SERVER_GROUP_CAPABILITY.getDynamicName("other-server-group");
    private static final PathAddress TEST_OTHER_SERVER_GROUP_ADDRESS = PathAddress.pathAddress(SERVER_GROUP, "other-server-group");

    private static final String TEST_FULL_HA_SOCKETS_CAPABILITY = SocketBindingGroupResourceDefinition.SOCKET_BINDING_GROUP_CAPABILITY.getDynamicName("full-ha-sockets");
    private static final PathAddress TEST_FULL_HA_SOCKETS_ADDRESS = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "full-ha-sockets");
    private static final CapabilityScope TEST_SBG_CONTEXT = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, TEST_FULL_HA_SOCKETS_ADDRESS);

    public static final ModelInitializer XML_MODEL_INITIALIZER = new ModelInitializer() {

        @Override
        public void populateModel(ManagementModel managementModel) {
            RuntimeCapabilityRegistry cr = managementModel.getCapabilityRegistry();
            populateCapabilityRegistry(cr);
        }

        public void populateModel(Resource rootResource) {
            // nothing to do
        }
    };

    public static void populateCapabilityRegistry(RuntimeCapabilityRegistry cr) {

        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(TEST_MAIN_SERVER_GROUP_CAPABILITY).build();
        RuntimeCapabilityRegistration reg = new RuntimeCapabilityRegistration(capability, TEST_SERVER_GROUP_CONTEXT,
                new RegistrationPoint(TEST_MAIN_SERVER_GROUP_ADDRESS, null));
        cr.registerCapability(reg);

        capability = RuntimeCapability.Builder.of(TEST_OTHER_SERVER_GROUP_CAPABILITY).build();
        reg = new RuntimeCapabilityRegistration(capability, TEST_SERVER_GROUP_CONTEXT,
                new RegistrationPoint(TEST_OTHER_SERVER_GROUP_ADDRESS, null));
        cr.registerCapability(reg);

        capability = RuntimeCapability.Builder.of(TEST_FULL_HA_SOCKETS_CAPABILITY).build();
        reg = new RuntimeCapabilityRegistration(capability, TEST_SBG_CONTEXT,
                new RegistrationPoint(TEST_FULL_HA_SOCKETS_ADDRESS, null));
        cr.registerCapability(reg);

    }

    private ServerConfigInitializers() {}
}
