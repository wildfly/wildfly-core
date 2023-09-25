/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.auditlog;

import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractAuditLogTestCase extends AbstractCoreModelTest {

    private final TestModelType type;

    public AbstractAuditLogTestCase(TestModelType type) {
        this.type = type;
    }

    @Test
    public void testAuditLog() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
            .setXmlResource(getXmlResource())
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), getXmlResource()), marshalled);

        kernelServices = createKernelServicesBuilder()
                .setXml(marshalled)
                .build();
            Assert.assertTrue(kernelServices.isSuccessfulBoot());
        kernelServices.shutdown();
    }

    KernelServicesBuilder createKernelServicesBuilder() {
        return createKernelServicesBuilder(type);
    }

    protected abstract String getXmlResource();

    protected ModelInitializer createEmptyModelInitalizer() {
        return new ModelInitializer() {
            @Override
            public void populateModel(Resource rootResource) {
                //Default is no-op
            }
        };
    }
}
