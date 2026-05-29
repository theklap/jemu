package io.github.arkosammy12.jemu.app.drivers;

import io.github.arkosammy12.jemu.core.common.AudioGenerator;
import io.github.arkosammy12.jemu.frontend.audio.AudioRenderer;
import io.github.arkosammy12.jemu.frontend.audio.MonoAudioRenderer;

import java.io.IOException;
import java.util.Optional;

public class MonoAudioRendererDriver extends DefaultAudioRendererDriver {

    public MonoAudioRendererDriver(AudioGenerator<?> audioGenerator, MonoAudioRenderer audioRenderer) {
        super(audioGenerator, audioRenderer);
    }

    @Override
    public int getSampleRate() {
        return AudioRenderer.SAMPLE_RATE;
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
                for (int i = 0; i < buf.length; i++) {
                    int sample16 = ((int) buf[i] & 0xFF) * 256;
                    buf16[i * 2] = (byte) ((sample16 & 0xFF00) >>> 8);
                    buf16[(i * 2) + 1] = (byte) (sample16 & 0xFF);
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
