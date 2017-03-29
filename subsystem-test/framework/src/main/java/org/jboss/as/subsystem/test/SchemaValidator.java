/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.subsystem.test;

import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ExpressionResolverImpl;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.subsystem.test.xml.JBossEntityResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XML Schema Validator.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public class SchemaValidator {

    private static ErrorHandler ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            fail("warning: " + exception.getMessage());

        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            fail("error: " + exception.getMessage());

        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            fail("fatal error: " + exception.getMessage());
        }
    };

    /**
     * Validate subtrees of the XML content against the XSD.
     *
     * Only subtrees starting from the given elementRoot will be validated against the schema.
     */
    static void validateXML(String xmlContent, String elementRoot, String xsdPath, Properties resolvedProperties) throws Exception {
        // build an input source from the XML content
        InputSource inputSource = new InputSource(new StringReader(xmlContent));
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputSource);

        // validated only the nodes corresponding to the given elementRoot
        NodeList nodes = document.getElementsByTagName(elementRoot);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            // use a transformer to the the String result of the node tree
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            SchemaValidator.validateXML(writer.toString(), xsdPath, resolvedProperties);
        }
    }

    /**
     * Validate the XML content against the XSD.
     *
     * The whole XML content must be valid.
     */
    static void validateXML(String xmlContent, String xsdPath, Properties resolvedProperties) throws Exception {
        String resolvedXml = resolveAllExpressions(xmlContent, resolvedProperties);

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(xsdPath);
        final Source source = new StreamSource(stream);

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setErrorHandler(ERROR_HANDLER);
        schemaFactory.setResourceResolver(new JBossEntityResolver());
        Schema schema = schemaFactory.newSchema(source);
        javax.xml.validation.Validator validator = schema.newValidator();
        validator.setErrorHandler(ERROR_HANDLER);
        validator.setFeature("http://apache.org/xml/features/validation/schema", true);
        validator.validate(new StreamSource(new StringReader(resolvedXml)));
    }

    /**
     * Subsystem XML can contain expressions for simple XSD types (boolean, long, etc.) that
     * prevents to validate it against the schema.
     *
     * For XML validation, the XML is read and any expression is resolved (they must have a default value to
     * be properly resolved).
     */
    private static String resolveAllExpressions(String xmlContent, Properties resolvedProperties) throws IOException {
        // We hack a bit here and use a variant of the management model expression resolver
        // to resolve expressions in the xml. XML strings aren't DMR model nodes but
        // pretending they are seems to work well enough.
        ExpressionResolver replacer = new Resolver(resolvedProperties);
        StringBuilder out = new StringBuilder();

        try( BufferedReader reader = new BufferedReader(new StringReader(xmlContent)) ) {
            String line;
            while ((line = reader.readLine()) != null) {
                String resolved = line;
                if (ExpressionResolver.EXPRESSION_PATTERN.matcher(line).matches()) {
                    ModelNode input = new ModelNode(new ValueExpression(line));
                    try {
                        resolved = replacer.resolveExpressions(input).asString();
                    } catch (OperationFailedException e) {
                        // ignore, output the original line and see what happens ;)
                    }
                }
                out.append(resolved);
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static class Resolver extends ExpressionResolverImpl {
        private final Properties properties;

        private Resolver(Properties properties) {
            super(true);
            this.properties = properties == null ? new Properties() : properties;
        }

        @Override
        protected void resolvePluggableExpression(ModelNode node) throws OperationFailedException {
            String expression = node.asString();
            if (expression.startsWith("${") && expression.endsWith("}")) {
                int colon = expression.indexOf(':');
                int end = colon < 0 ? expression.length() - 1 : colon;
                String key = expression.substring(2, end);
                String value = properties.getProperty(key);
                if (value != null) {
                    node.set(value);
                } else if (colon > 0) {
                    node.set(expression.substring(colon + 1, expression.length() - 1));
                } // else let it go
            }
        }
    }
}
