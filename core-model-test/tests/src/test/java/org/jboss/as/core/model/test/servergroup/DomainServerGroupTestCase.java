/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.core.model.test.servergroup;

import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic model testing of server-group resources.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class DomainServerGroupTestCase extends AbstractCoreModelTest {

    @Test
    public void testServerGroupXml() throws Exception {
        testServerGroupXml("servergroup.xml");
    }

    @Test
    public void testServerGroupXmlExpressions() throws Exception {
        testServerGroupXml("servergroup-with-expressions.xml");
    }

    @Test
    public void testServerGroupXmlWithWrongDeployments() throws Exception {
        try {
            createKernelServicesBuilder(TestModelType.DOMAIN)
                    .setXmlResource("servergroup-with-duplicate-runtime-names.xml")
                    .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                    .createContentRepositoryContent("12345678901234567890")
                    .createContentRepositoryContent("09876543210987654321")
                    .build();
        } catch (XMLStreamException ex) {
            String expectedMessage = ControllerLogger.ROOT_LOGGER.duplicateNamedElement("foo.war", new Location() {
                public int getLineNumber() {
                    return 1634;
                }

                public int getColumnNumber() {
                    return 1;
                }

                public int getCharacterOffset() {
                    return 1;
                }

                public String getPublicId() {
                    return "";
                }

                public String getSystemId() {
                    return "";
                }
            }).getMessage();
            expectedMessage = expectedMessage.substring(expectedMessage.indexOf("WFLYCTL0073:"));
            MatcherAssert.assertThat(ex.getMessage(), containsString(expectedMessage));
        }
    }

    @Test
    public void testServerGroupXmlWithDeployments() throws Exception {
        testServerGroupXml("servergroup-with-deployments.xml");
    }

    @Test
    public void testAddServerGroupBadProfile() throws Exception {

        KernelServices kernelServices = createKernelServices("servergroup.xml");

        PathAddress pa = PathAddress.pathAddress(SERVER_GROUP, "group-three");

        final ModelNode operation = Util.createAddOperation(pa);
        operation.get(PROFILE).set("bad-profile");
        operation.get(SOCKET_BINDING_GROUP).set("test-sockets");

        ModelNode response = kernelServices.executeOperation(operation);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testChangeServerGroupInvalidProfile() throws Exception {

        KernelServices kernelServices = createKernelServices("servergroup.xml");

        PathAddress pa = PathAddress.pathAddress(SERVER_GROUP, "test");
        ModelNode op = Util.getWriteAttributeOperation(pa, PROFILE, "does-not-exist");
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testAddServerGroupBadSocketBindingGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("servergroup.xml");

        PathAddress pa = PathAddress.pathAddress(SERVER_GROUP, "group-three");

        final ModelNode operation = Util.createAddOperation(pa);
        operation.get(PROFILE).set("test");
        operation.get(SOCKET_BINDING_GROUP).set("bad-sockets");

        ModelNode response = kernelServices.executeOperation(operation);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    @Test
    public void testChangeServerGroupInvalidSocketBindingGroup() throws Exception {

        KernelServices kernelServices = createKernelServices("servergroup.xml");

        PathAddress pa = PathAddress.pathAddress(SERVER_GROUP, "test");
        ModelNode op = Util.getWriteAttributeOperation(pa, SOCKET_BINDING_GROUP, "does-not-exist");
        ModelNode response = kernelServices.executeOperation(op);
        Assert.assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        Assert.assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0369"));
    }

    private void testServerGroupXml(String resource) throws Exception {

        KernelServices kernelServices = createKernelServices(resource);

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), resource), marshalled);

        kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXml(marshalled)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

    }

    private KernelServices createKernelServices(String resource) throws Exception {

        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource(resource)
                .setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER)
                .createContentRepositoryContent("12345678901234567890")
                .createContentRepositoryContent("09876543210987654321")
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        return kernelServices;

    }

}
