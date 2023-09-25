/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

// $Id$

import java.util.Hashtable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.jboss.as.jmx.logging.JmxLogger;

/**
 * A simple factory for creating safe object names.
 *
 * @author Thomas.Diesler@jboss.org
 * @since 08-May-2006
 */
public class ObjectNameFactory {

    public static ObjectName create(String name) {
        try {
            return new ObjectName(name);
        } catch (MalformedObjectNameException e) {
            throw JmxLogger.ROOT_LOGGER.invalidObjectName(name, e.getLocalizedMessage());
        }
    }

    public static ObjectName create(String domain, String key, String value) {
        try {
            return new ObjectName(domain, key, value);
        } catch (MalformedObjectNameException e) {
            throw JmxLogger.ROOT_LOGGER.invalidObjectName(domain, key, value, e.getLocalizedMessage());
        }
    }

    public static ObjectName create(String domain, Hashtable<String, String> table) {
        try {
            return new ObjectName(domain, table);
        } catch (MalformedObjectNameException e) {
            throw JmxLogger.ROOT_LOGGER.invalidObjectName(domain, table, e.getLocalizedMessage());
        }
    }
}
