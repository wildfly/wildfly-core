/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition.SUFFIX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler;

/**
 * Resource for a {@link org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicSizeRotatingHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "periodic-size-rotating-file-handler";
    private static final PathElement PERIODIC_SIZE_ROTATING_HANDLER_PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, MAX_BACKUP_INDEX, ROTATE_SIZE, ROTATE_ON_BOOT, SUFFIX, NAMED_FORMATTER, FILE);

    public PeriodicSizeRotatingHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler) {
        super(PERIODIC_SIZE_ROTATING_HANDLER_PATH, false, PeriodicSizeRotatingFileHandler.class, resolvePathHandler, diskUsagePathHandler, ATTRIBUTES);
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion,
                                         final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                         final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_2_0_0: {
                    rootResourceBuilder.rejectChildResource(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
                    loggingProfileBuilder.rejectChildResource(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
                    break;
                }
            }
        }
    }

}
