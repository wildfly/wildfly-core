/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
