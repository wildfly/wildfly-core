/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.core.model.test.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.model.test.FailedOperationTransformationConfig.REJECTED_RESOURCE;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for deployment transformers.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(TransformersTestParameterized.class)
public class DomainDeploymentTransformersTestCase extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    public DomainDeploymentTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }

    @TransformersParameter
    public static List<TransformersTestParameter> parameters() {
        return TransformersTestParameter.setupVersions();
    }

    @Test
    public void domainDeployment() throws Exception {
        try {
            KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                    .setXmlResource("domain-deployments.xml")
                    .createContentRepositoryContent("12345678901234567890");
            builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion).configureReverseControllerCheck(ARCHIVE_REDEFINER, ARCHIVE_REDEFINER);
            KernelServices mainServices = builder.build();
            Assert.assertTrue(mainServices.isSuccessfulBoot());

            KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
            Assert.assertTrue("Legacy services didn't boot for version " + modelVersion, legacyServices.isSuccessfulBoot());
            checkCoreModelTransformation(mainServices, modelVersion);
        } catch (Exception ex) {
            throw new RuntimeException("Error for version " + modelVersion + " " + ex.getMessage(), ex);
        }
    }

    @Test
    public void domainExplodedDeployment() throws Exception {
        try {
            KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                    .setXmlResource("domain-deployments.xml")
                    .createContentRepositoryContent("12345678901234567890");
            builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion).configureReverseControllerCheck(ARCHIVE_REDEFINER, ARCHIVE_REDEFINER);
            KernelServices mainServices = builder.build();
            Assert.assertTrue(mainServices.isSuccessfulBoot());

            KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
            Assert.assertTrue("Legacy services didn't boot for version " + modelVersion, legacyServices.isSuccessfulBoot());

            List<ModelNode> operations = builder.parseXmlResource("domain-exploded-deployments.xml");
            //removing the ading of "management-client-content" => "rollout-plans
            operations = operations.subList(0, operations.size() -2);
            ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, operations, getConfig());
        } catch (Exception ex) {
            throw new RuntimeException("Error for version " + modelVersion + " " + ex.getMessage(), ex);
        }
    }

    private FailedOperationTransformationConfig getConfig() {
        return new FailedOperationTransformationConfig()
                .addFailedAttribute(PathAddress.pathAddress().append(DEPLOYMENT, "myThirdApp"), new RejectArchiveConfig())
                .addFailedAttribute(PathAddress.pathAddress(PathElement.pathElement("management-client-content","rollout-plans")), REJECTED_RESOURCE);
    }

    private static final ModelFixer ARCHIVE_REDEFINER = new ModelFixer() {
        @Override
        public ModelNode fixModel(ModelNode modelNode) {
            ModelNode clone = modelNode.clone();
            List<String> applications = Arrays.asList("myFirstApp", "mySecondApp", "myThirdApp");
            for (String app : applications) {
                if (modelNode.hasDefined("deployment", app, "content")) {
                    clone.get("deployment", app, "content").clear().set(removeArchive(modelNode.get("deployment", app, "content")));
                }
            }
            return clone;
        }

        private ModelNode removeArchive(ModelNode content) {
            ModelNode clone = content.clone();
            if (content.hasDefined(0) && !content.get(0).hasDefined(ARCHIVE)) {
                clone.get(0).get(ARCHIVE).set(true);
            }
            return clone;
        }
    };

    public static class RejectArchiveConfig implements FailedOperationTransformationConfig.PathAddressConfig {

        @Override
        public boolean expectFailed(ModelNode operation) {
            if (ADD.equals(operation.get(OP).asString())) {
                if (operation.hasDefined(CONTENT) && operation.get(CONTENT).hasDefined(0)) {
                    ModelNode content = operation.require(CONTENT).require(0);
                    return content.hasDefined(ARCHIVE);
                }
            }
            return false;
        }

        @Override
        public boolean canCorrectMore(ModelNode operation) {
            return false;
        }

        @Override
        public ModelNode correctOperation(ModelNode operation) {
            return operation;
        }

        @Override
        public List<ModelNode> createWriteAttributeOperations(ModelNode operation) {
            return Collections.emptyList();
        }

        @Override
        public boolean expectFailedWriteAttributeOperation(ModelNode operation) {
            return false;
        }

        @Override
        public ModelNode correctWriteAttributeOperation(ModelNode operation) {
            return operation;
        }

        @Override
        public boolean expectDiscarded(ModelNode operation) {
            return false;
        }

    }
}
