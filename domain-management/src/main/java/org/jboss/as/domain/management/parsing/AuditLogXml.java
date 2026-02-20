/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.parsing;

import java.util.List;
import javax.xml.stream.XMLStreamException;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public interface AuditLogXml {
    static AuditLogXml newInstance(IntVersion version, boolean host) {
        return newInstance(version, host, Stability.DEFAULT);
    }

    static AuditLogXml newInstance(IntVersion version, boolean host, Stability stability) {
        switch (version.major()) {
            case 1:
            case 2:
            case 3:
                return new AuditLogXml_Legacy(host);
            case 4:
                return new AuditLogXml_4(host);
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
            case 18:
            case 19:
            case 20:
                 return new AuditLogXml_5(host);
            default:
                return new AuditLogXml_6(host, stability);
        }
    }

    // TODO - The problem is that it is version dependent but it would be nicer to not have processing instructions and find a
    // better way to decide if a native interface is required.

    void parseAuditLog(XMLExtendedStreamReader reader, ModelNode address, String expectedNs, List<ModelNode> list) throws XMLStreamException;

    // TODO - Writing should be based on what is actually in the model, the delegate should be the final check if interfaces are
    // really disallowed.

    default void writeAuditLog(XMLExtendedStreamWriter writer, ModelNode auditLog) throws XMLStreamException  {
        throw new UnsupportedOperationException();
    }

}
