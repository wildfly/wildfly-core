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

package org.jboss.as.domain.management.parsing;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.Namespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Bits of parsing and marshaling logic that are related to {@code <management>} elements in domain.xml, host.xml and
 * standalone.xml.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ManagementXml {

    static ManagementXml newInstance(Namespace namespace, ManagementXmlDelegate delegate, boolean domainConfiguration) {
        switch (namespace.getMajorVersion()) {
            case 1:
            case 2:
            case 3:
                return new ManagementXml_Legacy(namespace, delegate, domainConfiguration);
            case 4:
                return new ManagementXml_4(namespace, delegate, domainConfiguration);
            default:
                return new ManagementXml_5(namespace, delegate, domainConfiguration);
        }
    }

    // TODO - The problem is that it is version dependent but it would be nicer to not have processing instructions and find a
    // better way to decide if a native interface is required.

    void parseManagement(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list, boolean requireNativeInterface) throws XMLStreamException;

    // TODO - Writing should be based on what is actually in the model, the delegate should be the final check if interfaces are
    // really disallowed.

    default void writeManagement(XMLExtendedStreamWriter writer, ModelNode management, boolean allowInterfaces) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }
}
