/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                new DomainXml_5(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
                break;
            case 16:
            case 17:
            case 18:
            case 19:
            default:
                new DomainXml_16(extensionXml, extensionRegistry, readerNS).readElement(reader, nodes);
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context) throws XMLStreamException {
        new DomainXml_16(extensionXml, extensionRegistry, CURRENT).writeContent(writer, context);
    }

}
