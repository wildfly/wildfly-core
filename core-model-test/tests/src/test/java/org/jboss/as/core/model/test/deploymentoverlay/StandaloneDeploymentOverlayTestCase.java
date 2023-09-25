/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.deploymentoverlay;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneDeploymentOverlayTestCase extends AbstractCoreModelTest {

    @Test
    public void testDomainOverlays() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.STANDALONE)
            .setXmlResource("standalone.xml")
            .createContentRepositoryContent("12345678901234567890")
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "standalone.xml"), marshalled);
    }
}
