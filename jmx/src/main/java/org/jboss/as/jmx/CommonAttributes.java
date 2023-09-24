/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

/**
 * @author Emanuel Muckenhuber
 */
interface CommonAttributes {

    String AUDIT_LOG = "audit-log";
    String CONNECTOR = "connector";
    String CONFIGURATION = "configuration";
    String DEFAULT_EXPRESSION_DOMAIN = "jboss.as.expr";
    String DEFAULT_RESOLVED_DOMAIN = "jboss.as";
    String DOMAIN_NAME = "domain-name";
    String ENABLED = "enabled";
    String EXPOSE_EXPRESSION_MODEL = "expose-expression-model";
    String EXPOSE_MODEL = "expose-model";
    String EXPOSE_RESOLVED_MODEL = "expose-resolved-model";
    String EXPRESSION = "expression";
    String HANDLER = "handler";
    String HANDLERS = "handlers";
    String JMX = "jmx";
    String JMX_CONNECTOR = "jmx-connector";
    String LOG_BOOT = "log-boot";
    String LOG_READ_ONLY = "log-read-only";
    String NAME = "name";
    String NON_CORE_MBEANS = "non-core-mbeans";
    String NON_CORE_MBEAN_SENSITIVITY = "non-core-mbean-sensitivity";
    String PROPER_PROPERTY_FORMAT = "proper-property-format";
    String REGISTRY_BINDING = "registry-binding";
    String REMOTING_CONNECTOR = "remoting-connector";
    String RESOLVED = "resolved";
    String SENSITIVITY = "sensitivity";
    String SHOW_MODEL = "show-model";
    String SERVER_BINDING = "server-binding";
    String USE_MANAGEMENT_ENDPOINT = "use-management-endpoint";
    String VALUE = "value";
}
