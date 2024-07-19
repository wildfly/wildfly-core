/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.PERMISSION_SET_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.ACTION;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.PERMISSION;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.TARGET_NAME;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.createPermissions;

import java.security.Permissions;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron.PermissionMapperDefinitions.Permission;

/**
 * A {@link ResourceDefinition} for a permission set.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class PermissionSetDefinition {

    static final ObjectListAttributeDefinition PERMISSIONS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSIONS, PERMISSION)
            .setRequired(false)
            .setRestartAllServices()
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .build();

    static ResourceDefinition getPermissionSet() {
        final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PERMISSIONS };
        TrivialAddHandler<Permissions>  add = new TrivialAddHandler<Permissions>(Permissions.class, PERMISSION_SET_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<Permissions> getValueSupplier(ServiceBuilder<Permissions> serviceBuilder,
                                                                   OperationContext context, ModelNode model) throws OperationFailedException {

                final List<Permission> permissionsList = new ArrayList<>();
                if (model.hasDefined(ElytronDescriptionConstants.PERMISSIONS)) {
                    for (ModelNode permission : model.get(ElytronDescriptionConstants.PERMISSIONS).asList()) {
                        permissionsList.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                MODULE.resolveModelAttribute(context, permission).asStringOrNull(),
                                TARGET_NAME.resolveModelAttribute(context, permission).asStringOrNull(),
                                ACTION.resolveModelAttribute(context, permission).asStringOrNull()));
                    }
                }
                return () -> createPermissions(permissionsList);
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.PERMISSION_SET, add, ATTRIBUTES, PERMISSION_SET_RUNTIME_CAPABILITY);
    }
}
