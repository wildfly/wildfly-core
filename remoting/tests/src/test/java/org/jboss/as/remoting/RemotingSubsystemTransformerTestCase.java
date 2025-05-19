/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_4_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_8_0_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_8_1_0;
import static org.jboss.as.remoting.RemotingSubsystemTestUtil.DEFAULT_ADDITIONAL_INITIALIZATION;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelFixer;
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
 * Transformer tests for the remoting subsystem.
 */
@RunWith(Parameterized.class)
public class RemotingSubsystemTransformerTestCase extends AbstractSubsystemTest {

    @Parameters
    public static Iterable<ModelTestControllerVersion> parameters() {
        return EnumSet.of(EAP_8_1_0, EAP_8_0_0, EAP_7_4_0);
    }

    private final ModelTestControllerVersion controller;
    private final AdditionalInitialization additionalInitialization = DEFAULT_ADDITIONAL_INITIALIZATION;
    private final ModelVersion version;

    public RemotingSubsystemTransformerTestCase(ModelTestControllerVersion controller) {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
        this.controller = controller;
        this.version = this.getModelVersion();
    }

    private String formatSubsystemArtifact() {
        return this.formatArtifact("%s:wildfly-remoting:%s");
    }

    private String formatArtifact(String pattern) {
        return String.format(pattern, this.controller.getCoreMavenGroupId(), this.controller.getCoreVersion());
    }

    private ModelVersion getModelVersion() {
        switch (this.controller) {
            case EAP_7_4_0:
                return EAP_7_4_0.getSubsystemModelVersion(getMainSubsystemName());
            case EAP_8_0_0:
                return EAP_8_0_0.getSubsystemModelVersion(getMainSubsystemName());
            case EAP_8_1_0:
                return EAP_8_1_0.getSubsystemModelVersion(getMainSubsystemName());
            default:
                throw new IllegalArgumentException();
        }
    }

    private String[] getDependencies() {
        switch (this.controller) {
            case EAP_7_4_0:
            case EAP_8_0_0:
            case EAP_8_1_0:
                return new String[]{
                        formatSubsystemArtifact(),
                };
            default:
                throw new IllegalArgumentException();
        }
    }

    @Test
    public void testTransformation() throws Exception {
        String subsystemXmlResource = String.format("remoting-transform-%d.%d.%d.xml", this.version.getMajor(), this.version.getMinor(), this.version.getMicro());

        KernelServicesBuilder builder = createKernelServicesBuilder(this.additionalInitialization)
                .setSubsystemXmlResource(subsystemXmlResource);

        builder.createLegacyKernelServicesBuilder(this.additionalInitialization, this.controller, this.version)
                .addMavenResourceURL(this.getDependencies())
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();

        assertTrue(services.isSuccessfulBoot());
        assertTrue(services.getLegacyServices(this.version).isSuccessfulBoot());

        ModelFixer fixer = model -> {
            if (RemotingSubsystemModel.VERSION_7_0_0.requiresTransformation(this.version)) {
                // Deprecated /subsystem=remoting/configuration=endpoint resource was dropped
                model.remove("configuration");
            }
            return model;
        };

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, this.version, fixer, false);
    }

    @Test
    public void testRejectingTransformers() throws Exception {
        testRejectingTransformers(this.controller, this.version);
    }

    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion, ModelVersion modelVersion) throws Exception {
        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION);

        builder.createLegacyKernelServicesBuilder(DEFAULT_ADDITIONAL_INITIALIZATION, controllerVersion, modelVersion)
                .addMavenResourceURL(getDependencies())
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(services.isSuccessfulBoot());
        KernelServices legacyServices = services.getLegacyServices(modelVersion);
        Assert.assertNotNull(legacyServices);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("remoting-transform-rejects.xml");
        PathAddress subsystemAddress = PathAddress.pathAddress("subsystem", RemotingExtension.SUBSYSTEM_NAME);

        ModelTestUtils.checkFailedTransformedBootOperations(services, modelVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(ConnectorResource.PATH),
                        new FailedOperationTransformationConfig
                                .NewAttributesConfig(ConnectorResource.PROTOCOL)
                )
        );

    }
}
