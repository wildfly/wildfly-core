/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.deployment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.fail;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.OperationFailedException;
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
public class StandaloneDeploymentUnicityTestCase extends AbstractCoreModelTest {

    @Test
    public void testDeployments() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.STANDALONE)
            .setXmlResource("standalone.xml")
            .createContentRepositoryContent("12345678901234567890")
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "standalone.xml"), marshalled);
    }

    @Test
    public void testIncorrectDeployments() throws Exception {
        try {
            createKernelServicesBuilder(TestModelType.STANDALONE)
                    .setXmlResource("standalone_duplicate.xml")
                    .createContentRepositoryContent("12345678901234567890")
                    .build();
            fail("Expected boot failed");
        } catch (OperationFailedException ex) {
            final String failureDescription = ex.getFailureDescription().asString();
            MatcherAssert.assertThat(failureDescription, allOf(containsString("WFLYSRV0205:"), containsString("abc.war")));
        }
    }
}
