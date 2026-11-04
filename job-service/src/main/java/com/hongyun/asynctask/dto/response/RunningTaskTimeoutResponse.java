package com.hongyun.asynctask.dto.response;

import java.util.List;

public class RunningTaskTimeoutResponse {

    private final int scannedCount;
    private final int timedOutCount;
    private final List<Long> timedOutTaskIds;

    public RunningTaskTimeoutResponse(
            int scannedCount,
            int timedOutCount,
            List<Long> timedOutTaskIds
    ) {
        this.scannedCount = scannedCount;
        this.timedOutCount = timedOutCount;
        this.timedOutTaskIds = timedOutTaskIds;
    }


    public int getScannedCount() {
        return scannedCount;
    }

    public int getTimedOutCount() {
        return timedOutCount;
    }

    public List<Long> getTimedOutTaskIds() {
        return timedOutTaskIds;
    }
}