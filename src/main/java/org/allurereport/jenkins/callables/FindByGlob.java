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
package org.allurereport.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.ant.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * eroshenkoam.
 * 26.06.17
 */
public class FindByGlob extends MasterToSlaveFileCallable<List<FilePath>> {

    private final String includes;

    public FindByGlob(final String includes) {
        this.includes = includes;
    }

    @Override
    public List<FilePath> invoke(final File file,
                                 final VirtualChannel channel) throws IOException, InterruptedException {
        final FilePath root = new FilePath(file);

        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(file);
        scanner.setIncludes(new String[]{includes});
        scanner.setCaseSensitive(false);
        scanner.scan();

        final String[] paths = scanner.getIncludedDirectories();
        final List<FilePath> files = new ArrayList<>();
        for (String path : paths) {
            files.add(root.child(path));
        }
        return files;
    }

}
