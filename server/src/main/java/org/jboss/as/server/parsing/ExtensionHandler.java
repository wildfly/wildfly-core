/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * An {@link ExtensionHandler} is a component of the {@link StandaloneXml} parser
 * and allows for customisation of the routines to read and write XML extension elements.
 * This includes the discovery of extension modules and the registration subsystem parsers that are associated with it.
 * The regular {@link StandaloneXml} parser leverages a default implementation.
 *
 * @author Heiko Braun
 * @since 27/11/15
 */
public interface ExtensionHandler {
    void parseExtensions(XMLExtendedStreamReader reader, ModelNode address, Namespace namespace, List<ModelNode> list) throws XMLStreamException;

    Set<ProfileParsingCompletionHandler> getProfileParsingCompletionHandlers();

    void writeExtensions(XMLExtendedStreamWriter writer, ModelNode modelNode) throws XMLStreamException;
}
