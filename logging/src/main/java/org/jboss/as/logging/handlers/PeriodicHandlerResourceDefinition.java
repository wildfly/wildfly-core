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
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.validators.SuffixValidator;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.PeriodicRotatingFileHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "periodic-rotating-file-handler";
    private static final PathElement PERIODIC_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition SUFFIX = PropertyAttributeDefinition.Builder.of("suffix", ModelType.STRING)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(new SuffixValidator())
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, FILE, SUFFIX, NAMED_FORMATTER);

    public PeriodicHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final boolean includeLegacyAttributes) {
        this(resolvePathHandler, null, includeLegacyAttributes);
    }

    public PeriodicHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler, final boolean includeLegacyAttributes) {
        super(PERIODIC_HANDLER_PATH, PeriodicRotatingFileHandler.class, resolvePathHandler, diskUsagePathHandler,
                (includeLegacyAttributes ? Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES) : ATTRIBUTES));
    }

    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(PERIODIC_HANDLER_PATH);
        }
    }
}
