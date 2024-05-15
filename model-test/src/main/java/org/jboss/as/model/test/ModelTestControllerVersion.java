/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.as.controller.ModelVersion;
import org.wildfly.legacy.version.LegacyVersions;


public enum ModelTestControllerVersion {
    //AS releases

    MASTER (CurrentVersion.VERSION, false, null, "master" ),

    //EAP releases
    EAP_7_4_0("7.4.0.GA-redhat-00005", true, "23.0.0", "15.0.2.Final-redhat-00001", "7.4.0"),
    EAP_8_0_0("8.0.0.GA-redhat-00011", true, "29.0.0", "21.0.5.Final-redhat-00001", "8.0.0"),
    EAP_XP_4("4.0.0.GA-redhat-00003", true, "23.0.0", "15.0.26.Final-redhat-00001", "xp4");

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
