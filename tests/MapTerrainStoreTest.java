package dev.unbox.client;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Standalone regression checks against the production cache, with disposable storage. */
public final class MapTerrainStoreTest {
    private static final int FIRST = 0xff123456, SECOND = 0xff654321;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        maximumViewportRetainsVisibleTiles(root);
        evictionPreservesPendingCells(root);
    }

    private static void maximumViewportRetainsVisibleTiles(Path root) {
        MapTerrainStore store = new MapTerrainStore(root, "viewport", "overworld");
        try {
            var visible = new LinkedHashMap<Long, MapTerrainStore.Tile>();
            // A 2048 x 1024 view touches up to 17 x 9 regions, each holding 8 x 8 chunks.
            for (int rz = 0; rz < 9; rz++) for (int rx = 0; rx < 17; rx++) {
                for (int tz = 0; tz < 8; tz++) for (int tx = 0; tx < 8; tx++) {
                    int x = rx * 8 + tx, z = rz * 8 + tz;
                    visible.put(MapTerrainStore.key(x, z), store.chunk(x, z));
                }
            }
            check(visible.size() == 9792, "Maximum viewport fixture must cover 9792 tiles");
            for (var entry : visible.entrySet()) {
                long key = entry.getKey();
                check(store.chunk((int) (key >> 32), (int) key) == entry.getValue(),
                    "Still-visible tile was evicted from the maximum viewport: " + key);
            }
            System.out.println("PASS: all 9792 maximum-viewport tiles remain cached");
        } finally {
            store.close();
        }
    }

    private static void evictionPreservesPendingCells(Path root) throws Exception {
        var ioField = MapTerrainStore.class.getDeclaredField("IO");
        ioField.setAccessible(true);
        var io = (ExecutorService) ioField.get(null);
        var limitField = MapTerrainStore.class.getDeclaredField("LIMIT");
        limitField.setAccessible(true);
        int limit = limitField.getInt(null);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        io.submit(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        MapTerrainStore store = new MapTerrainStore(root, "pending-write", "overworld");
        try {
            check(entered.await(5, TimeUnit.SECONDS), "Could not hold the disk worker");
            store.capture(0, 0, FIRST);
            var original = store.chunk(0, 0);
            store.flush(1);
            // No tick/read budget: nothing can merge the pending snapshot into the new tile.
            for (int x = 1; x <= limit; x++) store.chunk(x, 0);
            check(store.chunk(0, 0) != original, "Fixture must force eviction at the current cache limit");
            store.capture(1, 0, SECOND);
            store.flush(1);
        } finally {
            store.close();
            release.countDown();
        }
        io.submit(() -> {}).get(10, TimeUnit.SECONDS);

        MapTerrainStore reopened = new MapTerrainStore(root, "pending-write", "overworld");
        try {
            reopened.tick();
            reopened.color(0, 0);
            io.submit(() -> {}).get(10, TimeUnit.SECONDS);
            reopened.tick();
            check(reopened.color(0, 0) == FIRST, "First captured cell disappeared after pending-write replacement");
            check(reopened.color(1, 0) == SECOND, "Second captured cell did not survive close/reopen");
            System.out.println("PASS: eviction at limit " + limit + " preserves both pending cells after reopen");
        } finally {
            reopened.close();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
