/*
 * Copyright 2017 Red Hat, Inc.
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

package org.wildfly.test.security.common.elytron;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Interface representing a configurable object in domain model. The implementation has to override at least one of the
 * {@code create(...)} methods and one of the {@code remove(...)} methods.
 *
 * @author Josef Cacek
 */
public interface ConfigurableElement {

    /**
     * Returns name of this element.
     */
    String getName();

    /**
     * Creates this element in domain model and it also may create other resources if needed (e.g. external files).
     * Implementation can choose if controller client is used or provided CLI wrapper.
     */
    void create(ModelControllerClient client, CLIWrapper cli) throws Exception;

    /**
     * Reverts the changes introdued by {@code create(...)} method(s).
     */
    void remove(ModelControllerClient client, CLIWrapper cli) throws Exception;
}
