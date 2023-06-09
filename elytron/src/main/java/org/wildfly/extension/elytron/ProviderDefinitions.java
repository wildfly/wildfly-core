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

import java.security.Provider;

import org.jboss.as.controller.ResourceDefinition;
import org.wildfly.extension.elytron.common.ElytronCommonAggregateComponentDefinition;
import org.wildfly.extension.elytron.common.ElytronCommonProviderDefinitions;

/**
 * Resource definition(s) for resources satisfying the Provider[] capability. This is a compatibility wrapper, not
 * required by new usages of the corresponding common class.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderDefinitions extends ElytronCommonProviderDefinitions {

    static ElytronCommonAggregateComponentDefinition<Provider[]> getAggregateProvidersDefinition() {
        return getAggregateProvidersDefinition(ElytronExtension.class);
    }

    static ResourceDefinition getProviderLoaderDefinition(boolean serverOrHostController) {
        return getProviderLoaderDefinition(ElytronExtension.class, serverOrHostController);
    }
}
