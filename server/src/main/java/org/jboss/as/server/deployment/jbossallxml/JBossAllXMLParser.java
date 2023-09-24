/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.jbossallxml;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser class that should be implemented by JBoss XML element parsers to parse their deployment descriptor data.
 *
 * @author Stuart Douglas
 */
public interface JBossAllXMLParser<T> {

    T parse(final XMLExtendedStreamReader reader, final DeploymentUnit deploymentUnit) throws XMLStreamException;
}
