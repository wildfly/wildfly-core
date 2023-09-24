/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.module;

import static org.wildfly.common.Assert.checkNotNullParam;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.cli.handlers.module.ModuleConfig.Dependency;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 *
 * @author Alexey Loubyansky
 */
public class ModuleDependency implements Dependency {

    private final String name;
    private final boolean export;

    public ModuleDependency(String name) {
        this(name, false);
    }

    public ModuleDependency(String name, boolean export) {
        this.name = checkNotNullParam("name", name);
        this.export = export;
    }

    /* (non-Javadoc)
     * @see org.jboss.staxmapper.XMLElementWriter#writeContent(org.jboss.staxmapper.XMLExtendedStreamWriter, java.lang.Object)
     */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, Dependency value) throws XMLStreamException {

        if(value != null && this != value) {
            throw new IllegalStateException("Wrong target dependency.");
        }

        writer.writeStartElement(ModuleConfigImpl.MODULE);
        writer.writeAttribute(ModuleConfigImpl.NAME, name);
        if (export) {
            writer.writeAttribute(ModuleConfigImpl.EXPORT, "true");
        }
        writer.writeEndElement();
    }
}
