/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostServerSystemPropertyTestCase extends AbstractSystemPropertyTest {
    static final PathElement HOST_ELEMENT = PathElement.pathElement(HOST, "primary");
    static final PathElement SERVER_ONE_ELEMENT = PathElement.pathElement(SERVER_CONFIG, "server-one");
    static final PathElement SERVER_TWO_ELEMENT = PathElement.pathElement(SERVER_CONFIG, "server-two");
    static final PathAddress SERVER_ONE_ADDRESS = PathAddress.pathAddress(HOST_ELEMENT, SERVER_ONE_ELEMENT);

    public HostServerSystemPropertyTestCase() {
        super(false, false);
    }


    protected PathAddress getSystemPropertyAddress(String propName) {
        return SERVER_ONE_ADDRESS.append(PathElement.pathElement(SYSTEM_PROPERTY, propName));
    }

    protected KernelServicesBuilder createKernelServicesBuilder(boolean xml) {
        return createKernelServicesBuilder(TestModelType.HOST);
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return ServerConfigInitializers.XML_MODEL_INITIALIZER;
    }

    protected KernelServices createEmptyRoot() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(false).setModelInitializer(BOOT_OP_MODEL_INITIALIZER, null).build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;
    }

    protected ModelNode readSystemPropertiesParentModel(KernelServices kernelServices) {
        ModelNode model = kernelServices.readWholeModel();
        return ModelTestUtils.getSubModel(model, SERVER_ONE_ADDRESS).get(SYSTEM_PROPERTY);
    }

    @Override
    protected String getXmlResource() {
        return "host-server-systemproperties.xml";
    }

    private ModelInitializer BOOT_OP_MODEL_INITIALIZER = new ModelInitializer() {
        @Override
        public void populateModel(ManagementModel managementModel) {
            populateModel(managementModel.getRootResource());
            ServerConfigInitializers.populateCapabilityRegistry(managementModel.getCapabilityRegistry());
        }

        @Override
        public void populateModel(Resource rootResource) {
            Resource host = Resource.Factory.create();
            rootResource.registerChild(HOST_ELEMENT, host);
            ModelNode serverConfig = new ModelNode();
            serverConfig.get(GROUP).set("test");
            Resource server1 = Resource.Factory.create();
            server1.writeModel(serverConfig);
            Resource server2 = Resource.Factory.create();
            server2.writeModel(serverConfig);
            host.registerChild(SERVER_ONE_ELEMENT, server1);
            host.registerChild(SERVER_TWO_ELEMENT, server2);
        }
    };
}
