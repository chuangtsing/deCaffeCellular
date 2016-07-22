package edu.psu.cse.vatest;

/**
 * Created by zblas_000 on 8/14/2015.
 */
public class VideoProcess {
    private Video video;

    public enum ProcessState {
        UNPROCESSED,
        EXTRACTING,
        CLASSIFYING,
        WAITING,
        UPLOADING,
        FINISHED;
    }

    public ProcessState state;
    public long maxProgress;
    public long progress;

    VideoProcess(Video video) {
        this.video = video;
        state = ProcessState.UNPROCESSED;
    }

    public Video getVideo() {
        return video;
    }
}
