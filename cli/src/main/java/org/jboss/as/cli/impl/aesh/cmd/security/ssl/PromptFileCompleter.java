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
