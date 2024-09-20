/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.parsing;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.ManagementXmlReaderWriter;
import org.jboss.as.controller.parsing.ManagementXmlSchema;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code host.xml}
 *
 * @author Brian Stansberry
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:jperkins@jboss.com">James R. Perkins</a>
 */
public final class HostXml implements ManagementXmlReaderWriter {

    private final String defaultHostControllerName;
    private final RunningMode runningMode;
    private final boolean isCachedDc;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionXml extensionXml;

    public HostXml(String defaultHostControllerName, RunningMode runningMode, boolean isCachedDC, final ModuleLoader loader,
                   final ExecutorService executorService, final ExtensionRegistry extensionRegistry) {
        this.defaultHostControllerName = defaultHostControllerName;
        this.runningMode = runningMode;
        this.isCachedDc = isCachedDC;
        this.extensionRegistry = extensionRegistry;
        extensionXml = new ExtensionXml(loader, executorService, extensionRegistry);
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final VersionedNamespace<IntVersion, ManagementXmlSchema> namespace, final List<ModelNode> operationList)
            throws XMLStreamException {

        final IntVersion version = namespace.getVersion();
        final String namespaceUri = namespace.getUri();

        switch (version.major()) {
            case 1:
            case 2:
            case 3:
                new HostXml_Legacy(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 4:
                new HostXml_4(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 5:
                new HostXml_5(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 6:
            case 7:
            case 8:
            case 9:
                new HostXml_6(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 10:
                new HostXml_10(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                new HostXml_11(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            case 18:
            case 19:
                new HostXml_18(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, version, namespaceUri).readElement(reader, operationList);
                break;
            default:
                new HostXml_20(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, namespace).readElement(reader, operationList);
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final VersionedNamespace<IntVersion, ManagementXmlSchema> namespace, final ModelMarshallingContext context)
            throws XMLStreamException {

        new HostXml_20(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, namespace).writeContent(writer, context);
    }

}
