/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
