/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.core.model.test.host;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of server-config aspects of the host model.
 *
 * @author Brian Stansberry
 */
public class ServerConfigTestCase extends AbstractCoreModelTest {

    @Test
    public void testAddServerConfigBadSocketBindingGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("host.xml");

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "master"), PathElement.pathElement(SERVER_CONFIG, "server-four"));

        final ModelNode operation = Util.createAddOperation(pa);
        operation.get(GROUP).set("main-server-group");
        operation.get(SOCKET_BINDING_GROUP).set("bad-sockets");

        ModelNode response = kernelServices.executeOperation(operation);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testChangeServerGroupInvalidSocketBindingGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("host.xml");

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "master"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        ModelNode op = Util.getWriteAttributeOperation(pa, SOCKET_BINDING_GROUP, "does-not-exist");
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testAddServerConfigBadServerGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("host.xml");

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "master"), PathElement.pathElement(SERVER_CONFIG, "server-four"));

        final ModelNode operation = Util.createAddOperation(pa);
        operation.get(GROUP).set("bad-group");

        ModelNode response = kernelServices.executeOperation(operation);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testChangeServerGroupInvalidServerGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("host.xml");

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "master"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        ModelNode op = Util.getWriteAttributeOperation(pa, GROUP, "does-not-exist");
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }


    private KernelServices createKernelServices(String configFile) throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.HOST)
                .setXmlResource(configFile)
                .setModelInitializer(ServerConfigInitializers.XML_MODEL_INITIALIZER, null)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        return kernelServices;
    }
}
