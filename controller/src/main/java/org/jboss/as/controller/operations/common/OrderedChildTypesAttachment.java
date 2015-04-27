package org.jboss.as.controller.operations.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;

/**
 * We currently only care about ordered child resources when describing the model for the use of the sync handlers.
 * We don't care about all that when describing the model for the server boot operations. This attachment is used
 * to collect the information if present.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildTypesAttachment {

    public static final OperationContext.AttachmentKey<OrderedChildTypesAttachment> KEY =
            OperationContext.AttachmentKey.create(OrderedChildTypesAttachment.class);

    private final Map<PathAddress, Set<String>> orderedChildren = new HashMap<>();
    /**
     * If the resource has ordered child types, those child types will be stored in the attachment. If there are no
     * ordered child types, this method is a no-op.
     *
     * @param resourceAddress the address of the resource
     * @param resource the resource which may or may not have ordered children.
     */
    public void addOrderedChildResourceTypes(PathAddress resourceAddress, Resource resource) {
        Set<String> orderedChildTypes = resource.getOrderedChildTypes();
        if (orderedChildTypes.size() > 0) {
            orderedChildren.put(resourceAddress, resource.getOrderedChildTypes());
        }
    }

    /**
     *
     */
    public Set<String> getOrderedChildTypes(PathAddress resourceAddress) {
        //The describe handlers don't append the profile element at the stage when the addOrderedChildResourceTypes()
        //method gets called, so strip it off here.
        final PathAddress lookupAddress =
                resourceAddress.size() > 0 && resourceAddress.getElement(0).getKey().equals(ModelDescriptionConstants.PROFILE) ?
                        resourceAddress.subAddress(1) :
                        resourceAddress;

        return orderedChildren.get(lookupAddress);
    }


}
