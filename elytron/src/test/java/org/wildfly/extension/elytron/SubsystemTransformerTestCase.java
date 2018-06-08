/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_1_0;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of transformation of the elytron subsystem to previous API versions.
 *
 * @author Brian Stansberry
 * @author Tomaz Cerar
 */
public class SubsystemTransformerTestCase extends AbstractSubsystemBaseTest {

    public SubsystemTransformerTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("elytron-transformers-1.2.xml");
    }

    @Test
    public void testRejectingTransformersEAP710() throws Exception {
        testRejectingTransformers(EAP_7_1_0);
    }

    @Test
    public void testTransformerEAP710() throws Exception {
        testTransformation(EAP_7_1_0);
    }

    private KernelServices buildKernelServices(String xml, ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }

    private void testTransformation(final ModelTestControllerVersion controller) throws Exception {
        final ModelVersion version = controller.getSubsystemModelVersion(getMainSubsystemName());

        KernelServices services = this.buildKernelServices(getSubsystemXml(), controller, version,
                controller.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + controller.getCoreVersion());

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, version, null, false);

        ModelNode transformed = services.readTransformedModel(version);
        Assert.assertTrue(transformed.isDefined());
    }


    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion) throws Exception {
        ModelVersion elytronVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.withCapabilities(
                RuntimeCapability.buildDynamicCapabilityName(Capabilities.DATA_SOURCE_CAPABILITY_NAME, "ExampleDS")
        ));
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, elytronVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + controllerVersion.getCoreVersion())
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(elytronVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("elytron-transformers-1.2-reject.xml");
        PathAddress subsystemAddress = PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, elytronVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(KerberosSecurityFactoryDefinition.FAIL_CACHE)
                )
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "DisallowedScramSha384")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "DisallowedScramSha512")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
        );
        /*ModelTestUtils.checkFailedTransformedBootOperations(mainServices, elytronVersion, ops, new FailedOperationTransformationConfig()
               ...
        );*/
    }


}
