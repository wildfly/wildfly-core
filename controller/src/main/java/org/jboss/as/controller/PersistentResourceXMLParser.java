/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public abstract class PersistentResourceXMLParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext>, UnaryOperator<PersistentResourceXMLDescription> {

    private final AtomicReference<PersistentResourceXMLDescription> cachedDescription = new AtomicReference<>();

    public abstract PersistentResourceXMLDescription getParserDescription();

    /** @deprecated Experimental; for internal use only. May be removed at any time. */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = false)
    public final void cacheXMLDescription() {
        this.cachedDescription.updateAndGet(this);
    }

    @Override
    public PersistentResourceXMLDescription apply(PersistentResourceXMLDescription description) {
        return (description != null) ? description : this.getParserDescription();
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> modelNodes) throws XMLStreamException {
        // To reduce memory footprint, we only cache for a single parsing run
        new PersistentResourceXMLDescriptionReader(this.apply(this.cachedDescription.getAndSet(null))).readElement(reader, modelNodes);
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        new PersistentResourceXMLDescriptionWriter(this.apply(this.cachedDescription.get())).writeContent(writer, context);
    }
}
