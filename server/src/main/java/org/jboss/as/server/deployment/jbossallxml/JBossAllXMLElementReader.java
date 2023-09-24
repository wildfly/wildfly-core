/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.jbossallxml;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 *
 * {@link XMLElementReader} that delegates to a {@link JBossAllXMLParser}
 *
 *
 * @author Stuart Douglas
 */
class JBossAllXMLElementReader implements XMLElementReader<JBossAllXmlParseContext> {

    private final JBossAllXMLParserDescription parserDescription;

    JBossAllXMLElementReader(final JBossAllXMLParserDescription<?> parserDescription) {
        this.parserDescription = parserDescription;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader xmlExtendedStreamReader, final JBossAllXmlParseContext jBossXmlParseContext) throws XMLStreamException {
        final Location nsLocation = xmlExtendedStreamReader.getLocation();
        final QName elementName = xmlExtendedStreamReader.getName();
        final Object result = parserDescription.getParser().parse(xmlExtendedStreamReader, jBossXmlParseContext.getDeploymentUnit());
        jBossXmlParseContext.addResult(elementName, result, nsLocation);
    }
}
