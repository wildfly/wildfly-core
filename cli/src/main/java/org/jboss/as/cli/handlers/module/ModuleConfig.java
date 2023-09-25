/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.module;

import java.util.Collection;
import java.util.Map;

import org.jboss.staxmapper.XMLElementWriter;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ModuleConfig extends XMLElementWriter<ModuleConfig> {

    String getSchemaVersion();

    String getModuleName();

    String getSlot();

    String getMainClass();

    Collection<Resource> getResources();

    Collection<Dependency> getDependencies();

    Map<String, String> getProperties();

    interface Resource extends XMLElementWriter<Resource> {

    }

    interface Dependency extends XMLElementWriter<Dependency> {
    }
}
