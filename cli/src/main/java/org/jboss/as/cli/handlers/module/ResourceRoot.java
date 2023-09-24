/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.module;

import static org.wildfly.common.Assert.checkNotNullParam;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.cli.handlers.module.ModuleConfig.Resource;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 *
 * @author Alexey Loubyansky
 */
public class ResourceRoot implements Resource {

    private final String path;

    public ResourceRoot(String path) {
        this.path = checkNotNullParam("path", path);
    }

    /* (non-Javadoc)
     * @see org.jboss.staxmapper.XMLElementWriter#writeContent(org.jboss.staxmapper.XMLExtendedStreamWriter, java.lang.Object)
     */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, Resource value) throws XMLStreamException {

        if(value != null && this != value) {
            throw new IllegalStateException("Wrong target resource.");
        }

        writer.writeStartElement(ModuleConfigImpl.RESOURCE_ROOT);
        writer.writeAttribute(ModuleConfigImpl.PATH, path);
        writer.writeEndElement();
    }
}
