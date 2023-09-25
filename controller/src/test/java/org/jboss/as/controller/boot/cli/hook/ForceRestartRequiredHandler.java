/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.boot.cli.hook;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ForceRestartRequiredHandler implements OperationStepHandler {
    static final String NAME = "force-reload-required";
    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(NAME, NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .setRuntimeOnly()
            .build();

    static final ForceRestartRequiredHandler INSTANCE = new ForceRestartRequiredHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.restartRequired();
    }
}
