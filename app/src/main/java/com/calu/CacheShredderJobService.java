package com.calu;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Background JobService executed every 24 hours by Android JobScheduler.
 */
public class CacheShredderJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CacheShredder.shredAll(getApplicationContext());
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true; // Work is running asynchronously on background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false; // Do not reschedule immediately if cancelled
    }
}
