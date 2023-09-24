/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jboss.as.test.shared.CliUtils.escapePath;

/**
 * Helper class for adding "path" and "relative-to" attributes into CLI commands.
 *
 * @author Josef Cacek
 */
public class CliPath implements CliFragment {

    public static final CliPath EMPTY = CliPath.builder().build();

    private final String path;
    private final String relativeTo;

    private CliPath(Builder builder) {
        this.path = builder.path;
        this.relativeTo = builder.relativeTo;
    }

    public String getPath() {
        return path;
    }

    public String getRelativeTo() {
        return relativeTo;
    }

    /**
     * Generates part of CLI string in form "[path=..., [relative-to=..., ]"
     */
    @Override
    public String asString() {
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(path)) {
            sb.append(String.format("path=\"%s\", ", escapePath(path)));
            if (isNotBlank(relativeTo)) {
                sb.append(String.format("relative-to=\"%s\"", relativeTo));
            }
        }
        return sb.toString();
    }

    /**
     * Creates builder to build {@link CliPath}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link CliPath}.
     */
    public static final class Builder {
        private String path;
        private String relativeTo;

        private Builder() {
        }

        public Builder withPath(String path) {
            this.path = path;
            return this;
        }

        public Builder withRelativeTo(String relativeTo) {
            this.relativeTo = relativeTo;
            return this;
        }

        public CliPath build() {
            return new CliPath(this);
        }
    }

}
