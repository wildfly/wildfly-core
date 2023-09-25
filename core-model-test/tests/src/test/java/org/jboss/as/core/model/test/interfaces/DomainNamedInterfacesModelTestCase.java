/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.interfaces;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainNamedInterfacesModelTestCase extends AbstractCoreModelTest {

    @Test
    public void testDomainInterfaces() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
            .setXmlResource("domain.xml")
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "domain.xml"), marshalled);
    }

    @Test
    public void testDomainEmptyInterfaces() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
            .setXmlResource("domain-empty.xml")
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelNode original = kernelServices.readWholeModel();

        kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXml(marshalled)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        ModelTestUtils.compare(original, kernelServices.readWholeModel());
    }
}
