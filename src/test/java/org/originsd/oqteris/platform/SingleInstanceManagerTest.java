package org.originsd.oqteris.platform;

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
}
