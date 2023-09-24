/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;

/**
 * Callback an {@link ProfileParsingCompletionHandler} can register to, upon completion of normal parsing of a profile, manipulate the list
 * of parsed boot operations associated with a profile.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@FunctionalInterface
public interface ProfileParsingCompletionHandler {

    /**
     * Callback indicating normal parsing of a profile is completed.
     *
     * @param profileBootOperations the boot operations added by subsystems in the profile, keyed by the URI of the
     *                              xml namespace used for the subsystem
     * @param otherBootOperations other operations registered in the boot prior to parsing of the profile
     */
    void handleProfileParsingCompletion(final Map<String, List<ModelNode>> profileBootOperations, List<ModelNode> otherBootOperations);
}
