/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.jvm;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostModelJvmModelTestCase extends GlobalJvmModelTestCase {

    static final PathElement PARENT = PathElement.pathElement(HOST, "primary");

    public HostModelJvmModelTestCase() {
        super(TestModelType.HOST);
    }


    @Test
    public void testXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
                .setXmlResource("host-global.xml")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "host-global.xml"), xml);

        ModelNode model = kernelServices.readWholeModel();
        ModelNode jvmParent = model.get(HOST, "primary", JVM);
        Assert.assertEquals(2, jvmParent.keys().size());

        checkEmptyJvm(jvmParent.get("empty"));
        checkFullJvm(jvmParent.get("full"));
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return new ModelInitializer() {
            @Override
            public void populateModel(Resource rootResource) {
                //Register the host resource that will be the parent of the jvm
                rootResource.registerChild(PARENT, Resource.Factory.create());
            }
        };
    }

    @Override
    protected ModelNode getPathAddress(String jvmName, String... subaddress) {
        return PathAddress.pathAddress(PARENT, PathElement.pathElement(JVM, "test")).toModelNode();
    }

}
