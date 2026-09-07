/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Model transformation tests for the IO subsystem.
 */
@RunWith(value = Parameterized.class)
public class IOSubsystemTransformerTestCase extends AbstractSubsystemTest {

    @Parameters
    public static Iterable<ModelTestControllerVersion> parameters() {
        return EnumSet.of(ModelTestControllerVersion.EAP_7_4_0, ModelTestControllerVersion.EAP_8_0_0, ModelTestControllerVersion.EAP_8_1_0);
    }

    private final ModelTestControllerVersion controller;
    private final AdditionalInitialization additionalInitialization;
    private final ModelVersion version;

    public IOSubsystemTransformerTestCase(ModelTestControllerVersion controller) {
        super(IOSubsystemResourceDefinitionRegistrar.REGISTRATION.getName(), new IOExtension());
        this.controller = controller;
        this.version = this.getModelVersion().getVersion();
        this.additionalInitialization = AdditionalInitialization.MANAGEMENT;
    }

    private String formatSubsystemArtifact() {
        return this.formatArtifact("org.wildfly.core:wildfly-io:%s");
    }

    private String formatArtifact(String pattern) {
        return String.format(pattern, this.controller.getCoreVersion());
    }

    private IOSubsystemModel getModelVersion() {
        switch (this.controller) {
            case EAP_7_4_0:
            case EAP_8_0_0:
                return IOSubsystemModel.VERSION_5_0_0;
            case EAP_8_1_0:
                return IOSubsystemModel.VERSION_6_0_0;
            default:
                throw new IllegalArgumentException();
        }
    }

    private String[] getDependencies() {
        switch (this.controller) {
            case EAP_7_4_0:
            case EAP_8_0_0:
                return new String[] {
                        formatSubsystemArtifact(),
                };
            case EAP_8_1_0:
                return new String[] {
                        formatArtifact("org.wildfly.core:wildfly-io:%s"),
                        formatArtifact("org.wildfly.core:wildfly-subsystem:%s"),
                };
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Tests transformation of model from current version into specified version.
     */
    @Test
    public void testTransformation() throws Exception {
        String subsystemXmlResource = String.format("io-transform-%d.%d.%d.xml", this.version.getMajor(), this.version.getMinor(), this.version.getMicro());

        // create builder for current subsystem version
        KernelServicesBuilder builder = createKernelServicesBuilder(this.additionalInitialization)
                .setSubsystemXmlResource(subsystemXmlResource);

        // initialize the legacy services and add required jars
        builder.createLegacyKernelServicesBuilder(this.additionalInitialization, this.controller, this.version)
                .addMavenResourceURL(getDependencies())
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();

        Assert.assertTrue(services.isSuccessfulBoot());
        Assert.assertTrue(services.getLegacyServices(this.version).isSuccessfulBoot());

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, this.version, null, false);
    }

    /**
     * Tests rejected transformation of the model from current version into specified version.
     */
    @Test
    public void testRejections() throws Exception {
        // create builder for current subsystem version
        KernelServicesBuilder builder = createKernelServicesBuilder(this.additionalInitialization);

        // initialize the legacy services and add required jars
        builder.createLegacyKernelServicesBuilder(this.additionalInitialization, this.controller, this.version)
                .addMavenResourceURL(this.getDependencies())
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(services.isSuccessfulBoot());
        KernelServices legacyServices = services.getLegacyServices(this.version);
        Assert.assertNotNull(legacyServices);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        List<ModelNode> operations = builder.parseXmlResource(String.format("io-transform-reject-%d.%d.%d.xml", this.version.getMajor(), this.version.getMinor(), this.version.getMicro()));
        ModelTestUtils.checkFailedTransformedBootOperations(services, this.version, operations, this.createFailedOperationTransformationConfig());
    }

    private FailedOperationTransformationConfig createFailedOperationTransformationConfig() {
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();
        PathAddress subsystemAddress = PathAddress.pathAddress(IOSubsystemResourceDefinitionRegistrar.REGISTRATION.getPathElement());

        if (IOSubsystemModel.VERSION_6_0_0.requiresTransformation(this.version)) {
            config.addFailedAttribute(subsystemAddress, new FailedOperationTransformationConfig.NewAttributesConfig(IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER.getName()));
        }

        return config;
    }
}
