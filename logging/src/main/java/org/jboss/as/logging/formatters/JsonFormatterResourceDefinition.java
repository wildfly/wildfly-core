/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.formatters;

import org.jboss.as.controller.PathElement;
import org.jboss.logmanager.formatters.JsonFormatter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonFormatterResourceDefinition extends StructuredFormatterResourceDefinition {
    public static final String NAME = "json-formatter";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    public static final JsonFormatterResourceDefinition INSTANCE = new JsonFormatterResourceDefinition();

    private JsonFormatterResourceDefinition() {
        super(PATH, NAME, JsonFormatter.class);
    }

    public static final class TransformerDefinition extends StructuredFormatterTransformerDefinition {

        public TransformerDefinition() {
            super(PATH);
        }
    }
}
