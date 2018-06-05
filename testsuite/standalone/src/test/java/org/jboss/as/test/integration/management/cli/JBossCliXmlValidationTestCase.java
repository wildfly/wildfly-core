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
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class JBossCliXmlValidationTestCase {

    @Test
    public void validateJBossCliXmlTestCase() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder parser = factory.newDocumentBuilder();
        final String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        Document document = parser.parse(new File(jbossDist, "bin/jboss-cli.xml"));

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setErrorHandler(new ErrorHandlerImpl());
        //schemaFactory.setResourceResolver(new XMLResourceResolver());

        Schema schema = schemaFactory.newSchema(resourceToURL("schema/wildfly-cli_3_5.xsd"));
        Validator validator = schema.newValidator();
        validator.validate(new DOMSource(document));

    }

    protected static final class ErrorHandlerImpl implements ErrorHandler {
        @Override
        public void error(SAXParseException e) throws SAXException {
            fail(formatMessage(e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            fail(formatMessage(e));
        }

        @Override
        public void warning(SAXParseException e) throws SAXException {
            System.out.println(formatMessage(e));
        }

        private String formatMessage(SAXParseException e) {
            StringBuffer sb = new StringBuffer();
            sb.append(e.getLineNumber()).append(':').append(e.getColumnNumber());
            if (e.getPublicId() != null)
                sb.append(" publicId='").append(e.getPublicId()).append('\'');
            if (e.getSystemId() != null)
                sb.append(" systemId='").append(e.getSystemId()).append('\'');
            sb.append(' ').append(e.getLocalizedMessage());
            sb.append(" a possible cause may be that a subsystem is not using the most up to date schema.");
            return sb.toString();
        }
    }

    private URL resourceToURL(final String name) {
        final ClassLoader classLoader = getClass().getClassLoader();
        final URL resource = classLoader.getResource(name);
        assertNotNull("Can't locate resource " + name + " on " + classLoader, resource);
        return resource;
    }

}
