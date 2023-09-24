/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.socketbindinggroups;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainSocketBindingGroupTestCase extends AbstractSocketBindingGroupTest {

    public DomainSocketBindingGroupTestCase() {
        super(TestModelType.DOMAIN);
    }

    @Override
    protected String getXmlResource() {
        return "domain.xml";
    }


    @Test
    public void testBadSocketBindingGroupIncludesAdd() throws Exception {
        KernelServices kernelServices = createKernelServices();

        PathAddress addr = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "bad-add");
        ModelNode op = Util.createAddOperation(addr);
        op.get(DEFAULT_INTERFACE).set("public");
        op.get(INCLUDES).add("test").add("NOT_THERE");
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testBadSocketBindingGroupIncludesRemove() throws Exception {
        KernelServices kernelServices = createKernelServices();

        PathAddress addr = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "test");
        ModelNode op = Util.createRemoveOperation(addr);

        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0368"));
    }

    @Test
    public void testBadSocketBindingGroupIncludesWrite() throws Exception {
        KernelServices kernelServices = createKernelServices();

        PathAddress addr = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "test-with-includes");
        ModelNode list = new ModelNode().add("test").add("standard-sockets").add("bad-SocketBindingGroup");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);

        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }
}
