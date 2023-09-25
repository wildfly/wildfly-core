/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.formatters;

import org.jboss.as.controller.PathElement;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.formatters.XmlFormatter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class XmlFormatterResourceDefinition extends StructuredFormatterResourceDefinition {
    public static final String NAME = "xml-formatter";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition PRINT_NAMESPACE = PropertyAttributeDefinition.Builder.of("print-namespace", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setPropertyName("printNamespace")
            .build();

    public static final PropertyAttributeDefinition NAMESPACE_URI = PropertyAttributeDefinition.Builder.of("namespace-uri", ModelType.STRING, true)
            .setAllowExpression(true)
            .setPropertyName("namespaceUri")
            .build();

    public static final XmlFormatterResourceDefinition INSTANCE = new XmlFormatterResourceDefinition();

    private XmlFormatterResourceDefinition() {
        super(PATH, NAME, XmlFormatter.class, PRINT_NAMESPACE, NAMESPACE_URI);
    }

    public static final class TransformerDefinition extends StructuredFormatterTransformerDefinition {

        public TransformerDefinition() {
            super(PATH);
        }
    }
}
