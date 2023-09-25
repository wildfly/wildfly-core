/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.interfaces;

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
public abstract class AbstractSpecifiedInterfacesTest extends AbstractCoreModelTest {

    private final TestModelType type;

    public AbstractSpecifiedInterfacesTest(TestModelType type) {
        this.type = type;
    }

    @Test
    public void testInterfaces() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
            .setXmlResource(getXmlResource())
            .setModelInitializer(getModelInitializer(), null)
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), getXmlResource()), marshalled);

        kernelServices = createKernelServicesBuilder()
                .setXml(marshalled)
                .setModelInitializer(getModelInitializer(), null)
                .build();
            Assert.assertTrue(kernelServices.isSuccessfulBoot());
    }

    KernelServicesBuilder createKernelServicesBuilder() {
        return createKernelServicesBuilder(type);
    }

    protected abstract String getXmlResource();

    protected ModelInitializer getModelInitializer() {
        return ModelInitializer.NO_OP;
    }
}
