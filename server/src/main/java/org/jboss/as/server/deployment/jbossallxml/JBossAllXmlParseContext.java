/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.jbossallxml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.DeploymentUnit;

/**
 * Package private class that holds the parsing state
 *
 * @author Stuart Douglas
 */
class JBossAllXmlParseContext {

    private final DeploymentUnit deploymentUnit;

    private final Map<QName, Object> parseResults = new HashMap<QName, Object>();

    public JBossAllXmlParseContext(final DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
    }

    public DeploymentUnit getDeploymentUnit() {
        return deploymentUnit;
    }

    public void addResult(final QName namespace, final Object result, final Location location) throws XMLStreamException {
        if(parseResults.containsKey(namespace)) {
            throw ServerLogger.ROOT_LOGGER.duplicateJBossXmlNamespace(namespace, location);
        }
        parseResults.put(namespace, result);
    }

    public Map<QName, Object> getParseResults() {
        return Collections.unmodifiableMap(parseResults);
    }
}
