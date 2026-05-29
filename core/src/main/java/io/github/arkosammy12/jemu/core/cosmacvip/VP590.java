package io.github.arkosammy12.jemu.core.cosmacvip;

import java.util.Arrays;

public class  VP590<E extends CosmacVIPEmulator> extends CDP1861<E> {

    private static final int[] BACKGROUND_COLORS = {
            0x000080,
            0x000000,
            0x008000,
            0X800000
    };

    private final byte[] colorRam = new byte[256];
    private int backgroundColorIndex = 0;
    private boolean hiresColor = false;
    private boolean colorRamModified = false;

    public VP590(E emulator) {
        super(emulator);
        Arrays.fill(this.colorRam, (byte) 0xF0);
    }

    public void writeColorRam(int address, int value) {
        this.colorRamModified = true;
        if ((address >= 0xC000 && address <= 0xCFFF) || (address >= 0xE000 && address <= 0xEFFF)) {
            hiresColor = false;
        } else if ((address >= 0xD000 && address <= 0xDFFF) || (address >= 0xF000 && address <= 0xFFFF)) {
            hiresColor = true;
        }
        this.colorRam[address & (this.hiresColor ? 0xFF : 0xE7)] = (byte) (0xF0 | (value & 7));
    }

    public int readColorRam(int address) {
        return (int) this.colorRam[address & (this.hiresColor ? 0xFF : 0xE7)] & 0xFF;
    }

    public void incrementBackgroundColorIndex() {
        this.backgroundColorIndex = (this.backgroundColorIndex + 1) % BACKGROUND_COLORS.length;
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public void onDMAOUT(int dmaOutAddress, int value) {
        if (!this.emulator.getCpu().getCurrentState().isS2Dma()) {
            return;
        }
        int row = this.scanlineIndex - DISPLAY_AREA_BEGIN;
        if (row < 0 || row >= this.getImageHeight()) {
            return;
        }
        int dmaIndex = (int) ((this.cycles % MACHINE_CYCLES_PER_SCANLINE) - DMAO_BEGIN);
        int colStart = dmaIndex * 8;
        int color = 0;
        int backgroundColor;
        if (this.colorRamModified) {
            backgroundColor = BACKGROUND_COLORS[backgroundColorIndex];
            int colorByte = this.readColorRam(dmaOutAddress);
            if ((colorByte & 1) != 0) {
                color |= 0xFF0000;
            }
            if ((colorByte & 0b100) != 0) {
                color |= 0x00FF00;
            }
            if ((colorByte & 0b10) != 0) {
                color |= 0x0000FF;
            }
        } else {
            backgroundColor = 0x000080;
            color = 0xFFFFFF;
        }
        for (int i = 0, mask = 0x80; i < 8; i++, mask >>>= 1) {
            int col = colStart + i;
            if (col < 0 || col >= 64) {
                break;
            }
            for (int j = 0; j < 4; j++) {
                this.displayBuffer[(row * IMAGE_WIDTH) + (col * 4) + j] = (value & mask) != 0 ? color : backgroundColor;
            }
        }
    }

}