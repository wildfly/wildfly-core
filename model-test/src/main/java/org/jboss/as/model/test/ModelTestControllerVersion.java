/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.model.test;

import java.util.Properties;


public enum ModelTestControllerVersion {
    //AS releases
    MASTER (CurrentVersion.VERSION, false, null),

    //WILDFLY RELEASES
    @Deprecated
    WILDFLY_8_0_0_FINAL ("8.0.0.Final", false, "8.0.0", false),
    @Deprecated
    WILDFLY_8_1_0_FINAL ("8.1.0.Final", false, "8.0.0", false),
    @Deprecated
    WILDFLY_8_2_0_FINAL ("8.2.0.Final", false, "8.0.0", false),
    //EAP releases

    EAP_6_2_0 ("7.3.0.Final-redhat-14", true, "7.3.0"), //EAP 6.2 is the earliest version we support for transformers
    EAP_6_3_0 ("7.4.0.Final-redhat-19", true, "7.4.0"),
    EAP_6_4_0 ("7.5.0.Final-redhat-21", true, "7.5.0"),
    EAP_7_0_0 ("7.0.0.GA-redhat-2", true, "10.0.0", true, "2.1.0.Final")
    ;

    private final String mavenGavVersion;
    private final String testControllerVersion;
    private final boolean eap;
    private final boolean validLegacyController;
    private final String coreVersion;
    private final String mavenGroupId;
    private final String coreMavenGroupId;
    private final String serverMavenArtifactId;
    private final String hostControllerMavenArtifactId;

    private ModelTestControllerVersion(String mavenGavVersion, boolean eap, String testControllerVersion) {
        this(mavenGavVersion, eap, testControllerVersion, true, null);
    }

    private ModelTestControllerVersion(String mavenGavVersion, boolean eap, String testControllerVersion, boolean validLegacyController) {
        this(mavenGavVersion, eap, testControllerVersion, validLegacyController, null);
    }
    private ModelTestControllerVersion(String mavenGavVersion, boolean eap, String testControllerVersion, boolean validLegacyController, String coreVersion) {
        this.mavenGavVersion = mavenGavVersion;
        this.testControllerVersion = testControllerVersion;
        this.eap = eap;
        this.validLegacyController = validLegacyController;
        this.coreVersion = coreVersion == null? mavenGavVersion : coreVersion; //full == core
        if (eap) {
            if (coreVersion != null) { //eap 7+ has core version defined
                this.coreMavenGroupId = "org.wildfly.core";
                this.mavenGroupId = "org.jboss.eap";
                this.serverMavenArtifactId = "wildfly-server";
                this.hostControllerMavenArtifactId = "wildfly-host-controller";
            } else { //we have EAP6
                this.mavenGroupId = "org.jboss.as";
                this.coreMavenGroupId = "org.jboss.as"; //full == core
                this.serverMavenArtifactId = "jboss-as-server";
                this.hostControllerMavenArtifactId = "jboss-as-host-controller";
            }

        } else { //we only handle WildFly 9+ as legacy version, we don't care about AS7.x.x community releases.
            this.coreMavenGroupId = "org.wildfly.core";
            this.mavenGroupId = "org.wildfly";
            this.serverMavenArtifactId = "wildfly-server";
            this.hostControllerMavenArtifactId = "wildfly-host-controller";
        }
    }

    public String getMavenGavVersion() {
        return mavenGavVersion;
    }

    public String getTestControllerVersion() {
        return testControllerVersion;
    }

    public boolean isEap() {
        return eap;
    }

    public boolean hasValidLegacyController() {
        return validLegacyController;
    }

    public String getCoreVersion() {
        return coreVersion;
    }

    public String getMavenGroupId() {
        return mavenGroupId;
    }

    public String getCoreMavenGroupId() {
        return coreMavenGroupId;
    }

    public String getServerMavenArtifactId() {
        return serverMavenArtifactId;
    }

    public String getHostControllerMavenArtifactId() {
        return hostControllerMavenArtifactId;
    }

    public interface CurrentVersion {
        String VERSION = VersionLocator.VERSION;
    }

    static final class VersionLocator {
        private static String VERSION;

        static {
            try {
                Properties props = new Properties();
                props.load(ModelTestControllerVersion.class.getResourceAsStream("version.properties"));
                VERSION = props.getProperty("as.version");
            } catch (Exception e) {
                VERSION = "10.0.0.Alpha5-SNAPSHOT";
                e.printStackTrace();
            }
        }

        static String getCurrentVersion() {
            return VERSION;
        }
    }

}
