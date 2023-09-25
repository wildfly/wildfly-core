/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger.ROOT_LOGGER;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.wildfly.common.xml.SAXParserFactoryUtil;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Determines if an XML document is well formed, to prevent half copied XML files from being deployed
 *
 * @author Stuart Douglas
 */
public class XmlCompletionScanner {


    public static boolean isCompleteDocument(final File file) throws IOException {
        ErrorHandler handler = new ErrorHandler(file.getName());
        try {
            SAXParserFactory factory = SAXParserFactoryUtil.create();
            factory.setValidating(false);
            final SAXParser parser = factory.newSAXParser();
            parser.parse(file, handler);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            DeploymentScannerLogger.ROOT_LOGGER.debugf(e, "Exception parsing scanned XML document %s", file);
            return false;
        }
        return !handler.error;
    }

    private static class ErrorHandler extends DefaultHandler {

        private boolean error = false;
        private final String fileName;

        public ErrorHandler(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void error(final SAXParseException e) throws SAXException {
            traceError(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            traceError(e);
            throw(e);
        }

        private void traceError(SAXParseException e) {
            error = true;
            ROOT_LOGGER.info(ROOT_LOGGER.invalidXmlFileFound(fileName, e.getLineNumber(), e.getColumnNumber()));
        }
    }
}
