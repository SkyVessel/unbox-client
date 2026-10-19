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
        failedWriteRetries(root);
        oldRetryDoesNotOverwriteReopenedWorld(root);
        damagedTileDoesNotStarveOtherTiles(root);
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

    private static void failedWriteRetries(Path root)throws Exception {
        Path blocked=root.resolve("blocked");java.nio.file.Files.createDirectories(root);java.nio.file.Files.writeString(blocked,"obstacle");
        MapTerrainStore store=new MapTerrainStore(blocked,"retry","overworld");store.capture(0,0,FIRST);store.flush(1);
        MapTerrainStore.finishWrites(); // First write fails because its parent is a file.
        store.capture(1,0,SECOND);store.close(); // Newer snapshot must survive replacement and close.
        java.nio.file.Files.delete(blocked);
        Thread.sleep(5500);MapTerrainStore.finishWrites();
        MapTerrainStore reopened=new MapTerrainStore(blocked,"retry","overworld");reopened.tick();reopened.color(0,0);MapTerrainStore.finishWrites();reopened.tick();
        check(reopened.color(0,0)==FIRST&&reopened.color(1,0)==SECOND,"Failed write lost cells on retry / close");reopened.close();
        System.out.println("PASS: failed disk write retries latest snapshot after close");
    }

    private static void oldRetryDoesNotOverwriteReopenedWorld(Path root)throws Exception {
        Path blocked=root.resolve("reopened-blocked");java.nio.file.Files.writeString(blocked,"obstacle");
        MapTerrainStore first=new MapTerrainStore(blocked,"world","overworld");first.capture(0,0,FIRST);first.capture(1,0,FIRST);first.close();MapTerrainStore.finishWrites();
        java.nio.file.Files.delete(blocked);
        MapTerrainStore second=new MapTerrainStore(blocked,"world","overworld");second.capture(0,0,SECOND);second.close();MapTerrainStore.finishWrites();
        Thread.sleep(5500);MapTerrainStore.finishWrites();
        MapTerrainStore reopened=new MapTerrainStore(blocked,"world","overworld");reopened.tick();reopened.color(0,0);MapTerrainStore.finishWrites();reopened.tick();
        check(reopened.color(0,0)==SECOND,"Old retry overwrote a newer world capture");check(reopened.color(1,0)==FIRST,"Reopening lost another pending cell");reopened.close();
        System.out.println("PASS: delayed retry cannot overwrite newer captures after reopening a world");
    }

    private static void damagedTileDoesNotStarveOtherTiles(Path root)throws Exception {
        MapTerrainStore store=new MapTerrainStore(root,"damaged-target","overworld");
        var field=MapTerrainStore.class.getDeclaredField("directory");field.setAccessible(true);Path directory=(Path)field.get(store);
        Path obstacle=directory.resolve("0_0.tile");java.nio.file.Files.createDirectories(obstacle);
        store.capture(0,0,FIRST);store.capture(16,0,SECOND);store.close();MapTerrainStore.finishWrites();
        Thread.sleep(5500);MapTerrainStore.finishWrites();
        check(java.nio.file.Files.isRegularFile(directory.resolve("1_0.tile")),"One damaged target starved a healthy tile");
        java.nio.file.Files.delete(obstacle);Thread.sleep(5500);MapTerrainStore.finishWrites();
        check(java.nio.file.Files.isRegularFile(obstacle),"Repaired target did not recover");
        System.out.println("PASS: a damaged target does not starve healthy tiles and recovers after repair");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
