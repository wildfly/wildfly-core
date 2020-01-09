/*
 * JBoss, Home of Professional Open Source
 * Copyright 2019, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.boot.cli.hook;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.HashMap;
import java.util.Map;

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
public class BootCliReloadHandler implements OperationStepHandler {

    public static final BootCliReloadHandler INSTANCE = new BootCliReloadHandler();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("reload", new NonResolvingResourceDescriptionResolver())
            .setRuntimeOnly()
            .build();
    Map<String, ModelNode> parameters;

    private BootCliReloadHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        operation = operation.clone();
        operation.remove(OP);
        operation.remove(OP_ADDR);

        parameters = new HashMap<>();
        for (String key : operation.keys()) {
            parameters.put(key, operation.get(key));
        }
    }
}
