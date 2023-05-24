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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * An add handler which makes use of a {@code Doohickey} to coordinate making a resource available
 * both as an MSC service and as a runtime API. This is a compatibility wrapper, not required by new usages of the
 * corresponding common class.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class DoohickeyAddHandler<T> extends ElytronCommonDoohickeyAddHandler<T> {

    public DoohickeyAddHandler(RuntimeCapability<?> runtimeCapability, AttributeDefinition[] configAttributes, String apiCapabilityName) {
        super(ElytronExtension.class, runtimeCapability, configAttributes, apiCapabilityName);
    }
}
