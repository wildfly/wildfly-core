/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.deployment.module;

import java.util.function.Function;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

/**
 * An XML Stream reader delegate adding methods that use a {@link Function} to resolve values retrieved by getElementTexts.
 *
 * @author Yeray Borges
 */
public class ExpressionStreamReaderDelegate extends StreamReaderDelegate {
    private final Function<String, String> exprExpandFunction;

    /**
     * @param reader             The {@link XMLStreamReader} for the XML file
     * @param exprExpandFunction A function which will be used, if provided, to expand any expressions (of the form of {@code ${foobar}})
     *                           in the content being parsed. This function can be null, in which case the content is processed literally.
     */
    public ExpressionStreamReaderDelegate(XMLStreamReader reader, final Function<String, String> exprExpandFunction) {
        super(reader);
        this.exprExpandFunction = exprExpandFunction;
    }

    public String getElementText() throws XMLStreamException {
        String elementText = super.getElementText();
        return expand(elementText);
    }

    private String expand(final String content) {
        if (content == null || content.isEmpty() || exprExpandFunction == null) {
            return content;
        }
        return exprExpandFunction.apply(content);
    }
}
