/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.core.model.test.access;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test of access control handling in the host model.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class HostAccessControlTestCase extends AbstractCoreModelTest {

    @Test
    public void testConfiguration() throws Exception {

        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.HOST)
                .setXmlResource("host.xml")
                .validateDescription()
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

//        System.out.println(kernelServices.readWholeModel());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "host.xml"), marshalled);
    }
}

