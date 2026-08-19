/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BROWSE_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPLODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE_CONTENT;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilderFromCurrent;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.isUnmanagedContent;

import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderFactory;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.controller.resources.DeploymentResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * Transformers for deployments in domain mode.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
class DeploymentTransformers {
    static ChainedTransformationDescriptionBuilder buildTransformerChain(ChainedTransformationDescriptionBuilderFactory factory) {
        ChainedTransformationDescriptionBuilder chainedBuilder = factory.createChainedTransformationDescriptionBuilder(DeploymentResourceDefinition.PATH);

        ResourceTransformationDescriptionBuilder builder = createBuilderFromCurrent(chainedBuilder, KernelAPIVersion.VERSION_4_1);
        builder
                .getAttributeBuilder().addRejectCheck(EXPLODED_REJECT, DeploymentAttributes.CONTENT_RESOURCE_ALL)
                    .setValueConverter(ARCHIVE_REMOVER, DeploymentAttributes.CONTENT_RESOURCE_ALL)
                    .end()
                .addOperationTransformationOverride(READ_ATTRIBUTE_OPERATION)
                    .setDiscard(DiscardAttributeChecker.ALWAYS, DeploymentAttributes.MANAGED)
                    .end()
                .addOperationTransformationOverride(ADD)
                    .addRejectCheck(EXPLODED_REJECT, DeploymentAttributes.CONTENT_PARAM_ALL)
                    .setValueConverter(ARCHIVE_REMOVER, DeploymentAttributes.CONTENT_PARAM_ALL)
                    .end()
                .discardOperations(BROWSE_CONTENT, READ_CONTENT, EXPLODE, ADD_CONTENT, REMOVE_CONTENT);
        return chainedBuilder;
    }

    private static final RejectAttributeChecker EXPLODED_REJECT = new RejectAttributeChecker.ListRejectAttributeChecker(new RejectAttributeChecker.DefaultRejectAttributeChecker() {
        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            return !isUnmanagedContent(attributeValue) && attributeValue.hasDefined(ARCHIVE) && !attributeValue.get(ARCHIVE).asBoolean(true);
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
}
