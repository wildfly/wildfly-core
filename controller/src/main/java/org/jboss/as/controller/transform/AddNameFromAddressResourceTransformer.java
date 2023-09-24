/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;

/**
 * {@link ResourceTransformer} that takes the value in the last element of the given address
 * and stores it in a model attribute named {@code name}.
 * <p>
 * This transformer can be used to preserve compatibility when {@link org.jboss.as.controller.ReadResourceNameOperationStepHandler} is
 * used to replace storage of a resource name in the model.
 * </p>
 *
 * @see org.jboss.as.controller.ReadResourceNameOperationStepHandler
 */
@SuppressWarnings("deprecation")
public class AddNameFromAddressResourceTransformer implements ResourceTransformer {
    public static final AddNameFromAddressResourceTransformer INSTANCE = new AddNameFromAddressResourceTransformer();

    private AddNameFromAddressResourceTransformer() {
    }

    @Override
    public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {

        transformResourceInternal(address, resource);
        ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, resource);
        childContext.processChildren(resource);
    }

    private void transformResourceInternal(final PathAddress address, final Resource resource) throws OperationFailedException {

        final PathElement element = address.getLastElement();
        resource.getModel().get(NAME).set(element.getValue());
    }
}
