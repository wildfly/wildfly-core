/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2012, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.jboss.as.threads;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.SingleClassFilter;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class ThreadsSubsystemTestCase extends AbstractSubsystemBaseTest {
    public ThreadsSubsystemTestCase() {
        super(ThreadsExtension.SUBSYSTEM_NAME, new ThreadsExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("threads-subsystem-2_0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-threads_2_0.xsd";
    }

    @Test
    public void testExpressions() throws Exception {
        standardSubsystemTest("expressions.xml");
    }


    @Test
    public void testTransformerWFY80() throws Exception {
        testTransformer_1_1(ModelTestControllerVersion.WILDFLY_8_0_0_FINAL);
    }

    /**
     * Tests transformation of model from 2.0.0 version into 1.1.0 version.
     *
     * @throws Exception
     */
    private void testTransformer_1_1(ModelTestControllerVersion controllerVersion) throws Exception {
        String subsystemXml = "threads-transform-1_1.xml";
        ModelVersion modelVersion = ModelVersion.create(1, 1, 0); //The old model version
        //Use the non-runtime version of the extension which will happen on the HC
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT)
                .setSubsystemXmlResource(subsystemXml);

        final PathAddress subsystemAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, getMainSubsystemName()));
        // Add legacy subsystems
        builder.createLegacyKernelServicesBuilder(null, controllerVersion, modelVersion)
                .addOperationValidationResolve("add", subsystemAddress.append(PathElement.pathElement(CommonAttributes.THREAD_FACTORY)))
                .addMavenResourceURL("org.wildfly:wildfly-threads:" + controllerVersion.getMavenGavVersion())
                .excludeFromParent(SingleClassFilter.createFilter(ThreadsLogger.class));

        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertNotNull(legacyServices);
        checkSubsystemModelTransformation(mainServices, modelVersion);
    }

    @Override
    protected void validateDescribeOperation(KernelServices hc, AdditionalInitialization serverInit, ModelNode expectedModel) throws Exception {
        final ModelNode operation = createDescribeOperation();
        final ModelNode result = hc.executeOperation(operation);
        Assert.assertTrue("The subsystem describe operation must fail",
                result.hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION));
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization() {

            @Override
            protected ProcessType getProcessType() {
                return ProcessType.HOST_CONTROLLER;
            }

            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.ADMIN_ONLY;
            }
        };
    }
}
