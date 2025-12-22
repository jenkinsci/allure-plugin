/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.config;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * eroshenkoam.
 * 25/12/16
 */
public class ResultsConfig implements Serializable {

    private final String path;

    @DataBoundConstructor
    public ResultsConfig(final String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(path).toHashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof ResultsConfig) {
            final ResultsConfig other = (ResultsConfig) object;
            return new EqualsBuilder().append(path, other.path).isEquals();
        } else {
            return false;
        }
    }

    public static List<ResultsConfig> convertPaths(final List<String> paths) {
        final List<ResultsConfig> results = new ArrayList<>();
        for (String path : paths) {
            results.add(new ResultsConfig(path));
        }
        return results;
    }

}
