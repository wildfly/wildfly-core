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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.as.controller.ModelVersion;
import org.wildfly.legacy.version.LegacyVersions;


public enum ModelTestControllerVersion {
    //AS releases
    @Deprecated
    WF_11_0_0_CR1("11.0.0.CR1", false, null, "3.0.1.Final", "11.0.0"), // used for testing elytron shipped in core 3.0.2.Final vs 3.0.1.Final
    MASTER (CurrentVersion.VERSION, false, null, "master" ),

    //EAP releases
    @Deprecated
    EAP_6_2_0 ("7.3.0.Final-redhat-14", true, null, "6.2.0"),
    @Deprecated
    EAP_6_3_0 ("7.4.0.Final-redhat-19", true, null, "6.3.0"),
    @Deprecated
    EAP_6_4_0 ("7.5.0.Final-redhat-21", true, "7.5.0", "6.4.0"), //EAP 6.4 is the earliest version we support for transformers
    @Deprecated
    EAP_6_4_7 ("7.5.7.Final-redhat-3", true, "7.5.0", "6.4.7"), //this one is special as it has model change in micro release
    @Deprecated
    EAP_7_0_0 ("7.0.0.GA-redhat-2", true, "10.0.0", "2.1.2.Final-redhat-1", "7.0.0"),
    @Deprecated
    EAP_7_1_0 ("7.1.0.GA-redhat-11", true, "11.0.0", "3.0.10.Final-redhat-1", "7.1.0"),

    // WildFly legacy test will need to rename the *-wf14.dmr files to *-7.2.0.dmr
    @Deprecated
    EAP_7_2_0("7.2.0.GA-redhat-00005", true, "14.0.0", "6.0.11.Final-redhat-00001", "7.2.0"),
    @Deprecated
    EAP_7_3_0("7.3.0.GA-redhat-00004", true, "18.0.0", "10.1.2.Final-redhat-00001", "7.3.0"),
    EAP_7_4_0("7.4.0.GA-redhat-00005", true, "23.0.0", "15.0.2.Final-redhat-00001", "7.4.0"),

    // https://issues.redhat.com/browse/WFCORE-5753
    // Once EAP XP 4 is out, we need to replace the following live with
    // EAP_XP_4("4.0.0.GA-redhat-0000x", true, "24.0.0", "18.0.0.Final-redhat-0000x", "xp4")
    // We will also need to remove the *-wf26 files in wildfly-legacy-test and replace them with *-xp4 ones
    // generated from EAP 7.4.0 with XP 4 installed
    EAP_XP_4("26.0.0.Beta1", false, "25.0.0", "18.0.0.Final", "wf26")
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
    private final String realVersionName;
    private final String artifactIdPrefix;
    private final Map<String, ModelVersion> subsystemModelVersions = new LinkedHashMap<>();

    ModelTestControllerVersion(String mavenGavVersion, boolean eap, String testControllerVersion, String realVersionName) {
        this(mavenGavVersion, eap, testControllerVersion, null, realVersionName);
    }

    ModelTestControllerVersion(String mavenGavVersion, boolean eap, String testControllerVersion, String coreVersion, String realVersionName) {
        this.mavenGavVersion = mavenGavVersion;
        this.testControllerVersion = testControllerVersion;
        this.eap = eap;
        this.validLegacyController = testControllerVersion != null;
        this.coreVersion = coreVersion == null? mavenGavVersion : coreVersion; //full == core
        this.realVersionName = realVersionName;
        if (eap) {
            if (coreVersion != null) { //eap 7+ has core version defined
                this.coreMavenGroupId = "org.wildfly.core";
                this.mavenGroupId = "org.jboss.eap";
                this.serverMavenArtifactId = "wildfly-server";
                this.hostControllerMavenArtifactId = "wildfly-host-controller";
                this.artifactIdPrefix = "wildfly-";
            } else { //we have EAP6
                this.mavenGroupId = "org.jboss.as";
                this.coreMavenGroupId = "org.jboss.as"; //full == core
                this.serverMavenArtifactId = "jboss-as-server";
                this.hostControllerMavenArtifactId = "jboss-as-host-controller";
                this.artifactIdPrefix = "jboss-as-";
            }

        } else { //we only handle WildFly 9+ as legacy version, we don't care about AS7.x.x community releases.
            this.coreMavenGroupId = "org.wildfly.core";
            this.mavenGroupId = "org.wildfly";
            this.serverMavenArtifactId = "wildfly-server";
            this.hostControllerMavenArtifactId = "wildfly-host-controller";
            this.artifactIdPrefix = "wildfly-";
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

    public String getRealVersionName() {
        return realVersionName;
    }

    public String getArtifactIdPrefix(){
        return artifactIdPrefix;
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

    public ModelVersion getSubsystemModelVersion(String subsystemName){
        return getSubsystemModelVersions().get(subsystemName);
    }

    public Map<String,ModelVersion> getSubsystemModelVersions(){
        if (subsystemModelVersions.isEmpty()){
            synchronized (subsystemModelVersions){
                subsystemModelVersions.putAll(LegacyVersions.getModelVersions(realVersionName));
            }
        }
        return this.subsystemModelVersions;
    }

    public String getMavenGav(String artifactIdPart, boolean coreArtifact) {
        return String.format("%s:%s%s:%s",
                coreArtifact ? getCoreMavenGroupId() : getMavenGroupId(),
                getArtifactIdPrefix(), artifactIdPart,
                getMavenGavVersion());

    }

}
