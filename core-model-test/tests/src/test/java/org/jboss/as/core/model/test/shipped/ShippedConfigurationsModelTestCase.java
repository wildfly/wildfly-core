/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.core.model.test.shipped;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.ModelWriteSanitizer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ShippedConfigurationsModelTestCase extends AbstractCoreModelTest {

    @Test
    public void testDomainXml() throws Exception {
        testConfiguration(TestModelType.DOMAIN, "domain.xml",
                new ModelInitializer() {
                    @Override
                    public void populateModel(ManagementModel managementModel) {
                        populateModel(managementModel.getRootResource());

                        RuntimeCapabilityRegistry cr = managementModel.getCapabilityRegistry();

                        RuntimeCapability<Void> capability =
                                RuntimeCapability.Builder.of(ProfileResourceDefinition.PROFILE_CAPABILITY.getDynamicName("full")).build();
                        RuntimeCapabilityRegistration reg = new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                                new RegistrationPoint(PathAddress.pathAddress(PROFILE, "full"), null));
                        cr.registerCapability(reg);

                        capability = RuntimeCapability.Builder.of(ProfileResourceDefinition.PROFILE_CAPABILITY.getDynamicName("full-ha")).build();
                        reg = new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                                new RegistrationPoint(PathAddress.pathAddress(PROFILE, "full-ha"), null));
                        cr.registerCapability(reg);
                    }

                    public void populateModel(Resource rootResource) {
                        rootResource.registerChild(PathElement.pathElement(PROFILE, "full"), Resource.Factory.create());
                        rootResource.registerChild(PathElement.pathElement(PROFILE, "full-ha"), Resource.Factory.create());
                    }
                },
                new ModelWriteSanitizer() {
                    public ModelNode sanitize(ModelNode model) {
                        model.remove(PROFILE);
                        return model;
                    }
                });
    }

    @Test
    public void testHostXml() throws Exception {
        testConfiguration(TestModelType.HOST, "host.xml", ServerConfigInitializers.XML_MODEL_INITIALIZER, null);
    }

    @Test
    public void testStandaloneXml() throws Exception {
        testConfiguration(TestModelType.STANDALONE, "standalone.xml", null, null);
    }

    private void testConfiguration(TestModelType type, String xmlResource, ModelInitializer initializer, ModelWriteSanitizer sanitizer) throws Exception {

        KernelServices kernelServices = createKernelServicesBuilder(type)
                .setXmlResource(xmlResource)
                .validateDescription()
                .setModelInitializer(initializer, sanitizer)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), xmlResource), marshalled);
    }

}
