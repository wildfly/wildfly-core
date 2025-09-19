/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.mgmt_interfaces;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.version.Stability;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test case to test the handling of management interface definitions.
 *
 *  @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class HostMgmtInterfacesTestCase extends AbstractCoreModelTest {

    @Test
    public void testConfiguration() throws Exception {
        testConfiguration("host.xml");
    }

    @Test
    public void testEmptyAllowedOriginsConfiguration() throws Exception {
        // Test for https://issues.jboss.org/browse/WFCORE-4656
        testConfiguration("host_empty_allowed_origins.xml");
    }

    @Test
    public void testResourceConstraints() throws Exception {
        // Test for https://issues.redhat.com/browse/WFCORE-7317
        testConfiguration("host_resource_constraints.xml");
    }

    private void testConfiguration(String fileName) throws Exception {
        testConfiguration(fileName, Stability.DEFAULT);
    }

    private void testConfiguration(String fileName, Stability stability) throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.HOST, stability)
                .setXmlResource(fileName)
                .validateDescription()
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), fileName), marshalled);
    }

}
