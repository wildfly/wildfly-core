/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.PERMISSION_SET_RUNTIME_CAPABILITY;
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

    static final ObjectListAttributeDefinition PERMISSIONS = new ObjectListAttributeDefinition.Builder(ElytronCommonConstants.PERMISSIONS, PERMISSION)
            .setRequired(false)
            .setRestartAllServices()
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .build();

    static ResourceDefinition getPermissionSet() {
        final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PERMISSIONS };
        ElytronCommonTrivialAddHandler<Permissions> add = new ElytronCommonTrivialAddHandler<Permissions>(Permissions.class, ATTRIBUTES, PERMISSION_SET_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<Permissions> getValueSupplier(ServiceBuilder<Permissions> serviceBuilder,
                                                                   OperationContext context, ModelNode model) throws OperationFailedException {

                final List<Permission> permissionsList = new ArrayList<>();
                if (model.hasDefined(ElytronCommonConstants.PERMISSIONS)) {
                    for (ModelNode permission : model.get(ElytronCommonConstants.PERMISSIONS).asList()) {
                        permissionsList.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                MODULE.resolveModelAttribute(context, permission).asStringOrNull(),
                                TARGET_NAME.resolveModelAttribute(context, permission).asStringOrNull(),
                                ACTION.resolveModelAttribute(context, permission).asStringOrNull()));
                    }
                }
                return () -> createPermissions(permissionsList);
            }
        };

        return new ElytronCommonTrivialResourceDefinition(ElytronCommonConstants.PERMISSION_SET, add, ATTRIBUTES, PERMISSION_SET_RUNTIME_CAPABILITY);
    }
}
