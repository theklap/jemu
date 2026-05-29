package io.github.arkosammy12.jemu.frontend.audio;

import javax.sound.sampled.AudioFormat;

public class StereoAudioRenderer extends AudioRenderer {

    private static final int BYTES_PER_OUTPUT_SAMPLE = 4;

    public StereoAudioRenderer(int framerate) {
        super(framerate);
    }

    @Override
    protected AudioFormat getAudioFormat() {
        return new AudioFormat(SAMPLE_RATE, 16, 2, true, true);
    }

    @Override
    protected int getBytesPerOutputSample() {
        return BYTES_PER_OUTPUT_SAMPLE;
    }

}
