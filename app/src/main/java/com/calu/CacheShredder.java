package com.calu;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;

/**
 * Manages 24-hour periodic secure shredding of app cache directories.
 */
public final class CacheShredder {
    public static final int JOB_ID = 2401;
    public static final long INTERVAL_MS = 24L * 60L * 60L * 1000L; // 24 hours
    private static final String PREFS_NAME = "calu_cache_shredder";
    private static final String KEY_LAST_SHRED = "last_shred_timestamp";

    private CacheShredder() {}

    /**
     * Checks if 24 hours have passed since the last shred. If so, triggers an asynchronous shred.
     */
    public static void checkAndShred(final Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastShred = prefs.getLong(KEY_LAST_SHRED, 0L);
        long now = System.currentTimeMillis();

        if (now - lastShred >= INTERVAL_MS) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    shredAll(appCtx);
                }
            }).start();
        }
    }

    /**
     * Schedules a recurring JobScheduler task that runs every 24 hours (persisted across reboots).
     */
    public static void schedulePeriodicJob(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        JobScheduler js = (JobScheduler) appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        // Check if job is already scheduled
        if (Build.VERSION.SDK_INT >= 24) {
            JobInfo existing = js.getPendingJob(JOB_ID);
            if (existing != null) return;
        } else {
            for (JobInfo job : js.getAllPendingJobs()) {
                if (job.getId() == JOB_ID) return;
            }
        }

        ComponentName serviceComponent = new ComponentName(appCtx, CacheShredderJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
        if (Build.VERSION.SDK_INT >= 24) {
            // Periodic with 4-hour flex window
            builder.setPeriodic(INTERVAL_MS, 4L * 60L * 60L * 1000L);
        } else {
            builder.setPeriodic(INTERVAL_MS);
        }
        builder.setPersisted(true);

        try {
            js.schedule(builder.build());
        } catch (Exception ignored) {
        }
    }

    /**
     * Securely shreds all cache files and directories in internal and external cache.
     */
    public static void shredAll(Context context) {
        if (context == null) return;
        try {
            File internalCache = context.getCacheDir();
            if (internalCache != null && internalCache.exists()) {
                shredDirectoryContents(internalCache);
            }

            File externalCache = context.getExternalCacheDir();
            if (externalCache != null && externalCache.exists()) {
                shredDirectoryContents(externalCache);
            }

            // Update timestamp after successful shred
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_SHRED, System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Recursively shreds and deletes contents of a directory while preserving the root directory itself.
     */
    public static void shredDirectoryContents(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                shredFileOrDir(child);
            }
        }
    }

    /**
     * Securely overwrites a file with random bytes and zeros before deleting it.
     */
    public static void shredFileOrDir(File file) {
        if (file == null || !file.exists()) return;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    shredFileOrDir(child);
                }
            }
            file.delete();
            return;
        }

        long length = file.length();
        if (length > 0 && file.canWrite()) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
                byte[] buffer = new byte[(int) Math.min(length, 4096)];
                Random rnd = new Random();

                // Pass 1: overwrite with random noise
                long written = 0;
                while (written < length) {
                    rnd.nextBytes(buffer);
                    int toWrite = (int) Math.min(buffer.length, length - written);
                    raf.write(buffer, 0, toWrite);
                    written += toWrite;
                }
                raf.getFD().sync();

                // Pass 2: overwrite with zero bytes
                Arrays.fill(buffer, (byte) 0);
                raf.seek(0);
                written = 0;
                while (written < length) {
                    int toWrite = (int) Math.min(buffer.length, length - written);
                    raf.write(buffer, 0, toWrite);
                    written += toWrite;
                }
                raf.getFD().sync();
            } catch (Exception ignored) {
            }
        }
        file.delete();
    }
}
