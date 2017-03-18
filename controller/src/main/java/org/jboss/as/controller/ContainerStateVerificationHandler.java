/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
