/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * A placeholder parser for a subsystem namespace not enabled by the stability level of the current process.
 */
public class UnstableSubsystemNamespaceParser implements XMLElementReader<List<ModelNode>> {
    private final String subsystemName;

    public UnstableSubsystemNamespaceParser(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
        throw ControllerLogger.ROOT_LOGGER.unstableSubsystemNamespace(this.subsystemName, reader.getNamespaceURI());
    }
}
