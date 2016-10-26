/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.handlers.module;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModuleConfigTestCase {

    private static final String MODULE_1_1_SCHEMA = "/schema/module-1_1.xsd";

    static final ErrorHandler ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(final SAXParseException exception) throws SAXException {
            Assert.fail("warning: " + exception.getMessage());
        }

        @Override
        public void error(final SAXParseException exception) throws SAXException {
            Assert.fail("error: " + exception.getMessage());
        }

        @Override
        public void fatalError(final SAXParseException exception) throws SAXException {
            Assert.fail("fatal: " + exception.getMessage());
        }
    };

    @Test
    public void validateGeneratedModuleXml() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Create a dummy module
        final ModuleConfigImpl moduleConfig = new ModuleConfigImpl("org.jboss.as.cli.test.module");
        moduleConfig.setMainClass("org.jboss.as.cli.test.Main");
        moduleConfig.addDependency(new ModuleDependency("org.jboss.logging"));
        moduleConfig.addDependency(new ModuleDependency("org.jboss.foo", true));
        moduleConfig.addResource(new ResourceRoot("fake.jar"));
        moduleConfig.setProperty("test.property", "value");
        moduleConfig.setSlot("other");

        final FormattingXMLStreamWriter writer = new FormattingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(out));

        try {
            moduleConfig.writeContent(writer, moduleConfig);
            writer.flush();
        } finally {
            writer.close();
        }

        validateXmlSchema(getClass().getResource(MODULE_1_1_SCHEMA), new ByteArrayInputStream(out.toByteArray()));
    }

    private void validateXmlSchema(final URL schemaUrl, final InputStream data) throws IOException, SAXException {
        final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setErrorHandler(ERROR_HANDLER);

        final Schema schema = schemaFactory.newSchema(schemaUrl);
        final Validator validator = schema.newValidator();
        validator.validate(new StreamSource(data));
    }
}
