/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * Context passed to {@link XMLElementWriter}s that marshal a model.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ModelMarshallingContext {

    /**
     * Gets the model to marshal.
     * @return the model
     */
    ModelNode getModelNode();

    /**
     * Gets the writer that can marshal the subsystem with the given name.
     *
     * @param subsystemName the name of the subsystem
     * @return the writer, or {@code null} if there is no writer registered
     *          under {@code subsystemName}
     */
    XMLElementWriter<SubsystemMarshallingContext> getSubsystemWriter(String subsystemName);
}
