/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
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

package org.jboss.as.test.manualmode.vault.module;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestVaultResolveExpressionHandler implements OperationStepHandler {

    public static final OperationDefinition RESOLVE = new SimpleOperationDefinitionBuilder("test", new NonResolvingResourceDescriptionResolver())
        .addParameter(TestVaultResolveExpressionHandler.PARAM_EXPRESSION)
        .build();

    public static final AttributeDefinition PARAM_EXPRESSION = SimpleAttributeDefinitionBuilder.create("expression", ModelType.STRING, false)
        .setAllowExpression(true)
        .build();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        TestVaultResolveExpressionHandler.PARAM_EXPRESSION.validateOperation(operation);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation)
                    throws OperationFailedException {
                context.getResult().set(context.resolveExpressions(operation.get(TestVaultResolveExpressionHandler.PARAM_EXPRESSION.getName())));
            }
        }, Stage.RUNTIME);
    }

}
