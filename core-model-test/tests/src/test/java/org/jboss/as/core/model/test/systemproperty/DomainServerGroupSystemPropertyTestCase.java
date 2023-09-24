/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainServerGroupSystemPropertyTestCase extends AbstractSystemPropertyTest {
    static final PathAddress PARENT = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "test"));

    public DomainServerGroupSystemPropertyTestCase() {
        super(false, true);
    }

    protected PathAddress getSystemPropertyAddress(String propName) {
        return PARENT.append(PathElement.pathElement(SYSTEM_PROPERTY, propName));
    }

    protected KernelServicesBuilder createKernelServicesBuilder(boolean xml) {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN);
        if (xml) {
            builder.setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER);
        }
        return builder;
    }

    protected KernelServices createEmptyRoot() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(false).setModelInitializer(BOOT_OP_MODEL_INITIALIZER, null).build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;
    }

    protected ModelNode readSystemPropertiesParentModel(KernelServices kernelServices) {
        ModelNode model = kernelServices.readWholeModel();
        return ModelTestUtils.getSubModel(model, PARENT).get(SYSTEM_PROPERTY);
    }

    @Override
    protected String getXmlResource() {
        return "domain-servergroup-systemproperties.xml";
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return StandardServerGroupInitializers.XML_MODEL_INITIALIZER;
    }

    private ModelInitializer BOOT_OP_MODEL_INITIALIZER = new ModelInitializer() {
        @Override
        public void populateModel(Resource rootResource) {
            Resource host = Resource.Factory.create();
            rootResource.registerChild(PARENT.getElement(0), host);
        }
    };
}
