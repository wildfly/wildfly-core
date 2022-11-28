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
