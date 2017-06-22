/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.security.common.elytron;

/**
 * Object which holds sasl filter configuration.
 *
 * @author Josef Cacek
 */
public class SaslFilter {

    private final String predefinedFilter;
    private final String patternFilter;
    private final Boolean enabling;

    private SaslFilter(Builder builder) {
        this.predefinedFilter = builder.predefinedFilter;
        this.patternFilter = builder.patternFilter;
        this.enabling = builder.enabling;
    }

    /**
     * Creates builder to build {@link SaslFilter}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getPredefinedFilter() {
        return predefinedFilter;
    }

    public String getPatternFilter() {
        return patternFilter;
    }

    public Boolean isEnabling() {
        return enabling;
    }

    /**
     * Builder to build {@link SaslFilter}.
     */
    public static final class Builder {
        private String predefinedFilter;
        private String patternFilter;
        private Boolean enabling;

        private Builder() {
        }

        public Builder withPredefinedFilter(String predefinedFilter) {
            this.predefinedFilter = predefinedFilter;
            return this;
        }

        public Builder withPatternFilter(String patternFilter) {
            this.patternFilter = patternFilter;
            return this;
        }

        public Builder withEnabling(Boolean enabling) {
            this.enabling = enabling;
            return this;
        }

        public SaslFilter build() {
            return new SaslFilter(this);
        }
    }


}
