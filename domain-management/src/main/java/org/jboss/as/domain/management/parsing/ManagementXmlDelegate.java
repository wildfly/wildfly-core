/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.parsing.Element;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * The management element within the configurations has different constraints applied depending on whether it is for a
 * standalone server or a host or domain, the different parsers can provide an implementation of this interface to override the
 * specific behaviour.
 *
 * This interface consists of default methods so an implementation of the interface needs to only implement the methods of
 * interest.
 *
 * The general pattern for the parse methods is that they return a boolean to indicate if an element has been handled, if it has
 * not been handled the default parsing can occur. If an element is not supported the method should be implemented to throw a
 * {@link XMLStreamException}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface ManagementXmlDelegate {

    /**
     * Parse {@link Element#SECURITY_REALMS} content.
     * <p>
     * This default implementation does standard parsing; override to disable.
     * </p>
     *
     * @param reader the xml reader
     * @param address the address of the parent resource for any added resources
     * @param expectedNs the expected namespace for any children
     * @param operationsList list to which any operations should be added
     * @throws XMLStreamException
     */
    default boolean parseSecurityRealms(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList) throws XMLStreamException {
        return false;
    }

    /**
     * Parse {@link Element#OUTBOUND_CONNECTIONS} content.
     * <p>
     * This default implementation does standard parsing; override to disable.
     * </p>
     *
     * @param reader the xml reader
     * @param address the address of the parent resource for any added resources
     * @param expectedNs the expected namespace for any children
     * @param operationsList list to which any operations should be added
     * @throws XMLStreamException
     */
    default boolean parseOutboundConnections(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList) throws XMLStreamException {
        return false;
    }

    /**
     * Parse {@link Element#MANAGEMENT_INTERFACES} content.
     * <p>This default implementation throws {@code UnsupportedOperationException}; override to support.</p>
     *
     * @param reader the xml reader
     * @param address the address of the parent resource for any added resources
     * @param expectedNs the expected namespace for any children
     * @param operationsList list to which any operations should be added
     * @throws XMLStreamException
     */
    default boolean parseManagementInterfaces(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList) throws XMLStreamException {
        return false;
    }

    /**
     * Parse {@link Element#ACCESS_CONTROL} content.
     *
     * @param reader the xml reader
     * @param address the address of the parent resource for any added resources
     * @param operationsList list to which any operations should be added
     * @throws XMLStreamException
     */
    default boolean parseAccessControl(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> operationsList) throws XMLStreamException {
        return false;
    }

    default boolean parseAuditLog(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        return false;
    }


    /*
     * Write Methods
     */

    /**
     * Write the {@link Element#NATIVE_INTERFACE} element.
     * <p>This default implementation throws {@code UnsupportedOperationException}; override to support.</p>
     *
     * @param writer  the xml writer
     * @param protocol the interface configuration
     * @throws XMLStreamException
     */
    default boolean writeNativeManagementProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        return false;
    }

    /**
     * Write the {@link Element#HTTP_INTERFACE} element.
     * <p>This default implementation throws {@code UnsupportedOperationException}; override to support.</p>
     *
     * @param writer  the xml writer
     * @param protocol the interface configuration
     * @throws XMLStreamException
     */
    default boolean writeHttpManagementProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        return false;
    }

    default boolean writeAccessControl(XMLExtendedStreamWriter writer, ModelNode accessAuthorization) throws XMLStreamException {
        return false;
    }

    default boolean writeAuditLog(XMLExtendedStreamWriter writer, ModelNode auditLog) throws XMLStreamException {
        return false;
    }

}
