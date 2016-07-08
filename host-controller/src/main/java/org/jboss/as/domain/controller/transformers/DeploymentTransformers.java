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
package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BROWSE_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPLODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE_CONTENT;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.controller.resources.DeploymentResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * Transorformers for deployments in domain mode.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
class DeploymentTransformers {
    public static ChainedTransformationDescriptionBuilder buildTransformerChain(ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder chainedBuilder =
                TransformationDescriptionBuilder.Factory.createChainedInstance(DeploymentResourceDefinition.PATH, currentVersion);

        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_4_1);
        builder
                .getAttributeBuilder().addRejectCheck(EXPLODED_REJECT, DeploymentAttributes.CONTENT_ALL)
                    .setValueConverter(ARCHIVE_REMOVER, DeploymentAttributes.CONTENT_ALL)
                    .end()
                .addOperationTransformationOverride(ADD)
                    .addRejectCheck(EXPLODED_REJECT, DeploymentAttributes.CONTENT_ALL)
                    .setValueConverter(ARCHIVE_REMOVER, DeploymentAttributes.CONTENT_ALL)
                    .end()
                .discardOperations(BROWSE_CONTENT, READ_CONTENT, EXPLODE, ADD_CONTENT, REMOVE_CONTENT);
        return chainedBuilder;
    }

    private static final RejectAttributeChecker EXPLODED_REJECT = new RejectAttributeChecker.ListRejectAttributeChecker(new RejectAttributeChecker.DefaultRejectAttributeChecker() {
        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (!isUnmanagedContent(attributeValue)) {
                return attributeValue.hasDefined(ARCHIVE) && !attributeValue.get(ARCHIVE).asBoolean(true);
            }
            return false;
        }

        @Override
        public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
            return ControllerLogger.ROOT_LOGGER.explodedDeploymentNotSupported();
        }
    });

    private static final AttributeConverter ARCHIVE_REMOVER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                for (ModelNode content : attributeValue.asList()) {
                    if (!isUnmanagedContent(content)) {
                        if (content.hasDefined(ARCHIVE) && content.get(ARCHIVE).asBoolean(true)) {
                            content.remove(ARCHIVE);
                        }
                    }
                }
            }
        }
    };

    private static final List<String> UNMANAGED_CONTENT_ATTRIBUTES = Arrays.asList(DeploymentAttributes.CONTENT_PATH.getName(), DeploymentAttributes.CONTENT_RELATIVE_TO.getName());

    private static boolean isUnmanagedContent(ModelNode content) {
        return UNMANAGED_CONTENT_ATTRIBUTES.stream().anyMatch((s) -> (content.hasDefined(s)));
    }
}
