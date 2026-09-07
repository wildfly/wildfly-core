/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Abstract superclass for {@link Extension} implementations where the extension is no longer supported
 * for use on current version servers but is supported on host controllers in order to allow use
 * of the extension on legacy version hosts in a mixed-version domain.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class AbstractLegacyExtension implements Extension {

    private final String extensionName;
    private final List<String> subsystemNames;

    protected AbstractLegacyExtension(String extensionName, String... subsystemNames) {
        this.extensionName = extensionName;
        this.subsystemNames = Arrays.asList(subsystemNames);
    }

    @Override
    public void initialize(ExtensionContext context) {

        if (context.getProcessType() == ProcessType.DOMAIN_SERVER) {
            // Do nothing. This allows an extension=cmp:add op that's really targeted
            // to legacy servers to work
            ControllerLogger.MGMT_OP_LOGGER.ignoringUnsupportedLegacyExtension(subsystemNames, extensionName);
            return;
        } else if (context.getProcessType() == ProcessType.STANDALONE_SERVER) {
            if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                //log a message, but fall through and register the model
                ControllerLogger.MGMT_OP_LOGGER.removeUnsupportedLegacyExtension(subsystemNames, extensionName);
            } else {
                throw new UnsupportedOperationException(ControllerLogger.ROOT_LOGGER.unsupportedLegacyExtension(extensionName));
            }
        }

        Set<ManagementResourceRegistration> subsystemRoots = initializeLegacyModel(context);
        for (ManagementResourceRegistration subsystemRoot : subsystemRoots) {
            subsystemRoot.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION,
                new UnsupportedSubsystemDescribeHandler(extensionName));
        }
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {

        if (context.getProcessType() == ProcessType.DOMAIN_SERVER) {
            // Do nothing. This allows the extension=cmp:add op that's really targeted
            // to legacy servers to work
            return;
        } else if (context.getProcessType() == ProcessType.STANDALONE_SERVER && context.getRunningMode() != RunningMode.ADMIN_ONLY) {
            throw new UnsupportedOperationException(ControllerLogger.ROOT_LOGGER.unsupportedLegacyExtension(extensionName));
        }

        initializeLegacyParsers(context);
    }

    /**
     * Perform the work that a non-legacy extension would perform in {@link #initialize(org.jboss.as.controller.ExtensionContext)},
     * except no handler for the {@code describe} operation should be registered.
     *
     * @param context the extension context
     * @return set containing the root {@link ManagementResourceRegistration} for all subsystems that were registered.
     *         The calling method will register a {@code describe} operation handler for each of these
     */
    protected abstract Set<ManagementResourceRegistration> initializeLegacyModel(ExtensionContext context);

    /**
     * Perform the work that a non-legacy extension would perform in
     * {@link #initializeParsers(org.jboss.as.controller.parsing.ExtensionParsingContext)}.
     *
     * @param context the extension parsing context
     */
    protected abstract void initializeLegacyParsers(ExtensionParsingContext context);
}
