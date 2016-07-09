/*
 * Copyright 2016 JBoss by Red Hat.
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
package org.jboss.as.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter used to filter contents when browsing the content respository.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public interface ContentFilter {

    boolean acceptFile(Path rootPath, Path file) throws IOException;

    boolean acceptDirectory(Path rootPath, Path path) throws IOException;

    class SimpleContentFilter implements ContentFilter {
        private final int depth;
        private final boolean archiveOnly;
        private final Set<String> visitedDirectories;

        private SimpleContentFilter(int depth, boolean archiveOnly) {
            this.depth = depth;
            this.archiveOnly = archiveOnly;
            this.visitedDirectories = new HashSet<>();
        }

        @Override
        public boolean acceptFile(Path rootPath, Path file) throws IOException {
            Path relativePath = rootPath.relativize(file);
            if(this.depth < 0 || this.depth >= relativePath.getNameCount()) {
               if(archiveOnly) {
                   if(PathUtil.isArchive(file)) {
                       return true;
                   }
                   return false;
               }
               return true;
            }
            return false;
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) {
            Path relativePath = rootPath.relativize(dir);
            if (!archiveOnly) {
                return (this.depth < 0 || this.depth >= relativePath.getNameCount());
            }
            return false;
        }
    }

    class FileContentFilter extends SimpleContentFilter {

        private FileContentFilter(int depth, boolean archiveOnly) {
            super(depth, archiveOnly);
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) {
            return false;
        }
    }

    ContentFilter ALL = new ContentFilter() {
        @Override
        public boolean acceptFile(Path rootPath, Path file) throws IOException {
            return true;
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) throws IOException {
            return true;
        }
    };

    public class Factory {

        public static ContentFilter createFileFilter(int depth, boolean archiveOnly) {
            return new FileContentFilter(depth, archiveOnly);
        }

        public static ContentFilter explodableFileFilter(boolean archiveOnly) {
            return new FileContentFilter(-1, archiveOnly);
        }

        public static ContentFilter directoryListeFileFilter() {
            return new FileContentFilter(1, false);
        }

        public static ContentFilter createContentFilter(int depth, boolean archiveOnly) {
            return new SimpleContentFilter(depth, archiveOnly);
        }

        public static ContentFilter explodableContentFilter(boolean archiveOnly) {
            return new SimpleContentFilter(-1, archiveOnly);
        }

        public static ContentFilter directoryContentListFilter() {
            return new SimpleContentFilter(1, false);
        }
    }
}
