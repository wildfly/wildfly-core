/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;


import java.nio.file.Path;
import java.util.List;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller.RunningModeControl;
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
     * @param runningModeControl: the running mode control of the server.
     * @return true if the configuration extension should process operations - false otherwise.
     */
    boolean shouldProcessOperations(RunningModeControl runningModeControl);

    /**
     * Process the already defined boot operations to update them with the supplemnetal configurations.
     * @param rootRegistration: metamodel.
     * @param postExtensionOps: initial boot oerations.
     */
    void processOperations(ImmutableManagementResourceRegistration rootRegistration, List<ParsedBootOp> postExtensionOps);
}
