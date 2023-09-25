/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import java.util.ArrayList;
import java.util.List;
import org.aesh.impl.util.FileLister;
import org.aesh.readline.AeshContext;
import org.aesh.readline.completion.CompleteOperation;
import org.aesh.readline.completion.Completion;

/**
 * Completion of file during user interaction.
 *
 * @author jdenise@redhat.com
 */
public class PromptFileCompleter implements Completion {
    private final AeshContext ctx;

    public PromptFileCompleter(AeshContext ctx) {
        this.ctx = ctx;
    }
    @Override
    public void complete(CompleteOperation completeOperation) {
        List<String> candidates = new ArrayList<>();
        int cursor = new FileLister(completeOperation.getBuffer(),
                ctx.getCurrentWorkingDirectory()).
                findMatchingDirectories(candidates);
        completeOperation.addCompletionCandidates(candidates);
        completeOperation.setOffset(cursor);
        completeOperation.doAppendSeparator(false);
    }

}
