/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * Reports a failure due to a flawed container state, if and only if it appears no failure has already been
 * reported. Serves as a last line of defense for cases where an op introduces service container problems
 * but our standard mechanisms like ServiceVerificationHelper or OperationContextImpl.ServiceRemovalVerificationHandler
 * were unable to detect the problem and associate it with a particular operation step.
 *
 * @author Brian Stansberry
 */
final class ContainerStateVerificationHandler implements OperationStepHandler {
    static final OperationContext.AttachmentKey<Boolean> FAILURE_REPORTED_ATTACHMENT = OperationContext.AttachmentKey.create(Boolean.class);

    private final ContainerStateMonitor.ContainerStateChangeReport changeReport;

    ContainerStateVerificationHandler(ContainerStateMonitor.ContainerStateChangeReport changeReport) {
        assert changeReport != null;
        assert changeReport.hasNewProblems();
        this.changeReport = changeReport;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // We only need to report if our response has no failure description already
        // and if no other verification handler has reported a failure (which they may have done
        // against a different response object that is invisible to us at this point,
        // hence the use of the FAILURE_REPORTED_ATTACHMENT)
        if (!context.hasFailureDescription() && context.getAttachment(FAILURE_REPORTED_ATTACHMENT) == null) {
            throw new OperationFailedException(ContainerStateMonitor.createChangeReportLogMessage(changeReport, true));
        }
    }
}
