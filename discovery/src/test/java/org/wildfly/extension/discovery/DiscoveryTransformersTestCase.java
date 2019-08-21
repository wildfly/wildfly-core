/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.discovery;

import java.util.Locale;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Paul Ferraro
 */
public class DiscoveryTransformersTestCase extends AbstractSubsystemTest {

    public DiscoveryTransformersTestCase() {
        super(DiscoveryExtension.SUBSYSTEM_NAME, new DiscoveryExtension());
    }

    private static String formatEAP7SubsystemArtifact(ModelTestControllerVersion version) {
        return String.format(Locale.ROOT, "org.wildfly.core:wildfly-discovery:%s", version.getCoreVersion());
    }

    private static DiscoveryModel getModelVersion(ModelTestControllerVersion controllerVersion) {
        switch (controllerVersion) {
            case EAP_7_1_0:
            case EAP_7_2_0:
                return DiscoveryModel.VERSION_1_0_0;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static String[] getDependencies(ModelTestControllerVersion version) {
        switch (version) {
            case EAP_7_1_0:
            case EAP_7_2_0:
                return new String[] {
                        formatEAP7SubsystemArtifact(version),
                };
            default:
                throw new IllegalArgumentException();
        }
    }

    @Test
    public void testTransformerEAP710() throws Exception {
        this.testTransformation(ModelTestControllerVersion.EAP_7_1_0);
    }

    @Test
    public void testTransformerEAP720() throws Exception {
        this.testTransformation(ModelTestControllerVersion.EAP_7_2_0);
    }

    private void testTransformation(final ModelTestControllerVersion controller) throws Exception {
        final ModelVersion version = getModelVersion(controller).getVersion();
        final String[] dependencies = getDependencies(controller);

        KernelServices services = this.buildKernelServices(String.format(Locale.ROOT, "discovery-transform-%d.%d.%d.xml", version.getMajor(), version.getMinor(), version.getMicro()), controller, version, dependencies);

        checkSubsystemModelTransformation(services, version, null, false);
    }

    private static AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.MANAGEMENT;
    }

    private KernelServicesBuilder createKernelServicesBuilder() {
        return this.createKernelServicesBuilder(createAdditionalInitialization());
    }

    private KernelServices buildKernelServices(String subsystemXml, ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder().setSubsystemXmlResource(subsystemXml);

        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
        ;

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }
}
