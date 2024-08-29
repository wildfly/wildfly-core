/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.parsing;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.ManagementXmlReaderWriter;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainXml implements ManagementXmlReaderWriter {

    private final ExtensionXml extensionXml;
    private final ExtensionRegistry extensionRegistry;

    public DomainXml(final ModuleLoader loader, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        extensionXml = new ExtensionXml(loader, executorService, extensionRegistry);
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final IntVersion version, final String namespaceUri, final List<ModelNode> nodes) throws XMLStreamException {
        switch (version.major()) {
            case 1:
            case 2:
            case 3:
                new DomainXml_Legacy(extensionXml, extensionRegistry, version, namespaceUri).readElement(reader, nodes);
                break;
            case 4:
                new DomainXml_4(extensionXml, extensionRegistry, version, namespaceUri).readElement(reader, nodes);
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
                new DomainXml_5(extensionXml, extensionRegistry, version, namespaceUri).readElement(reader, nodes);
                break;
            default:
                new DomainXml_16(extensionXml, extensionRegistry, version, namespaceUri).readElement(reader, nodes);
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final IntVersion version, final String namespaceUri, final ModelMarshallingContext context) throws XMLStreamException {
        new DomainXml_16(extensionXml, extensionRegistry, version, namespaceUri).writeContent(writer, context);
    }

}
