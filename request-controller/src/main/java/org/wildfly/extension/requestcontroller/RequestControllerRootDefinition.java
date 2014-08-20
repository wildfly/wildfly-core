/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.wildfly.extension.requestcontroller;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Stuart Douglas
 */
class RequestControllerRootDefinition extends PersistentResourceDefinition {
    static final RequestControllerRootDefinition INSTANCE = new RequestControllerRootDefinition(new RequestController());

    static final PersistentResourceDefinition[] CHILDREN = {
    };

    private final RequestController requestController;

    public static final SimpleAttributeDefinition MAX_REQUESTS = SimpleAttributeDefinitionBuilder.create(Constants.MAX_REQUESTS, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(-1))
            .build();

    public static final SimpleAttributeDefinition ACTIVE_REQUESTS = SimpleAttributeDefinitionBuilder.create(Constants.ACTIVE_REQUESTS, ModelType.INT, true)
            .setStorageRuntime()
            .build();

    public static final AttributeDefinition[] ATTRIBUTES = {
            MAX_REQUESTS
    };

    private RequestControllerRootDefinition(RequestController requestController) {
        super(RequestControllerExtension.SUBSYSTEM_PATH,
                RequestControllerExtension.getResolver(),
                new RequestControllerSubsystemAdd(requestController),
                ReloadRequiredRemoveStepHandler.INSTANCE);
        this.requestController = requestController;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.<AttributeDefinition>singletonList(MAX_REQUESTS);
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(CHILDREN);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        MaxRequestsWriteHandler handler = new MaxRequestsWriteHandler(MAX_REQUESTS, requestController);
        resourceRegistration.registerReadWriteAttribute(MAX_REQUESTS, null, handler);
        resourceRegistration.registerMetric(ACTIVE_REQUESTS, new ActiveRequestsReadHandler(requestController));
    }
}
