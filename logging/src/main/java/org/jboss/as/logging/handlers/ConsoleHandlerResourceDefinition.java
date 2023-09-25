/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker.SimpleRejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.resolvers.TargetResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ConsoleHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String NAME = "console-handler";
    private static final PathElement CONSOLE_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition TARGET = PropertyAttributeDefinition.Builder.of("target", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(Target.SYSTEM_OUT.toString()))
            .setResolver(TargetResolver.INSTANCE)
            .setValidator(EnumValidator.create(Target.class))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, TARGET, NAMED_FORMATTER);

    public ConsoleHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(CONSOLE_HANDLER_PATH, ConsoleHandler.class,
                (includeLegacyAttributes ? Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES) : ATTRIBUTES));
    }


    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(CONSOLE_HANDLER_PATH);
        }

        @Override
        void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_1_5_0:
                case VERSION_2_0_0: {
                    resourceBuilder
                            .getAttributeBuilder()
                            .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, TARGET)
                            .addRejectCheck(new SimpleRejectAttributeChecker(new ModelNode(Target.CONSOLE.toString())), TARGET)
                            .end();
                    loggingProfileBuilder
                            .getAttributeBuilder()
                            .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, TARGET)
                            .addRejectCheck(new SimpleRejectAttributeChecker(new ModelNode(Target.CONSOLE.toString())), TARGET)
                            .end();
                    break;
                }
            }
        }
    }
}
