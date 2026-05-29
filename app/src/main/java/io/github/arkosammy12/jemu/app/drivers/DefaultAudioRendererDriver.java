package io.github.arkosammy12.jemu.app.drivers;

import io.github.arkosammy12.jemu.core.common.AudioGenerator;
import io.github.arkosammy12.jemu.core.drivers.AudioDriver;
import io.github.arkosammy12.jemu.frontend.audio.AudioRenderer;

import java.io.Closeable;
import java.util.Optional;

public abstract class DefaultAudioRendererDriver implements AudioDriver, Closeable {

    protected final AudioGenerator<?> audioGenerator;
    protected final AudioRenderer audioRenderer;

    public DefaultAudioRendererDriver(AudioGenerator<?> audioGenerator, AudioRenderer audioRenderer) {
        this.audioGenerator = audioGenerator;
        this.audioRenderer = audioRenderer;
    }

    public AudioRenderer getAudioRenderer() {
        return this.audioRenderer;
    }

    public void onFrame() {
        Optional<byte[]> optionalSamples = this.audioGenerator.getSampleFrame();
        if (optionalSamples.isEmpty()) {
            this.audioRenderer.pushSampleFrame(null);
            return;
        }
        this.audioRenderer.pushSampleFrame(this.convertBitDepthIfNecessary(optionalSamples.get()));
    }

    protected abstract byte[] convertBitDepthIfNecessary(byte[] buf);

}
