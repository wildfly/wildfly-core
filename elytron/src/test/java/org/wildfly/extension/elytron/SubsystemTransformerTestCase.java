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

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Tests of transformation of the elytron subsystem to previous API versions.
 *
 * @author Brian Stansberry
 */
public class SubsystemTransformerTestCase extends AbstractSubsystemTest {

    public SubsystemTransformerTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }


    @Test
    public void testRejectingTransformersWFCore300() throws Exception {
        ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_7_1_0;
        ModelVersion elytronVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT);
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, elytronVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId()+":wildfly-elytron-integration:"+controllerVersion.getCoreVersion())
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(elytronVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("transformers-1.0.xml");
        PathAddress subsystemAddress = PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
        /*ModelTestUtils.checkFailedTransformedBootOperations(mainServices, elytronVersion, ops, new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER)),
                        new MatchAllConfig()
                )
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuthenticationClientDefinitions.FORWARDING_MODE)
                )
        );*/
    }

    private static class MatchAllConfig extends FailedOperationTransformationConfig.AttributesPathAddressConfig<MatchAllConfig> {

        private MatchAllConfig() {
            super(PermissionMapperDefinitions.PERMISSION_MAPPINGS.getName());
        }

        @Override
        protected boolean isAttributeWritable(String attributeName) {
            return true;
        }

        @Override
        protected boolean checkValue(String attrName, ModelNode attribute, boolean isGeneratedWriteAttribute) {
            if (attribute.isDefined()) {
                for (ModelNode element : attribute.asList()) {
                    if (element.get(PermissionMapperDefinitions.MATCH_ALL.getName()).asBoolean(false)) {
                        return true;
                    }
                }
            }
            return  false;
        }

        @Override
        protected ModelNode correctValue(ModelNode toResolve, boolean isGeneratedWriteAttribute) {
            ModelNode corrected = toResolve.clone();
            if (corrected.isDefined()) {
                for (ModelNode element : corrected.asList()) {
                    ModelNode matchAll = element.get(PermissionMapperDefinitions.MATCH_ALL.getName());
                    if (matchAll.asBoolean(false)) {
                        matchAll.clear();
                        element.get(PermissionMapperDefinitions.ROLES.getName()).add("Administrator");

                    }
                }
            }
            return corrected;
        }
    }
}
