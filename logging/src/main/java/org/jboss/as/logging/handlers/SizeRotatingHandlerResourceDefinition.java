/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.FILE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.resolvers.SizeResolver;
import org.jboss.as.logging.validators.SizeValidator;
import org.jboss.as.logging.validators.SuffixValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SizeRotatingHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "size-rotating-file-handler";
    private static final PathElement SIZE_ROTATING_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition MAX_BACKUP_INDEX = PropertyAttributeDefinition.Builder.of("max-backup-index", ModelType.INT, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setRequired(false)
            .setDefaultValue(new ModelNode(1))
            .setPropertyName("maxBackupIndex")
            .setValidator(new IntRangeValidator(1, true))
            .build();

    public static final PropertyAttributeDefinition ROTATE_ON_BOOT = PropertyAttributeDefinition.Builder.of("rotate-on-boot", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setPropertyName("rotateOnBoot")
            .build();

    public static final PropertyAttributeDefinition ROTATE_SIZE = PropertyAttributeDefinition.Builder.of("rotate-size", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("2m"))
            .setPropertyName("rotateSize")
            .setResolver(SizeResolver.INSTANCE)
            .setValidator(new SizeValidator())
            .build();

    public static final PropertyAttributeDefinition SUFFIX = PropertyAttributeDefinition.Builder.of("suffix", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(new SuffixValidator(true, false))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, MAX_BACKUP_INDEX, ROTATE_SIZE, ROTATE_ON_BOOT, NAMED_FORMATTER, FILE, SUFFIX);

    public SizeRotatingHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final boolean includeLegacyAttributes) {
        this(resolvePathHandler, null, includeLegacyAttributes);
    }

    public SizeRotatingHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler, final boolean includeLegacyAttributes) {
        super(SIZE_ROTATING_HANDLER_PATH, SizeRotatingFileHandler.class, resolvePathHandler, diskUsagePathHandler,
                (includeLegacyAttributes ? Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES) : ATTRIBUTES));
    }

    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(SIZE_ROTATING_HANDLER_PATH);
        }

        @Override
        void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_2_0_0: {
                    resourceBuilder
                            .getAttributeBuilder()
                            .setDiscard(DiscardAttributeChecker.UNDEFINED, SUFFIX)
                            .addRejectCheck(RejectAttributeChecker.DEFINED, SUFFIX)
                            .end();
                    if (loggingProfileBuilder != null) {
                        loggingProfileBuilder
                                .getAttributeBuilder()
                                .setDiscard(DiscardAttributeChecker.UNDEFINED, SUFFIX)
                                .addRejectCheck(RejectAttributeChecker.DEFINED, SUFFIX)
                                .end();
                    }
                    break;
                }
            }

        }
    }

}
