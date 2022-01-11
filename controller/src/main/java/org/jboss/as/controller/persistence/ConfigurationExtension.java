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
import java.util.List;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;

/**
 * Interface for extensions of the XML boot configuration.
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public interface ConfigurationExtension {

    /** Loads supplemental configurations.
     * @param files the paths to files with supplemental configuration.
     * @return the configuration extension.
     */
    ConfigurationExtension load(Path... files);

    /**
     * Returns the command line usage arguments to be displayed for help.
     * @return the command line usage arguments to be displayed for help.
     */
    String[] getCommandLineUsageArguments();

    /**
     * Checks if the argument is one of those required for this configuration extension.
     * @param arg: the command line or one of its argument.
     * @return true if the argument is one of those required by this configuration extension - false otherwise.
     */
    boolean commandLineContainsArgument(String arg);

    /**
     * Returns the command line instructions.
     * @return the command line instructions.
     */
    String getCommandLineInstructions();

    /**
     * Checks if the configuration extension should process the supplemental configurations.
     * @param mode: the running mode of the server.
     * @return true if the configuration extension should process operations - false otherwise.
     */
    boolean shouldProcessOperations(RunningMode mode);

    /**
     * Process the already defined boot operations to update them with the supplemnetal configurations.
     * @param rootRegistration: metamodel.
     * @param postExtensionOps: initial boot oerations.
     */
    void processOperations(ImmutableManagementResourceRegistration rootRegistration, List<ParsedBootOp> postExtensionOps);
}
