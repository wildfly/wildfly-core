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
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public interface AuditLogXml {
    static AuditLogXml newInstance(Namespace namespace, boolean host) {
        switch (namespace.getMajorVersion()) {
            case 1:
            case 2:
            case 3:
                return new AuditLogXml_Legacy(host);
            case 4:
                return new AuditLogXml_4(host);
            default:
                return new AuditLogXml_5(host);
        }
    }

    // TODO - The problem is that it is version dependent but it would be nicer to not have processing instructions and find a
    // better way to decide if a native interface is required.

    void parseAuditLog(XMLExtendedStreamReader reader, ModelNode address, Namespace expectedNs, List<ModelNode> list) throws XMLStreamException;

    // TODO - Writing should be based on what is actually in the model, the delegate should be the final check if interfaces are
    // really disallowed.

    default void writeAuditLog(XMLExtendedStreamWriter writer, ModelNode auditLog) throws XMLStreamException  {
        throw new UnsupportedOperationException();
    }

}
