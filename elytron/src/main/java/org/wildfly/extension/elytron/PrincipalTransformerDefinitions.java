/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CASE_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REGEX_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER;
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

    static final SimpleAttributeDefinition CONSTANT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONSTANT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REPLACEMENT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REPLACEMENT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(0))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REPLACE_ALL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REPLACE_ALL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MATCH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition UPPER_CASE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.UPPER_CASE, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    static final AggregateComponentDefinition<PrincipalTransformer> AGGREGATE_PRINCIPAL_TRANSFORMER = AggregateComponentDefinition.create(PrincipalTransformer.class,
            ElytronDescriptionConstants.AGGREGATE_PRINCIPAL_TRANSFORMER, ElytronDescriptionConstants.PRINCIPAL_TRANSFORMERS, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY,
            (PrincipalTransformer[] pt) -> PrincipalTransformer.aggregate(pt));

    static AggregateComponentDefinition<PrincipalTransformer> getAggregatePrincipalTransformerDefinition() {
        return AGGREGATE_PRINCIPAL_TRANSFORMER;
    }

    static final AggregateComponentDefinition<PrincipalTransformer> CHAINED_PRINCIPAL_TRANSFORMER = AggregateComponentDefinition.create(PrincipalTransformer.class,
            ElytronDescriptionConstants.CHAINED_PRINCIPAL_TRANSFORMER, ElytronDescriptionConstants.PRINCIPAL_TRANSFORMERS, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY,
            (PrincipalTransformer[] pt) -> PrincipalTransformer.chain(pt));

    static AggregateComponentDefinition<PrincipalTransformer> getChainedPrincipalTransformerDefinition() {
        return CHAINED_PRINCIPAL_TRANSFORMER;
    }

    static ResourceDefinition getConstantPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { CONSTANT };
        AbstractAddStepHandler add = new TrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {
                final Principal principal = new NamePrincipal(CONSTANT.resolveModelAttribute(context, model).asString());

                return () -> p -> principal;
            }
        };

        return new TrivialResourceDefinition(CONSTANT_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getRegexPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PATTERN, REPLACEMENT, REPLACE_ALL };
        AbstractAddStepHandler add = new TrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final Pattern pattern     = Pattern.compile(PATTERN.resolveModelAttribute(context, model).asString());
                final String  replacement = REPLACEMENT.resolveModelAttribute(context, model).asString();
                final boolean replaceAll  = REPLACE_ALL.resolveModelAttribute(context, model).asBoolean();

                return () -> PrincipalTransformer.from(new RegexNameRewriter(pattern, replacement, replaceAll).asPrincipalRewriter());
            }
        };

        return new TrivialResourceDefinition(REGEX_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getRegexValidatingPrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PATTERN, MATCH };
        AbstractAddStepHandler add = new TrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {
                final Pattern pattern = Pattern.compile(PATTERN.resolveModelAttribute(context, model).asString());
                final boolean match = MATCH.resolveModelAttribute(context, model).asBoolean();

                return () -> PrincipalTransformer.from(new RegexNameValidatingRewriter(pattern, match).asPrincipalRewriter());
            }
        };

        return new TrivialResourceDefinition(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getCasePrincipalTransformerDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {UPPER_CASE};
        AbstractAddStepHandler add = new TrivialAddHandler<PrincipalTransformer>(PrincipalTransformer.class, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PrincipalTransformer> getValueSupplier(ServiceBuilder<PrincipalTransformer> serviceBuilder,
                                                                           OperationContext context, ModelNode model) throws OperationFailedException {
                final boolean upperCase = UPPER_CASE.resolveModelAttribute(context, model).asBoolean();
                return () -> PrincipalTransformer.from(new CaseNameRewriter(upperCase).asPrincipalRewriter());
            }
        };
        return new TrivialResourceDefinition(CASE_PRINCIPAL_TRANSFORMER, add, attributes, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
    }
}
