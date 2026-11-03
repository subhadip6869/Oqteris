package org.originsd.oqteris.platform;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages single-instance lifecycle and inter-process communication (IPC)
 * to route newly opened PDF document requests to the active application window.
 * Uses a robust, zero-network file-based IPC queue that avoids OS firewall prompts.
 */
public class SingleInstanceManager {
    private static final String LOCK_FILE_NAME = ".instance.lock";
    private static final String REQUEST_DIR_NAME = "ipc_requests";

    private static ScheduledExecutorService poller;
    private static volatile boolean running = false;
    private static File activeBaseDir;

    public static synchronized File getBaseDir() {
        if (activeBaseDir != null) {
            return activeBaseDir;
        }

        String customDir = System.getProperty("oqteris.config.dir");
        if (customDir != null && !customDir.isBlank()) {
            File dir = new File(customDir, ".oqteris");
            dir.mkdirs();
            activeBaseDir = dir;
            return activeBaseDir;
        }

        // Try user.home/.oqteris first
        File dir = new File(System.getProperty("user.home", "."), ".oqteris");
        if (!isWritableDir(dir)) {
            dir = new File(System.getProperty("java.io.tmpdir", "."), ".oqteris");
            if (!isWritableDir(dir)) {
                dir = new File(".oqteris");
                dir.mkdirs();
            }
        }

        activeBaseDir = dir;
        return activeBaseDir;
    }

    private static boolean isWritableDir(File dir) {
        try {
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    return false;
                }
            }
            File testFile = new File(dir, ".test_write_" + System.currentTimeMillis());
            if (testFile.createNewFile()) {
                testFile.delete();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static synchronized void setCustomBaseDir(File dir) {
        activeBaseDir = dir;
    }

    public static synchronized void reset() {
        stopServer();
        activeBaseDir = null;
    }

    private static File getLockFile() {
        return new File(getBaseDir(), LOCK_FILE_NAME);
    }

    private static File getRequestsDir() {
        File dir = new File(getBaseDir(), REQUEST_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Checks if another instance is already running. If so, sends file paths via the IPC queue.
     */
    public static boolean notifyRunningInstance(List<String> args) {
        File lockFile = getLockFile();
        if (!lockFile.exists() || lockFile.length() == 0) {
            return false;
        }

        File requestsDir = getRequestsDir();
        String requestId = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File reqFile = new File(requestsDir, "request_" + requestId + ".req");
        File ackFile = new File(requestsDir, "request_" + requestId + ".ack");

        try (FileWriter writer = new FileWriter(reqFile)) {
            List<String> safeArgs = args != null ? args : List.of();
            for (String arg : safeArgs) {
                writer.write(arg + "\n");
            }
        } catch (Exception e) {
            cleanLockFile();
            return false;
        }

        // Wait up to 1500ms for primary instance to process and acknowledge the request
        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline) {
            if (ackFile.exists()) {
                try {
                    ackFile.delete();
                } catch (Exception ignored) {
                }
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Primary instance did not respond in time; clean up request and stale lock
        try {
            reqFile.delete();
        } catch (Exception ignored) {
        }
        cleanLockFile();
        return false;
    }

    /**
     * Starts the single-instance IPC listener for this primary application instance.
     */
    public static synchronized void startServer(Consumer<List<File>> onFilesReceived) {
        if (running) return;

        File lockFile = getLockFile();
        File parent = lockFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(lockFile)) {
            writer.write(ProcessHandle.current().pid() + "\n" + System.currentTimeMillis() + "\n");
        } catch (Exception e) {
            System.err.println("[SingleInstanceManager] Could not write lock file: " + e.getMessage());
            return;
        }

        running = true;

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceManager::stopServer, "Oqteris-IPC-Shutdown"));

        File requestsDir = getRequestsDir();

        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Oqteris-IPC-Poller");
            t.setDaemon(true);
            return t;
        });

        poller.scheduleWithFixedDelay(() -> {
            if (!running) return;
            try {
                File[] reqs = requestsDir.listFiles((d, name) -> name.startsWith("request_") && name.endsWith(".req"));
                if (reqs == null || reqs.length == 0) return;

                for (File req : reqs) {
                    List<File> files = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(new FileReader(req))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isBlank()) {
                                File f = PdfFileOpenHelper.validatePdfFile(line);
                                if (f != null) {
                                    files.add(f);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    // Write acknowledgment
                    String name = req.getName();
                    String ackName = name.substring(0, name.length() - 4) + ".ack";
                    File ack = new File(requestsDir, ackName);
                    try {
                        ack.createNewFile();
                        req.delete();
                    } catch (Exception ignored) {
                    }

                    if (onFilesReceived != null && !files.isEmpty()) {
                        onFilesReceived.accept(files);
                    }
                }
            } catch (Exception ignored) {
            }
        }, 50, 100, TimeUnit.MILLISECONDS);
    }

    public static synchronized void stopServer() {
        running = false;
        if (poller != null && !poller.isShutdown()) {
            poller.shutdownNow();
            poller = null;
        }
        cleanLockFile();
        cleanRequests();
    }

    private static void cleanLockFile() {
        try {
            File lock = getLockFile();
            if (lock != null && lock.exists()) {
                Files.deleteIfExists(lock.toPath());
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanRequests() {
        try {
            File dir = getRequestsDir();
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
