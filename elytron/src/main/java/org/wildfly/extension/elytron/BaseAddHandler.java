/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.extension.elytron.common.ElytronCommonBaseAddHandler;

/**
 * An extension of {@link AbstractAddStepHandler} to ensure all Elytron runtime operations are performed in the required server
 * states. This is a compatibility wrapper, not required by new usages of the corresponding common class.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class BaseAddHandler extends ElytronCommonBaseAddHandler {

    BaseAddHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    BaseAddHandler(RuntimeCapability<?> runtimeCapability, AttributeDefinition... attributes) {
        super(runtimeCapability, attributes);
    }

    BaseAddHandler(Set<RuntimeCapability> capabilities, AttributeDefinition... attributes) {
        super(capabilities, attributes);
    }

    @Override
    protected String getSubsystemCapability() {
        return Capabilities.ELYTRON_CAPABILITY;
    }
}
