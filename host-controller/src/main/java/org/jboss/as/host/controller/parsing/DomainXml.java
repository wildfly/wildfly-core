/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.host.controller.parsing;

import static org.jboss.as.controller.parsing.Namespace.CURRENT;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainXml implements XMLElementReader<List<ModelNode>>, XMLElementWriter<ModelMarshallingContext> {

    private final ExtensionXml extensionXml;
    private final ExtensionRegistry extensionRegistry;

    public DomainXml(final ModuleLoader loader, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        extensionXml = new ExtensionXml(loader, executorService, extensionRegistry);
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> nodes) throws XMLStreamException {
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        switch (readerNS.getMajorVersion()) {
            case 1:
            case 2:
            case 3:
                new DomainXml_Legacy(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 4:
                new DomainXml_4(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 5:
                new DomainXml_5(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 6:
                new DomainXml_6(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 7:
                new DomainXml_7(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 8:
                new DomainXml_8(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 9:
                new DomainXml_9(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 10:
                new DomainXml_10(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 11:
                new DomainXml_11(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            default:
                new DomainXml_12(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context) throws XMLStreamException {
        new DomainXml_12(extensionXml, extensionRegistry, CURRENT).writeContent(writer, context);
    }

}
