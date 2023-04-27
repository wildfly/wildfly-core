/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.CASE_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronCommonConstants.CONSTANT_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronCommonConstants.REGEX_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronCommonConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.RegexAttributeDefinitions.PATTERN;

import java.security.Principal;
import java.util.regex.Pattern;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.util.CaseNameRewriter;
import org.wildfly.security.auth.util.RegexNameRewriter;
import org.wildfly.security.auth.util.RegexNameValidatingRewriter;

/**
 *
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PrincipalTransformerDefinitions {

    static final SimpleAttributeDefinition CONSTANT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CONSTANT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REPLACEMENT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.REPLACEMENT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(0))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REPLACE_ALL = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.REPLACE_ALL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MATCH = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.MATCH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition UPPER_CASE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.UPPER_CASE, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    static final ElytronCommonAggregateComponentDefinition<PrincipalTransformer> AGGREGATE_PRINCIPAL_TRANSFORMER = ElytronCommonAggregateComponentDefinition.create(PrincipalTransformer.class,
            ElytronCommonConstants.AGGREGATE_PRINCIPAL_TRANSFORMER, ElytronCommonConstants.PRINCIPAL_TRANSFORMERS, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY,
            (PrincipalTransformer[] pt) -> PrincipalTransformer.aggregate(pt));

    static ElytronCommonAggregateComponentDefinition<PrincipalTransformer> getAggregatePrincipalTransformerDefinition() {
        return AGGREGATE_PRINCIPAL_TRANSFORMER;
    }

    static final ElytronCommonAggregateComponentDefinition<PrincipalTransformer> CHAINED_PRINCIPAL_TRANSFORMER = ElytronCommonAggregateComponentDefinition.create(PrincipalTransformer.class,
            ElytronCommonConstants.CHAINED_PRINCIPAL_TRANSFORMER, ElytronCommonConstants.PRINCIPAL_TRANSFORMERS, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY,
            (PrincipalTransformer[] pt) -> PrincipalTransformer.chain(pt));

    static ElytronCommonAggregateComponentDefinition<PrincipalTransformer> getChainedPrincipalTransformerDefinition() {
        return CHAINED_PRINCIPAL_TRANSFORMER;
    }

    static ResourceDefinition getConstantPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { CONSTANT };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {
                final Principal principal = new NamePrincipal(CONSTANT.resolveModelAttribute(context, model).asString());

                return () -> p -> principal;
            }
        };

        return new ElytronCommonTrivialResourceDefinition(CONSTANT_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getRegexPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PATTERN, REPLACEMENT, REPLACE_ALL };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final Pattern pattern     = Pattern.compile(PATTERN.resolveModelAttribute(context, model).asString());
                final String  replacement = REPLACEMENT.resolveModelAttribute(context, model).asString();
                final boolean replaceAll  = REPLACE_ALL.resolveModelAttribute(context, model).asBoolean();

                return () -> PrincipalTransformer.from(new RegexNameRewriter(pattern, replacement, replaceAll).asPrincipalRewriter());
            }
        };

        return new ElytronCommonTrivialResourceDefinition(REGEX_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getRegexValidatingPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PATTERN, MATCH };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {
                final Pattern pattern = Pattern.compile(PATTERN.resolveModelAttribute(context, model).asString());
                final boolean match = MATCH.resolveModelAttribute(context, model).asBoolean();

                return () -> PrincipalTransformer.from(new RegexNameValidatingRewriter(pattern, match).asPrincipalRewriter());
            }
        };

        return new ElytronCommonTrivialResourceDefinition(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getCasePrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {UPPER_CASE};
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                                                                           OperationContext context, ModelNode model) throws OperationFailedException {
                final boolean upperCase = UPPER_CASE.resolveModelAttribute(context, model).asBoolean();
                return () -> PrincipalTransformer.from(new CaseNameRewriter(upperCase).asPrincipalRewriter());
            }
        };
        return new ElytronCommonTrivialResourceDefinition(CASE_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }
}
