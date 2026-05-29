package io.github.arkosammy12.jemu.core.drivers;

public interface VideoDriver {

    /**
     * Outputs a completed frame for display.
     *
     * @param rgb the frame buffer in row-major order, where each element is a
     *            packed RGB integer in the format 0x00RRGGBB. The pixel at
     *            column {@code x}, row {@code y} is at index {@code y * width + x}.
     */
    void outputFrame(int[] rgb);

}
