/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.jvm;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainModelJvmModelTestCase extends GlobalJvmModelTestCase {

    static final PathElement PARENT = PathElement.pathElement(SERVER_GROUP, "groupA");

    public DomainModelJvmModelTestCase() {
        super(TestModelType.DOMAIN);
    }

    @Test
    public void testFullJvmXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
                .setXmlResource("domain-full.xml")
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "domain-full.xml"), xml);

        ModelNode model = kernelServices.readWholeModel();
        ModelNode jvmParent = model.get(SERVER_GROUP, "test", JVM);
        Assert.assertEquals(1, jvmParent.keys().size());
        checkFullJvm(jvmParent.get("full"));
    }

    @Test
    public void testEmptyJvmXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
                .setXmlResource("domain-empty.xml")
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "domain-empty.xml"), xml);

        ModelNode model = kernelServices.readWholeModel();
        ModelNode jvmParent = model.get(SERVER_GROUP, "test", JVM);
        Assert.assertEquals(1, jvmParent.keys().size());
        checkEmptyJvm(jvmParent.get("empty"));
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return new ModelInitializer() {
            @Override
            public void populateModel(Resource rootResource) {
                //Register the server group resource that will be the parent of the domain
                rootResource.registerChild(PARENT, Resource.Factory.create());
            }
        };
    }

    @Override
    protected ModelNode getPathAddress(String jvmName, String... subaddress) {
        return PathAddress.pathAddress(PARENT, PathElement.pathElement(JVM, "test")).toModelNode();
    }
}
