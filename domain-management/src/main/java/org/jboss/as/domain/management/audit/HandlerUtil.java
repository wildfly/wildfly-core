/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JSON_FORMATTER;
import static org.jboss.as.domain.management.audit.AuditLogHandlerResourceDefinition.HANDLER_TYPES;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class HandlerUtil {

    static void checkNoOtherHandlerWithTheSameName(OperationContext context) throws OperationFailedException {
        final PathAddress address = context.getCurrentAddress();
        final PathAddress parentAddress = address.subAddress(0, address.size() - 1);
        final Resource resource = context.readResourceFromRoot(parentAddress);

        final PathElement element = address.getLastElement();
        final String handlerType = element.getKey();
        final String handlerName = element.getValue();

        for (String otherHandler: HANDLER_TYPES) {
            if (handlerType.equals(otherHandler)) {
                // we need to check other handler types for the same name
                continue;
            }
            final PathElement check = PathElement.pathElement(otherHandler, handlerName);
            if (resource.hasChild(check)) {
                throw DomainManagementLogger.ROOT_LOGGER.handlerAlreadyExists(check.getValue(), parentAddress.append(check));
            }
        }
    }

    static boolean lookForHandler(final OperationContext context, final PathAddress addr, final String name) {
        final PathAddress subAddress = addr.subAddress(0, addr.size() - 2);
        final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

        for (String handlerType: HANDLER_TYPES) {
            final PathAddress referenceAddress = subAddress.append(handlerType, name);
            if (lookForResource(root, referenceAddress)) {
                return true;
            }
        }
        return false;
    }

    static boolean lookForFormatter(OperationContext context, PathAddress addr, String name) {
        PathAddress referenceAddress = addr.subAddress(0, addr.size() - 1).append(JSON_FORMATTER, name);
        final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
        return lookForResource(root, referenceAddress);
    }

    private static boolean lookForResource(final Resource root, final PathAddress pathAddress) {
        Resource current = root;
        for (PathElement element : pathAddress) {
            current = current.getChild(element);
            if (current == null) {
                return false;
            }
        }
        return true;
    }
}
