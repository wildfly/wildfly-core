/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.RemotingSubsystemTestUtil.DEFAULT_ADDITIONAL_INITIALIZATION;

import java.util.EnumSet;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
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
        return EnumSet.of(ModelTestControllerVersion.EAP_7_4_0);
    }

    private final ModelTestControllerVersion controller;
    private final AdditionalInitialization additionalInitialization = DEFAULT_ADDITIONAL_INITIALIZATION;
    private final ModelVersion version;

    public RemotingSubsystemTransformerTestCase(ModelTestControllerVersion controller) {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
        this.controller = controller;
        this.version = this.getModelVersion().getVersion();
    }

    private String formatSubsystemArtifact() {
        return this.formatArtifact("%s:wildfly-remoting:%s");
    }

    private String formatArtifact(String pattern) {
        return String.format(pattern, this.controller.getCoreMavenGroupId(), this.controller.getCoreVersion());
    }

    private RemotingSubsystemModel getModelVersion() {
        switch (this.controller) {
            case EAP_7_4_0:
                return RemotingSubsystemModel.VERSION_5_0_0;
            default:
                throw new IllegalArgumentException();
        }
    }

    private String[] getDependencies() {
        switch (this.controller) {
            case EAP_7_4_0:
                return new String[] {
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

        Assert.assertTrue(services.isSuccessfulBoot());
        Assert.assertTrue(services.getLegacyServices(this.version).isSuccessfulBoot());

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
}
