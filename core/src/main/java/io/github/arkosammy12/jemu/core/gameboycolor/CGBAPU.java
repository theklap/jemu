package io.github.arkosammy12.jemu.core.gameboycolor;

import io.github.arkosammy12.jemu.core.gameboy.DMGAPU;

public class CGBAPU<E extends GameBoyColorEmulator> extends DMGAPU<E> {

    public CGBAPU(E emulator) {
        super(emulator);
    }

    @Override
    protected CGBAPU<?>.Channel3 createChannel3() {
        return this.new Channel3();
    }

    @Override
    protected void onApuOn() {
        super.onApuOn();
        this.channel1.lengthTimer = 0;
        this.channel2.lengthTimer = 0;
        this.channel3.lengthTimer = 0;
        this.channel4.lengthTimer = 0;
    }

    protected class Channel3 extends DMGAPU<?>.Channel3 {

        private boolean triggeredThisCycle = false;

        @Override
        protected int readWaveRAM(int address) {
            this.firstFetchConsumed = this.fetchedFirstByte;
            if (this.getEnabled()) {
                return (int) this.waveRAM[((this.waveRamIndex - 1) & 31) / 2] & 0xFF;
            } else {
                return (int) this.waveRAM[address - WAVERAM_START] & 0xFF;
            }
        }

        @Override
        protected void writeWaveRAM(int address, int value) {
            this.firstFetchConsumed = this.fetchedFirstByte;
            if (this.getEnabled()) {
                this.waveRAM[((this.waveRamIndex - 1) & 31) / 2] = (byte) value;
            } else {
                this.waveRAM[address - WAVERAM_START] = (byte) value;
            }
        }

        @Override
        protected void checkWaveRamCorruption() {
            // No wave ram corruption on CGB
        }

        @Override
        protected int tick() {
            if (!this.getEnabled() || this.triggeredThisCycle) {
                this.triggeredThisCycle = false;
                int amplitude;
                if (this.waveRamIndex % 2 == 0) {
                    amplitude = (this.waveSampleBuffer >>> 4) & 0xF;
                } else {
                    amplitude = this.waveSampleBuffer & 0xF;
                }
                return amplitude >>> this.getShiftAmount();
            }
            return super.tick();
        }

        @Override
        protected void trigger() {
            this.triggeredThisCycle = true;
            super.trigger();
            this.wavePeriodTimer = 4;
        }

    }

}
