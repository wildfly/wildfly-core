/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import java.util.function.Supplier;

import org.jboss.staxmapper.XMLElementWriter;

/**
 * Registry for {@link XMLElementWriter}s that can marshal the configuration
 * for a subsystem.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface SubsystemXmlWriterRegistry {

    /**
     * Registers the writer that can marshal to XML the configuration of the
     * named subsystem.
     *
     * @param name the name of the subsystem
     * @param writer the XML writer
     */
    void registerSubsystemWriter(String name, Supplier<XMLElementWriter<SubsystemMarshallingContext>> writer);

    /**
     * Unregisters the XML configuration writer of the named subsystem.
     *
     * @param name the name of the subsystem
     */
    void unregisterSubsystemWriter(String name);
}
