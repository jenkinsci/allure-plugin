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
package ru.yandex.qatools.allure.jenkins.utils;

import de.schlichtherle.truezip.zip.ZipEntry;
import de.schlichtherle.truezip.zip.ZipOutputStream;
import hudson.util.FileVisitor;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * {@link FileVisitor} that creates a zip archive via TrueZip.
 */
public final class TrueZipArchiver extends Archiver {

    public static final ArchiverFactory FACTORY = new Factory();
    // Bitmask indicating directories in 'external attributes' of a ZIP archive entry.
    private static final long BITMASK_IS_DIRECTORY = 1 << 4;

    private final byte[] buf = new byte[8192];
    private final ZipOutputStream zip;

    /*package*/ TrueZipArchiver(final OutputStream out) {
        zip = new ZipOutputStream(out, Charset.defaultCharset());
    }

    @Override
    public void visit(final File f,
                      final String rawRelativePath) throws IOException {
        // int mode = IOUtils.mode(f); // TODO

        // On Windows, the elements of relativePath are separated by 
        // back-slashes (\), but Zip files need to have their path elements separated
        // by forward-slashes (/)
        final String relativePath = rawRelativePath.replace('\\', '/');

        if (f.isDirectory()) {
            final ZipEntry dirZipEntry = new ZipEntry(relativePath + '/');
            // Setting this bit explicitly is needed by some unzipping applications (see JENKINS-3294).
            dirZipEntry.setExternalAttributes(BITMASK_IS_DIRECTORY);
            //if (mode!=-1)   dirZipEntry.setUnixMode(mode); // TODO
            dirZipEntry.setTime(f.lastModified());
            zip.putNextEntry(dirZipEntry);
            zip.closeEntry();
        } else {
            final ZipEntry fileZipEntry = new ZipEntry(relativePath);
            //if (mode!=-1)   fileZipEntry.setUnixMode(mode); // TODO
            fileZipEntry.setTime(f.lastModified());
            zip.putNextEntry(fileZipEntry);
            try (InputStream in = Files.newInputStream(f.toPath())) {
                int len = in.read(buf);
                while (len >= 0) {
                    zip.write(buf, 0, len);
                    len = in.read(buf);
                }
            }
            zip.closeEntry();
        }
        entriesWritten++;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /**
     * A factory class for TrueZipArchivers.
     */
    private static final class Factory extends ArchiverFactory {
        private static final long serialVersionUID = 1L;

        @Override
        public Archiver create(final OutputStream out) throws IOException {
            return new TrueZipArchiver(out);
        }
    }
}
