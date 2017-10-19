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
package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * The handler to enable booting a host controller with an empty host config
 * but still allow ops to be routed as if we were a DC.
 * localHostControllerInfoImpl.setMasterDomainController(true) is package protected
 * from DomainModelControllerService, where we'd really want to get at this from, so
 * we use an OP. This should only be enabled when a host is added using /host=foo:add()
 * and reloaded, after the reload it should no longer be in effect.
 *
 * @author <a href="mailto:kwills@redhat.com">Ken Wills</a>
 */

public class EnableLocalDomainControllerRoutingHandler implements OperationStepHandler {

    // this is used when booting an empty host configuration, when no host has been added.
    // without this, the operation routing would be denied by determineRouting().
    public static final String OPERATION_NAME = "enable-local-domain-controller-routing";

    //Private method does not need resources for description
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, null)
            .setPrivateEntry()
            .build();

    private final LocalHostControllerInfoImpl localHostControllerInfoImpl;

    public EnableLocalDomainControllerRoutingHandler(final LocalHostControllerInfoImpl localHostControllerInfoImpl) {
        this.localHostControllerInfoImpl = localHostControllerInfoImpl;
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) {
        localHostControllerInfoImpl.setMasterDomainController(true);
    }
}
