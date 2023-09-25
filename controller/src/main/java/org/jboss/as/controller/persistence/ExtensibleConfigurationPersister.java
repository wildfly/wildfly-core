/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

/**
 * Combines {@link ConfigurationPersister} and {@link SubsystemXmlWriterRegistry}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ExtensibleConfigurationPersister extends ConfigurationPersister, SubsystemXmlWriterRegistry {

}
