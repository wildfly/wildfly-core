/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * {@code OperationTransformer} and {@code ResourceTransformer} transforming the operation address only.
 *
 * @author Emanuel Muckenhuber
 */
@Deprecated //todo could probably be removed as it is not used anywhere
public class AliasOperationTransformer implements CombinedTransformer {

    public interface AddressTransformer {

        /**
         * Transform an address.
         *
         * @param address the address to transform
         * @return the transformed address
         */
        PathAddress transformAddress(PathAddress address);

    }

    private final AddressTransformer transformer;
    protected AliasOperationTransformer(AddressTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public TransformedOperation transformOperation(final TransformationContext context, final PathAddress address, final ModelNode original) throws OperationFailedException{
        final ModelNode operation = original.clone();
        final PathAddress transformedAddress = transformer.transformAddress(address);
        operation.get(ModelDescriptionConstants.OP_ADDR).set(transformedAddress.toModelNode());

        // Hand-off to a local operation transformer at the right address
        final String operationName = operation.get(ModelDescriptionConstants.OP).asString();
        final OperationTransformer aliasTransformer = context.getTarget().resolveTransformer(context, transformedAddress, operationName);
        if(aliasTransformer != null) {
            return aliasTransformer.transformOperation(context, transformedAddress, operation);
        } else {
            return new TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
        }
    }

    @Override
    public void transformResource(final ResourceTransformationContext currentCtx, final PathAddress address, final Resource resource) throws OperationFailedException {
        final PathAddress transformedAddress = transformer.transformAddress(address);
        final ResourceTransformationContext context = ResourceTransformationContextImpl.createAliasContext(transformedAddress, currentCtx);
        final ResourceTransformer aliasTransformer = context.getTarget().resolveTransformer(null, transformedAddress);
        if(aliasTransformer != null) {
            aliasTransformer.transformResource(context, address, resource);
        } else {
            ResourceTransformer.DEFAULT.transformResource(context, transformedAddress, resource);
        }
    }

    /**
     * Replace the last element of an address with a static path element.
     *
     * @param element the path element
     * @return the operation address transformer
     */
    public static AliasOperationTransformer replaceLastElement(final PathElement element) {
        return create(new AddressTransformer() {
            @Override
            public PathAddress transformAddress(final PathAddress original) {
                final PathAddress address = original.subAddress(0, original.size() -1);
                return address.append(element);
            }
        });
    }

    public static AliasOperationTransformer create(final AddressTransformer transformer) {
        return new AliasOperationTransformer(transformer);
    }

}
