package io.github.arkosammy12.jemu.app.drivers;

import io.github.arkosammy12.jemu.core.common.AudioGenerator;
import io.github.arkosammy12.jemu.frontend.audio.StereoAudioRenderer;

import java.io.IOException;
import java.util.Optional;

public class StereoAudioRendererDriver extends DefaultAudioRendererDriver {

    public StereoAudioRendererDriver(AudioGenerator<?> audioGenerator, StereoAudioRenderer audioRenderer) {
        super(audioGenerator, audioRenderer);
    }

    @Override
    public int getSampleRate() {
        return this.audioRenderer.getSampleRate();
    }

    @Override
    public int getSamplesPerFrame() {
        return this.audioRenderer.getSamplesPerFrame();
    }

    @Override
    protected byte[] convertBitDepthIfNecessary(byte[] buf) {
        return switch (this.audioGenerator.getBytesPerSample()) {
            case BYTES_1 -> {
                byte[] buf16 = new byte[this.audioRenderer.getBytesPerFrame()];

                int frames = buf.length / 2;
                for (int i = 0; i < frames; i++) {
                    int sample16Left = ((int) buf[i * 2] & 0xFF) << 8;
                    int sample16Right = ((int) buf[(i * 2) + 1] & 0xFF) << 8;
                    buf16[i * 4] = (byte) ((sample16Left & 0xFF00) >>> 8);
                    buf16[(i * 4) + 1] = (byte) (sample16Left & 0xFF);
                    buf16[(i * 4) + 2] = (byte) ((sample16Right & 0xFF00) >>> 8);
                    buf16[(i * 4) + 3] = (byte) (sample16Right & 0xFF);
                }
                yield buf16;
            }
            case BYTES_2 -> buf;
        };
    }

    @Override
    public void close() throws IOException {
        if (this.audioRenderer != null) {
            this.audioRenderer.close();
        }
    }

}
