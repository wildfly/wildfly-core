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
package org.jboss.as.core.model.test.profile;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProfileTestCase extends AbstractCoreModelTest {

    @Test
    public void testProfileXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain.xml")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "domain.xml"), marshalled);

        kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXml(marshalled)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        ProfileUtils.executeDescribeProfile(kernelServices, "testA");

    }

    @Test
    public void testBadProfileIncludesRemove() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain.xml")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        PathAddress addr = PathAddress.pathAddress(PROFILE, "testA");
        ModelNode op = Util.createRemoveOperation(addr);
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0368"));

        ProfileUtils.executeDescribeProfile(kernelServices, "testA");

    }

    @Test
    public void testBadProfileIncludesWrite() throws Exception {

        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain.xml")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        PathAddress addr = PathAddress.pathAddress(PROFILE, "testA");
        ModelNode list = new ModelNode().add("bad-profile");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));

        ProfileUtils.executeDescribeProfile(kernelServices, "testA");
    }
}
