/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.LegacyKernelServicesInitializer;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.ModelWriteSanitizer;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandardServerGroupInitializers {

    private static final String TEST_PROFILE_CAPABILITY = ProfileResourceDefinition.PROFILE_CAPABILITY.getDynamicName("test");
    private static final PathAddress TEST_PROFILE_ADDRESS = PathAddress.pathAddress(PROFILE, "test");
    private static final CapabilityScope TEST_PROFILE_CONTEXT = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, TEST_PROFILE_ADDRESS);

    private static final String TEST_SBG_CAPABILITY = SocketBindingGroupResourceDefinition.SOCKET_BINDING_GROUP_CAPABILITY.getDynamicName("test-sockets");
    private static final PathAddress TEST_SBG_ADDRESS = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "test-sockets");
    private static final CapabilityScope TEST_SBG_CONTEXT = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, TEST_SBG_ADDRESS);

    public static final ModelInitializer XML_MODEL_INITIALIZER = new ModelInitializer() {

        @Override
        public void populateModel(ManagementModel managementModel) {
            populateModel(managementModel.getRootResource());
            RuntimeCapabilityRegistry cr = managementModel.getCapabilityRegistry();

            RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(TEST_PROFILE_CAPABILITY).build();
            RuntimeCapabilityRegistration reg = new RuntimeCapabilityRegistration(capability, TEST_PROFILE_CONTEXT,
                    new RegistrationPoint(TEST_PROFILE_ADDRESS, null));
            cr.registerCapability(reg);

            capability = RuntimeCapability.Builder.of(TEST_SBG_CAPABILITY).build();
            reg = new RuntimeCapabilityRegistration(capability, TEST_SBG_CONTEXT,
                    new RegistrationPoint(TEST_SBG_ADDRESS, null));
            cr.registerCapability(reg);
        }

        public void populateModel(Resource rootResource) {
            rootResource.registerChild(TEST_PROFILE_ADDRESS.getElement(0), Resource.Factory.create());
            rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "test-sockets"), Resource.Factory.create());
        }
    };

    public static final ModelWriteSanitizer XML_MODEL_WRITE_SANITIZER = new ModelWriteSanitizer() {
        @Override
        public ModelNode sanitize(ModelNode model) {
            //Remove the profile and socket-binding-group removed by the initializer so the xml does not include a profile
            model.remove(PROFILE);
            model.remove(SOCKET_BINDING_GROUP);
            return model;
        }
    };

    public static LegacyKernelServicesInitializer addServerGroupInitializers(LegacyKernelServicesInitializer legacyKernelServicesInitializer) {
        legacyKernelServicesInitializer.initializerCreateModelResource(PathAddress.EMPTY_ADDRESS, TEST_PROFILE_ADDRESS.getElement(0), null, TEST_PROFILE_CAPABILITY)
            .initializerCreateModelResource(PathAddress.EMPTY_ADDRESS, PathElement.pathElement(SOCKET_BINDING_GROUP, "test-sockets"), null, TEST_SBG_CAPABILITY);
        return legacyKernelServicesInitializer;
    }

    public static class Fixer extends AbstractCoreModelTest.RbacModelFixer {

        public Fixer(ModelVersion transformFromVersion) {
            super(transformFromVersion);
        }

        @Override
        public ModelNode fixModel(ModelNode modelNode) {
            modelNode = super.fixModel(modelNode);
            modelNode.remove(SOCKET_BINDING_GROUP);
            modelNode.remove(PROFILE);
            return modelNode;
        }
    }

}
