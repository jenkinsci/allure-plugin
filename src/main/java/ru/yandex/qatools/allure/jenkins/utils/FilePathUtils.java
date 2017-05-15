package ru.yandex.qatools.allure.jenkins.utils;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public final class FilePathUtils {

    private static final String ALLURE_PREFIX = "allure";
    private static final byte[] DEFAULT_BUFFER = new byte[8192];

    private FilePathUtils() {
    }

    @SuppressWarnings("TrailingComment")
    public static void copyRecursiveTo(FilePath from, FilePath to, AbstractBuild build, PrintStream logger)
            throws IOException, InterruptedException { //NOSONAR
        if (from.isRemote() && to.isRemote()) {
            final FilePath tmpMasterFilePath = new FilePath(build.getRootDir()).createTempDir(ALLURE_PREFIX, null);
            from.copyRecursiveTo(tmpMasterFilePath);
            tmpMasterFilePath.copyRecursiveTo(to);
            deleteRecursive(tmpMasterFilePath, logger);
        } else {
            from.copyRecursiveTo(to);
        }
    }

    @SuppressWarnings("TrailingComment")
    public static void deleteRecursive(FilePath filePath, PrintStream logger) {
        try {
            filePath.deleteContents();
            filePath.deleteRecursive();
        } catch (IOException | InterruptedException e) { //NOSONAR
            logger.println(String.format("Can't delete directory [%s]", filePath));
        }
    }

    public static InputStream getFileInputStream(final FilePath path, final TaskListener listener) {
        try {
            return path.read();
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Unable to read file: " + e);
        }
        return null;
    }

    public static byte[] computeSha1Sum(final InputStream stream, final TaskListener listener) {
        try {
            MessageDigest md = getSha1Digest();
            try (DigestInputStream dis = new DigestInputStream(stream, md)) {
                while (dis.read(DEFAULT_BUFFER) != -1) ;
            }
            return md.digest();
        } catch (IOException e) {
            listener.getLogger().println("Unable to compute SHA-1 checksum for the report file: " + e);
        }
        return new byte[]{};
    }

    private static MessageDigest getSha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to get SHA-1 digest instance");
        }
    }
}
