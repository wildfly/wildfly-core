/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 *
 */
package org.jboss.as.controller.persistence;

/**
 * Provider of a {@link ConfigurationPersister}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ConfigurationPersisterProvider {

    ConfigurationPersister getConfigurationPersister();
}
