/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.validation;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.runner.PatchUtils;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2013 Red Hat, inc.
 */
public class PatchingFileRenamingCollector {

    private static final PatchLogger log = PatchLogger.ROOT_LOGGER;
    private final File renamingFailureMarker;

    public PatchingFileRenamingCollector(final File renamingFailureMarker) {
        this.renamingFailureMarker = renamingFailureMarker;
    }

    public void renameFiles() throws IOException {
        List<String> failures = PatchUtils.readRefs(renamingFailureMarker);
        for(String path : failures) {
           File toBeRenamed = new File(path);
           if(toBeRenamed.exists()) {
               if(!toBeRenamed.renameTo(PatchUtils.getRenamedFileName(toBeRenamed))) {
                   log.cannotDeleteFile(path);
               }
           }
        }
        renamingFailureMarker.delete();
    }
}
