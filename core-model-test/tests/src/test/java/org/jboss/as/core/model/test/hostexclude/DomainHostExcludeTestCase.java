/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.core.model.test.hostexclude;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_EXCLUDE;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;


/**
 * Tests of handling of the host-exclude resources.
 *
 * @author Brian Stansberry
 */
public class DomainHostExcludeTestCase extends AbstractCoreModelTest {


    protected KernelServicesBuilder createKernelServicesBuilder(boolean xml) {
        return createKernelServicesBuilder(TestModelType.DOMAIN);
    }

    protected KernelServices createEmptyRoot() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(false).build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;
    }

    protected ModelNode readHostExcludesParentModel(KernelServices kernelServices) {
        ModelNode model = kernelServices.readWholeModel();
        return model.get(HOST_EXCLUDE);
    }


    protected String getXmlResource() {
        return "domain-host-excludes.xml";
    }

    @Test
    public void testXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(true)
                .setXmlResource(getXmlResource())
                .setModelInitializer(ModelInitializer.NO_OP, null)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String xmlOriginal = ModelTestUtils.readResource(this.getClass(), getXmlResource());
        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(xmlOriginal, marshalled);

        ModelNode props = readHostExcludesParentModel(kernelServices);
        Assert.assertEquals(4, props.keys().size());
    }
}
