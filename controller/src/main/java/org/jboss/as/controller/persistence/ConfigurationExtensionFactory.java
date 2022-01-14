/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.persistence;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * The factory in charge with loading the first ConfigurationExtension found.
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class ConfigurationExtensionFactory {

    private static final ConfigurationExtension extension;

    static {
        ServiceLoader<ConfigurationExtension> loader = ServiceLoader.load(ConfigurationExtension.class, ConfigurationExtensionFactory.class.getClassLoader());
        Iterator<ConfigurationExtension> iter = loader.iterator();
        if (iter.hasNext()) {
            extension = iter.next();
        } else {
            extension = null;
        }
    }

    /**
     * Returns true if a configuration extension is loaded - false otherwise.
     * @return true if a configuration extension is loaded - false otherwise.
     */
    public static boolean isConfigurationExtensionSupported() {
        return extension != null;
    }

    /**
     * Returns the command line instructions.
     * @return the command line instructions.
     */
    public static String getCommandLineInstructions() {
        if(extension != null) {
            return extension.getCommandLineInstructions();
        }
        return "";
    }

    /**
     * The command line usage arguments.
     * @return the command line usage arguments.
     */
    public static String[] getCommandLineUsageArguments() {
        if(extension != null) {
            return extension.getCommandLineUsageArguments();
        }
        return new String[0];
    }

    /**
     * Checks if the the argument is is one of those required by this configuration extension.
     * @param arg: the current command line argument.
     * @return true if the argument is one of those required by this configuration extension - false otherwise.
     */
    public static boolean commandLineContainsArgument(String arg) {
        if(extension != null) {
            return extension.commandLineContainsArgument(arg);
        }
        return false;
    }

    /**
     * Create the instance of ConfigurationExtension built from the supplemental configuration files.
     * @param files: the supplemental configuration files.
     * @return the instance of ConfigurationExtension built from  the supplemental configuration files.
     */
    public static ConfigurationExtension createConfigurationExtension(Path... files) {
        if (isConfigurationExtensionSupported() && files != null && files.length > 0) {
            return extension.load(files);
        }
        return null;
    }
}
