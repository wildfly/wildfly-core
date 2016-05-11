/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
        //For these we only had a full distribution
        AS_7_1_2_FINAL(AS, "7.1.2.Final", false, JDK.JDK6, "remote", "9999"),
        AS_7_1_3_FINAL(AS, "7.1.3.Final", false, JDK.JDK6, "remote", "9999"),
        AS_7_2_0_FINAL(AS, "7.2.0.Final", false, JDK.JDK6, "remote", "9999"),
        WF_8_0_0_FINAL(WILDFLY, "8.0.0.Final", false, JDK.JDK8, "http-remoting", "9990"),
        WF_8_1_0_FINAL(WILDFLY, "8.1.0.Final", false, JDK.JDK8, "http-remoting", "9990"),
        WF_8_2_0_FINAL(WILDFLY, "8.2.0.Final", false, JDK.JDK8, "http-remoting", "9990"),

        //For these we have a core distribution, and these versions are not tested in mixed domain. So to save space on
        //the CI we just use the core versions used in the WildFly versions
        WF_9_0_0_FINAL(WILDFLY_CORE, "1.0.0.Final", false, JDK.JDK8, "http-remoting", "9990"),
        WF_10_0_0_FINAL(WILDFLY_CORE, "2.0.10.Final", false, JDK.JDK8, "http-remoting", "9990"),

        //For these we only had a full distribution
        EAP_6_2_0(EAP, "6.2.0", true, JDK.JDK6, "remote", "9999"),
        EAP_6_3_0(EAP, "6.3.0", true, JDK.JDK7, "remote", "9999"),
        EAP_6_4_0(EAP, "6.4.0", true, JDK.JDK8, "remote", "9999"),
        //Although EAP >= 7 is split, the full distribution is needed in the mixed domain tests
        EAP_7_0_0(EAP, "7.0.0", true, JDK.JDK8, "http-remoting", "9990");


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
        JDK6,
        JDK7,
        JDK8
    }
}