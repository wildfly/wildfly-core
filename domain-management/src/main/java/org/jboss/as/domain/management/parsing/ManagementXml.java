/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
            case 16:
            case 17:
                return new ManagementXml_5(namespace, delegate, domainConfiguration);
            case 18:
            case 19:
            case 20:
                return new ManagementXml_18(namespace, delegate, domainConfiguration);
            default:
                return new ManagementXml_21(namespace, delegate, domainConfiguration);
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
