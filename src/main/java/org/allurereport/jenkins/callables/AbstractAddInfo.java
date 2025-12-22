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
import net.sf.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author charlie (Dmitry Baev).
 */
public abstract class AbstractAddInfo extends MasterToSlaveFileCallable<FilePath> {

    @Override
    public FilePath invoke(final File file,
                           final VirtualChannel channel) throws IOException {
        final Path outputDirectory = Paths.get(file.toURI()).toRealPath();
        Files.createDirectories(outputDirectory);
        final Path testRun = outputDirectory.resolve(getFileName());
        try (Writer writer = Files.newBufferedWriter(testRun, StandardCharsets.UTF_8)) {
            JSONObject.fromObject(getData())
                    .write(writer)
                    .flush();
        }
        return new FilePath(testRun.toFile());
    }

    protected abstract Object getData();

    protected abstract String getFileName();

}
