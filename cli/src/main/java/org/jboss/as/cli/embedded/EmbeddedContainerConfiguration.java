/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.cli.embedded;

import java.io.File;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.embedded.EmbeddedStandAloneServerFactory;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @author <a href="mailto:mmatloka@gmail.com">Michal Matloka</a>
 */
public class EmbeddedContainerConfiguration {

    private String jbossHome = System.getenv("JBOSS_HOME");

    private String modulePath = System.getProperty("module.path");

    private String cleanServerBaseDir = System.getProperty(EmbeddedStandAloneServerFactory.JBOSS_EMBEDDED_ROOT);

    public EmbeddedContainerConfiguration() {

        // if no jbossHome is set use jboss.home of already running jvm
        if (jbossHome == null || jbossHome.isEmpty()) {
            jbossHome = System.getProperty("jboss.home");
        }

        if ((modulePath == null || modulePath.isEmpty()) && jbossHome != null) {
            modulePath = jbossHome + "/modules";
        }
    }

    /**
     * @return the jbossHome
     */
    public String getJbossHome() {
        return jbossHome;
    }

    /**
     * @param jbossHome the jbossHome to set
     */
    public void setJbossHome(String jbossHome) {
        this.jbossHome = jbossHome;
    }

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(final String modulePath) {
        this.modulePath = modulePath;
    }

    public String getCleanServerBaseDir() {
        return cleanServerBaseDir;
    }

    public void setCleanServerBaseDir(String cleanServerBaseDir) {
        this.cleanServerBaseDir = cleanServerBaseDir;
    }

    void validate() throws CommandLineException {
        configurationDirectoryExists(jbossHome, "jboss-home '" + jbossHome + "' must exist");
        configurationDirectoryExists(modulePath, "module.path '" + modulePath + "' must exist");
    }

    private static void configurationDirectoryExists(final String string, final String message) throws CommandLineException {
        if (string == null || string.length() == 0 || !new File(string).isDirectory()) {
            throw new CommandLineException(message);
        }
    }
}
