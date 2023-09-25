/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import java.util.Arrays;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Add handler for a resource that represents a named bit of re-usable DMR.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentStoreHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "store";

    private final AttributeDefinition contentAttribute;
    private final ResourceDescriptionResolver descriptionResolver;

    public ManagedDMRContentStoreHandler(final AttributeDefinition contentAttribute, ResourceDescriptionResolver descriptionResolver) {
        this.contentAttribute = contentAttribute;
        this.descriptionResolver = descriptionResolver;
    }

    public OperationDefinition getDefinition() {
        return new SimpleOperationDefinitionBuilder(OPERATION_NAME, descriptionResolver)
                .setParameters(ManagedDMRContentResourceDefinition.HASH, contentAttribute)
                .build();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final byte[] oldHash = ManagedDMRContentResourceDefinition.HASH.validateOperation(operation).asBytes();
        final byte[] currentHash = resource.getModel().get(ManagedDMRContentResourceDefinition.HASH.getName()).asBytes();
        if (!Arrays.equals(oldHash, currentHash)) {
            throw ManagedDMRContentLogger.ROOT_LOGGER.invalidHash(HashUtil.bytesToHexString(oldHash), PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)), HashUtil.bytesToHexString(currentHash));
        }
        ModelNode model = new ModelNode();
        contentAttribute.validateAndSet(operation, model);

        // IMPORTANT: Use writeModel, as this is what causes the content to be flushed to the content repo!
        resource.writeModel(model);
    }
}
