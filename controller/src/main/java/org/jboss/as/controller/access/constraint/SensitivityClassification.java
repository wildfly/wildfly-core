/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;


/**
 * Classification to apply to resources, attributes or operation to allow configuration
 * of whether access, reads or writes are sensitive.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SensitivityClassification extends AbstractSensitivity {

    public static final SensitivityClassification ACCESS_CONTROL = new SensitivityClassification("access-control", true, true, true);
    public static final SensitivityClassification AUTHENTICATION_CLIENT_REF = new SensitivityClassification("authentication-client-ref", true, true, true);
    public static final SensitivityClassification AUTHENTICATION_FACTORY_REF = new SensitivityClassification("authentication-factory-ref", true, true, true);
    public static final SensitivityClassification CREDENTIAL = new SensitivityClassification("credential", false, true, true);
    public static final SensitivityClassification DOMAIN_CONTROLLER = new SensitivityClassification("domain-controller", false, false, true);
    public static final SensitivityClassification DOMAIN_NAMES = new SensitivityClassification("domain-names", false, false, true);
    public static final SensitivityClassification ELYTRON_SECURITY_DOMAIN_REF = new SensitivityClassification("elytron-security-domain-ref", true, true, true);
    public static final SensitivityClassification EXTENSIONS = new SensitivityClassification("extensions", false, false, true);
    public static final SensitivityClassification JVM = new SensitivityClassification("jvm", false, false, true);
    public static final SensitivityClassification MANAGEMENT_INTERFACES = new SensitivityClassification("management-interfaces", false, false, true);
    public static final SensitivityClassification MODULE_LOADING = new SensitivityClassification("module-loading", false, false, true);
    public static final SensitivityClassification PATCHING = new SensitivityClassification("patching", false, false, true);
    public static final SensitivityClassification READ_WHOLE_CONFIG = new SensitivityClassification("read-whole-config", false, true, true);
    public static final SensitivityClassification SECURITY_REALM = new SensitivityClassification("security-realm", true, true, true);
    public static final SensitivityClassification SECURITY_REALM_REF = new SensitivityClassification("security-realm-ref", true, true, true);
    public static final SensitivityClassification SECURITY_DOMAIN = new SensitivityClassification("security-domain", true, true, true);
    public static final SensitivityClassification SECURITY_DOMAIN_REF = new SensitivityClassification("security-domain-ref", true, true, true);
    public static final SensitivityClassification SECURITY_VAULT = new SensitivityClassification("security-vault", false, true, true);
    public static final SensitivityClassification SERVER_SSL = new SensitivityClassification("server-ssl", true, true, true);
    public static final SensitivityClassification SERVICE_CONTAINER = new SensitivityClassification("service-container", false, false, true);
    public static final SensitivityClassification SOCKET_BINDING_REF = new SensitivityClassification("socket-binding-ref", false, false, false);
    public static final SensitivityClassification SOCKET_CONFIG = new SensitivityClassification("socket-config", false, false, true);
    public static final SensitivityClassification SNAPSHOTS = new SensitivityClassification("snapshots", false, false, false);
    public static final SensitivityClassification SSL_REF = new SensitivityClassification("ssl-ref", true, true, true);
    public static final SensitivityClassification SYSTEM_PROPERTY = new SensitivityClassification("system-property", false, false, true);

    private final boolean core;
    private final String subsystem;
    private final String name;

    private SensitivityClassification(String name, boolean accessDefault, boolean readDefault, boolean writeDefault) {
        super(accessDefault, readDefault, writeDefault);
        this.core = true;
        this.subsystem = null;
        this.name = name;
    }

    public SensitivityClassification(String subsystem, String name, boolean accessDefault, boolean readDefault, boolean writeDefault) {
        super(accessDefault, readDefault, writeDefault);
        assert subsystem != null : "subsystem is null";
        assert name != null : "name is null";
        this.core = false;
        this.subsystem = subsystem;
        this.name = name;
    }

    public boolean isCore() {
        return core;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SensitivityClassification that = (SensitivityClassification) o;

        return core == that.core && name.equals(that.name)
                && !(subsystem != null ? !subsystem.equals(that.subsystem) : that.subsystem != null);

    }

    @Override
    public int hashCode() {
        int result = (core ? 1 : 0);
        result = 31 * result + (subsystem != null ? subsystem.hashCode() : 0);
        result = 31 * result + name.hashCode();
        return result;
    }

    Key getKey() {
        return new Key();
    }

    class Key {

        private final SensitivityClassification sensitivity = SensitivityClassification.this;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key thatKey = (Key) o;
            SensitivityClassification that = thatKey.sensitivity;

            return core == that.core && name.equals(that.name)
                    && !(subsystem != null ? !subsystem.equals(that.subsystem) : that.subsystem != null);

        }

        @Override
        public int hashCode() {
            int result = (core ? 1 : 0);
            result = 31 * result + (subsystem != null ? subsystem.hashCode() : 0);
            result = 31 * result + name.hashCode();
            return result;
        }

    }
}
