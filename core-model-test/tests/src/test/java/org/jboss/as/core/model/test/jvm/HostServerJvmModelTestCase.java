/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.jvm;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostServerJvmModelTestCase extends AbstractJvmModelTest {

    static final PathElement HOST_ELEMENT = PathElement.pathElement(HOST, "primary");
    static final PathElement SERVER_ONE_ELEMENT = PathElement.pathElement(SERVER_CONFIG, "server-one");
    static final PathElement SERVER_TWO_ELEMENT = PathElement.pathElement(SERVER_CONFIG, "server-two");
    public HostServerJvmModelTestCase() {
        super(TestModelType.HOST, true);
    }

    @Test
    public void testWriteDebugEnabled() throws Exception {
        KernelServices kernelServices = doEmptyJvmAdd();
        ModelNode value = ModelNode.TRUE;
        Assert.assertEquals(value, writeTest(kernelServices, "debug-enabled", value));
    }

    @Test
    public void testWriteDebugOptions() throws Exception {
        KernelServices kernelServices = doEmptyJvmAdd();
        ModelNode value = new ModelNode("abc");
        Assert.assertEquals(value, writeTest(kernelServices, "debug-options", value));
    }

    @Test
    public void testFullServerJvmXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
            .setXmlResource("host-server-full.xml")
            .setModelInitializer(ServerConfigInitializers.XML_MODEL_INITIALIZER, null)
            .build();

        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "host-server-full.xml"), xml);

        //Inspect the actual model
        ModelNode full = kernelServices.readWholeModel().get(HOST, "primary", SERVER_CONFIG, "server-one", JVM, "full");
        checkFullJvm(full);
    }

    @Test
    public void testEmptyServerJvmXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
            .setXmlResource("host-server-empty.xml")
            .setModelInitializer(ServerConfigInitializers.XML_MODEL_INITIALIZER, null)
            .build();

        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "host-server-empty.xml"), xml);

        //Inspect the actual model
        ModelNode empty = kernelServices.readWholeModel().get(HOST, "primary", SERVER_CONFIG, "server-one", JVM, "empty");
        checkEmptyJvm(empty);
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return bootOpModelInitializer;
    }

    @Override
    protected ModelNode getPathAddress(String jvmName, String... subaddress) {
        return PathAddress.pathAddress(HOST_ELEMENT, SERVER_ONE_ELEMENT, PathElement.pathElement(JVM, "test")).toModelNode();
    }

    private ModelInitializer bootOpModelInitializer = new ModelInitializer() {
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
