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

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code host.xml}
 *
 * @author Brian Stansberry
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:jperkins@jboss.com">James R. Perkins</a>
 */
public class HostXml extends CommonXml {

    private final String defaultHostControllerName;
    private final RunningMode runningMode;
    private final boolean isCachedDc;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionXml extensionXml;

    public HostXml(String defaultHostControllerName, RunningMode runningMode, boolean isCachedDC, final ModuleLoader loader,
            final ExecutorService executorService, final ExtensionRegistry extensionRegistry) {
        super(null);
        this.defaultHostControllerName = defaultHostControllerName;
        this.runningMode = runningMode;
        this.isCachedDc = isCachedDC;
        this.extensionRegistry = extensionRegistry;
        extensionXml = new ExtensionXml(loader, executorService, extensionRegistry);
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> operationList)
            throws XMLStreamException {
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        switch (readerNS.getMajorVersion()) {
            case 1:
            case 2:
            case 3:
                new HostXml_Legacy(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, readerNS).readElement(reader, operationList);
                break;
            default:
                new HostXml_4(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, readerNS).readElement(reader, operationList);
                break;
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context)
            throws XMLStreamException {
        new HostXml_4(defaultHostControllerName, runningMode, isCachedDc, extensionRegistry, extensionXml, CURRENT).writeContent(writer, context);
    }

}
