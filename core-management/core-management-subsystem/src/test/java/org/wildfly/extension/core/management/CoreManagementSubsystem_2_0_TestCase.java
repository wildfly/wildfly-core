/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.jboss.as.model.test.FailedOperationTransformationConfig.REJECTED_RESOURCE;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_8_0_0;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.core.management.CoreManagementExtension.SUBSYSTEM_PATH;
import static org.wildfly.extension.core.management.CoreManagementExtension.VERSION_1_0_0;
import static org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition.PATH;

public class CoreManagementSubsystem_2_0_TestCase extends AbstractSubsystemBaseTest {

    public CoreManagementSubsystem_2_0_TestCase() {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("core-management-subsystem-2_0.xml");
    }

    @Test
    public void testTransformersEAP8_0() throws Exception {
        testTransformers(EAP_8_0_0, VERSION_1_0_0, "core-management-subsystem-transformers-2_0.xml");
    }

    private void testTransformers(ModelTestControllerVersion legacyVersion,
                                  ModelVersion subsystemVersion,
                                  String xml) throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource(xml);

        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), legacyVersion, subsystemVersion)
                .addMavenResourceURL("org.wildfly.core:wildfly-core-management-subsystem:" + legacyVersion.getCoreVersion())
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(subsystemVersion).isSuccessfulBoot());

        checkSubsystemModelTransformation(mainServices, subsystemVersion);
        mainServices.shutdown();
    }

    @Test
    public void testRejectingTransformersEAP_8_0() throws Exception {
        testRejectingTransformers(EAP_8_0_0, VERSION_1_0_0, "core-management-subsystem-rejecting-2_0.xml");
    }

    private void testRejectingTransformers(ModelTestControllerVersion legacyVersion,
                                           ModelVersion subsystemVersion,
                                           String xml) throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization());

        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), legacyVersion, subsystemVersion)
                .addMavenResourceURL("org.wildfly.core:wildfly-core-management-subsystem:" + legacyVersion.getCoreVersion())
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(subsystemVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource(xml);
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM_PATH);

        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();
        config.addFailedAttribute(subsystemAddress.append(PATH), REJECTED_RESOURCE);

        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, subsystemVersion, ops, config);

        mainServices.shutdown();
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.ADMIN_ONLY_HC;
    }
}
