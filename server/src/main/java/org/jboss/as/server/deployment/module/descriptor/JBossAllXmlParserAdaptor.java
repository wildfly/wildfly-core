/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module.descriptor;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXMLParser;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Adaptor between {@link XMLElementReader} and {@link JBossAllXMLParser}
 *
 * @author Stuart Douglas
 */
public class JBossAllXmlParserAdaptor implements JBossAllXMLParser<ParseResult> {

    private final XMLElementReader<ParseResult> elementReader;

    public JBossAllXmlParserAdaptor(final XMLElementReader<ParseResult> elementReader) {
        this.elementReader = elementReader;
    }

    @Override
    public ParseResult parse(final XMLExtendedStreamReader reader, final DeploymentUnit deploymentUnit) throws XMLStreamException {
        final ParseResult result = new ParseResult(deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER), deploymentUnit);
        elementReader.readElement(reader, result);
        return result;

    }
}
