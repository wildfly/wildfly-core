/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
