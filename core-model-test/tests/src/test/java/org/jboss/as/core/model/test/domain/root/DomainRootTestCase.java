/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.domain.root;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.server.controller.descriptions.ServerDescriptionConstants;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainRootTestCase extends AbstractCoreModelTest {

    @Test
    public void testEmptyRoot() throws Exception {
        KernelServices kernelServices = createEmptyRoot();

        ModelNode model = kernelServices.readWholeModel(false, true);
        assertAttribute(model, ModelDescriptionConstants.NAMESPACES, new ModelNode().setEmptyList());
        assertAttribute(model, ModelDescriptionConstants.SCHEMA_LOCATIONS, new ModelNode().setEmptyList());
        assertAttribute(model, ModelDescriptionConstants.NAME, new ModelNode("Unnamed Domain"));
        assertAttribute(model, ModelDescriptionConstants.PRODUCT_NAME, null);
        assertAttribute(model, ModelDescriptionConstants.PRODUCT_VERSION, null);
        assertAttribute(model, ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION, new ModelNode(Version.MANAGEMENT_MAJOR_VERSION));
        assertAttribute(model, ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION, new ModelNode(Version.MANAGEMENT_MINOR_VERSION));
        assertAttribute(model, ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION, new ModelNode(Version.MANAGEMENT_MICRO_VERSION));
        assertAttribute(model, ServerDescriptionConstants.PROCESS_TYPE, new ModelNode("Domain Controller"));
        assertAttribute(model, ServerDescriptionConstants.LAUNCH_TYPE, new ModelNode("DOMAIN"));

        //These two cannot work in tests - placeholder
        assertAttribute(model, ModelDescriptionConstants.RELEASE_VERSION, new ModelNode("Unknown"));
        assertAttribute(model, ModelDescriptionConstants.RELEASE_CODENAME, new ModelNode(""));

        //Try changing namespaces, schema-locations and name
    }

    @Test
    public void testWriteName() throws Exception {
        KernelServices kernelServices = createEmptyRoot();

        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        op.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        op.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.NAME);
        op.get(ModelDescriptionConstants.VALUE).set("");
        kernelServices.executeForFailure(op);

        op.get(ModelDescriptionConstants.VALUE).set("Testing");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(op));

        ModelNode model = kernelServices.readWholeModel(false, true);
        assertAttribute(model, ModelDescriptionConstants.NAME, new ModelNode("Testing"));

        op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        op.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        op.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.NAME);
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(op));

        model = kernelServices.readWholeModel(false, true);
        assertAttribute(model, ModelDescriptionConstants.NAME, new ModelNode("Unnamed Domain"));
    }

    private void assertAttribute(ModelNode model, String name, ModelNode expected) {
        if (expected == null) {
            Assert.assertFalse(model.get(name).isDefined());
        } else {
            ModelNode actual = model.get(name);
            Assert.assertEquals(expected, actual);
        }
    }

    private KernelServices createEmptyRoot() throws Exception{
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;
    }
}
