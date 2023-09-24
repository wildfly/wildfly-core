/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.client.old.server.util;

/**
 * @author Kabir Khan
 */
public class Version {

    static final String AS = "jboss-as-";
    static final String WILDFLY = "wildfly-";
    static final String WILDFLY_CORE = "wildfly-core-";
    static final String EAP = "jboss-eap-";

    public enum AsVersion {
        EAP_7_4_0(EAP, "7.4.0", true, JDK.JDK11, "http-remoting", "9990");


        private final String basename;
        private final String version;
        private final boolean eap;
        private final JDK jdk;
        private final String managementProtocol;
        private final String managementPort;
        AsVersion(String basename, String version, boolean eap, JDK jdk, String managementProtocol, String managementPort){
            this.basename = basename;
            this.version = version;
            this.eap = eap;
            this.jdk = jdk;
            this.managementProtocol = managementProtocol;
            this.managementPort = managementPort;
        }

        public String getBaseName() {
            return basename;
        }

        public boolean isEap() {
            return eap;
        }

        public JDK getJdk() {
            return jdk;
        }

        public String getVersion() {
            return version;
        }

        public String getManagementProtocol() {
            return managementProtocol;
        }

        public String getManagementPort() {
            return managementPort;
        }

        public String getFullVersionName() {
            return basename + version;
        }
        public String getZipFileName() {
            return  getFullVersionName() + ".zip";
        }
    }

    enum JDK {
        JDK8,
        JDK11
    }
}