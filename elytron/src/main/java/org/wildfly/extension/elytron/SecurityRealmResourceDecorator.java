/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * A {@link DelegatingResourceDefinition} that decorates a given {@link ResourceDefinition} with additional definitions.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class SecurityRealmResourceDecorator extends DelegatingResourceDefinition {

    static ResourceDefinition wrap(ResourceDefinition resourceDefinition) {
        return new SecurityRealmResourceDecorator(resourceDefinition);
    }

    SecurityRealmResourceDecorator(ResourceDefinition resourceDefinition) {
        setDelegate(resourceDefinition);
    }

    public void registerChildren(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new IdentityResourceDefinition(this.delegate));
    }
}
