/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneSystemPropertyTestCase extends AbstractSystemPropertyTest {

    static final String PROP_ONE = "sys.prop.test.one";
    static final String PROP_TWO = "sys.prop.test.two";

    static final PathAddress PARENT = PathAddress.EMPTY_ADDRESS;

    public StandaloneSystemPropertyTestCase() {
        super(true, false);
    }

    @Before
    public void clearProperties() {
        System.clearProperty(PROP_ONE);
        System.clearProperty(PROP_TWO);
    }


    protected PathAddress getSystemPropertyAddress(String propName) {
        return PathAddress.pathAddress(PathElement.pathElement(SYSTEM_PROPERTY, propName));
    }


    protected KernelServicesBuilder createKernelServicesBuilder(boolean xml) {
        return createKernelServicesBuilder(TestModelType.STANDALONE);
    }

    protected KernelServices createEmptyRoot() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(false).build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;
    }

    protected ModelNode readSystemPropertiesParentModel(KernelServices kernelServices) {
        ModelNode model = kernelServices.readWholeModel().get(SYSTEM_PROPERTY);
        return model;
    }

    @Override
    protected String getXmlResource() {
        return "standalone-systemproperties.xml";
    }
}
