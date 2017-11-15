/*
 * Copyright 2017 JBoss by Red Hat.
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
package org.jboss.as.host.controller.model.host;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.operations.HostAddHandler;

public class HostDefinition extends SimpleResourceDefinition {

    private final HostControllerEnvironment environment;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final HostModelUtil.HostModelRegistrar hostModelRegistrar;
    private final Resource modelControllerResource;

    public HostDefinition(final HostControllerEnvironment environment,
                          final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                          final HostModelUtil.HostModelRegistrar hostModelRegistrar,
                          final Resource modelControllerResource) {
        super(new Parameters(PathElement.pathElement(HOST), HostModelUtil.getResourceDescriptionResolver()));
        this.environment = environment;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.hostModelRegistrar = hostModelRegistrar;
        this.modelControllerResource = modelControllerResource;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration hostDefinition) {
        super.registerOperations(hostDefinition);
        hostDefinition.registerOperationHandler(HostAddHandler.DEFINITION, new HostAddHandler(environment, ignoredDomainResourceRegistry, hostModelRegistrar, modelControllerResource));
    }

}
