package com.calu;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CacheShredderTest {

    @Test
    public void testShredSingleFile() throws IOException {
        File tempFile = File.createTempFile("calu_test_cache", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("sensitive cached data to be shredded".getBytes());
        }
        Assert.assertTrue(tempFile.exists());
        Assert.assertTrue(tempFile.length() > 0);

        CacheShredder.shredFileOrDir(tempFile);

        Assert.assertFalse("File should be deleted after shredding", tempFile.exists());
    }

    @Test
    public void testShredDirectoryRecursively() throws IOException {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "calu_test_dir_" + System.nanoTime());
        Assert.assertTrue(tempDir.mkdirs());

        File subDir = new File(tempDir, "subdir");
        Assert.assertTrue(subDir.mkdirs());

        File file1 = new File(tempDir, "file1.cache");
        File file2 = new File(subDir, "file2.cache");

        try (FileOutputStream fos1 = new FileOutputStream(file1);
             FileOutputStream fos2 = new FileOutputStream(file2)) {
            fos1.write(new byte[]{1, 2, 3, 4, 5});
            fos2.write(new byte[]{6, 7, 8, 9, 10});
        }

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        CacheShredder.shredFileOrDir(tempDir);

        Assert.assertFalse("Directory should be deleted after recursive shred", tempDir.exists());
    }

    @Test
    public void testShredDirectoryContentsPreservesRoot() throws IOException {
        File cacheRoot = new File(System.getProperty("java.io.tmpdir"), "calu_cache_root_" + System.nanoTime());
        Assert.assertTrue(cacheRoot.mkdirs());

        File child1 = new File(cacheRoot, "entry1.bin");
        File child2 = new File(cacheRoot, "entry2.bin");

        try (FileOutputStream fos1 = new FileOutputStream(child1);
             FileOutputStream fos2 = new FileOutputStream(child2)) {
            fos1.write("data 1".getBytes());
            fos2.write("data 2".getBytes());
        }

        Assert.assertEquals(2, cacheRoot.listFiles().length);

        CacheShredder.shredDirectoryContents(cacheRoot);

        Assert.assertTrue("Root directory should still exist", cacheRoot.exists());
        File[] remaining = cacheRoot.listFiles();
        Assert.assertNotNull(remaining);
        Assert.assertEquals("All contents inside root should be shredded", 0, remaining.length);

        // Cleanup
        cacheRoot.delete();
    }
}
