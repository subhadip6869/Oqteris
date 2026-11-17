package app.vercel.originsd.oqteris.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SingleInstanceManagerTest {

    @BeforeEach
    public void setup() {
        SingleInstanceManager.reset();
    }

    @AfterEach
    public void cleanup() {
        SingleInstanceManager.reset();
    }

    @Test
    public void testSingleInstanceIPC() throws Exception {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (!samplePdf.exists()) return;

        List<File> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SingleInstanceManager.startServer(files -> {
            received.addAll(files);
            latch.countDown();
        });

        // Small pause to allow server socket to accept
        Thread.sleep(100);

        // Notify from secondary process simulation
        boolean handled = SingleInstanceManager.notifyRunningInstance(List.of(samplePdf.getAbsolutePath()));
        assertTrue(handled, "notifyRunningInstance should return true when primary instance is listening");

        boolean receivedInTime = latch.await(3, TimeUnit.SECONDS);
        assertTrue(receivedInTime, "Primary instance should receive files within timeout");
        assertEquals(1, received.size());
        assertEquals(samplePdf.getCanonicalPath(), received.get(0).getCanonicalPath());
    }

    @Test
    public void testNoInstanceRunning() {
        boolean handled = SingleInstanceManager.notifyRunningInstance(List.of("dummy.pdf"));
        assertFalse(handled, "Should return false when no instance is listening");
    }

    @Test
    public void testSingleInstanceIPCNonEnglishFilenames() throws Exception {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (!samplePdf.exists()) return;

        File targetDir = new File("target", "test-ipc-unicode");
        targetDir.mkdirs();

        File bengaliPdf = new File(targetDir, "বাংলা_নথি_রিপোর্ট.pdf");
        File japanesePdf = new File(targetDir, "日本語_サンプルの書類.pdf");
        java.nio.file.Files.copy(samplePdf.toPath(), bengaliPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.copy(samplePdf.toPath(), japanesePdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        List<File> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SingleInstanceManager.startServer(files -> {
            received.addAll(files);
            latch.countDown();
        });

        Thread.sleep(100);

        boolean handled = SingleInstanceManager.notifyRunningInstance(List.of(
                bengaliPdf.getAbsolutePath(),
                japanesePdf.getAbsolutePath()
        ));
        assertTrue(handled, "notifyRunningInstance should return true when primary instance is listening");

        boolean receivedInTime = latch.await(3, TimeUnit.SECONDS);
        assertTrue(receivedInTime, "Primary instance should receive non-English files within timeout");
        assertEquals(2, received.size());
        assertEquals(bengaliPdf.getCanonicalPath(), received.get(0).getCanonicalPath());
        assertEquals(japanesePdf.getCanonicalPath(), received.get(1).getCanonicalPath());

        bengaliPdf.delete();
        japanesePdf.delete();
    }

    @Test
    public void testSingleInstanceIPCMangledCliArguments() throws Exception {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (!samplePdf.exists()) return;

        File targetDir = new File("target", "test-ipc-mangled");
        targetDir.mkdirs();

        File unicodePdf = new File(targetDir, "বাংলা_নথি_ডকুমেন্ট.pdf");
        java.nio.file.Files.copy(samplePdf.toPath(), unicodePdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        StringBuilder mangled = new StringBuilder();
        for (char c : unicodePdf.getAbsolutePath().toCharArray()) {
            mangled.append(c < 128 ? c : '?');
        }

        List<File> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SingleInstanceManager.startServer(files -> {
            received.addAll(files);
            latch.countDown();
        });

        Thread.sleep(100);

        // Notify with simulated Windows mangled argument
        boolean handled = SingleInstanceManager.notifyRunningInstance(List.of(mangled.toString()));
        assertTrue(handled, "notifyRunningInstance should return true");

        boolean receivedInTime = latch.await(3, TimeUnit.SECONDS);
        assertTrue(receivedInTime, "Primary instance should receive resolved file within timeout");
        assertEquals(1, received.size());
        assertEquals(unicodePdf.getCanonicalPath(), received.get(0).getCanonicalPath());

        unicodePdf.delete();
    }
}
