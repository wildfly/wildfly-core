/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
