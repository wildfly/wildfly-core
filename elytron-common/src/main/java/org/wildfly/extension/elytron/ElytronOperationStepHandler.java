/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * An {@link OperationStepHandler} which adds the ability to check whether or not the operation is running on a server
 * or host controller.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface ElytronOperationStepHandler extends OperationStepHandler {

    /**
     * Checks if the context is running on a {@linkplain ProcessType#isServer() server} or on a host controller. This
     * will return {@code true} even if the server is running in {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY}.
     *
     * @param context the current operation context
     *
     * @return {@code true} if the current context is a server or a host controller
     */
    default boolean isServerOrHostController(final OperationContext context) {
        return context.getProcessType().isServer() || !ModelDescriptionConstants.PROFILE.equals(context.getCurrentAddress().getElement(0).getKey());
    }
}
