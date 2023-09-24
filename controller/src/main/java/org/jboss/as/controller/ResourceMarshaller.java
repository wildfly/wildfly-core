/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 */
@FunctionalInterface
public interface ResourceMarshaller {
    void persist(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException;
}
