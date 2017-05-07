package ru.yandex.qatools.allure.jenkins.utils;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * eroshenkoam
 * 11.04.17
 */
public class ZipUtils {

    private ZipUtils() {
    }

    public static List<ZipEntry> listEntries(ZipFile zip, String path) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        List<ZipEntry> files = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(path)) {
                files.add(entry);
            }
        }
        return files;
    }
}
