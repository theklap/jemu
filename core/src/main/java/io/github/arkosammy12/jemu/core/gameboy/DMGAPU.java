package io.github.arkosammy12.jemu.core.gameboy;

import io.github.arkosammy12.jemu.core.common.Bus;
import io.github.arkosammy12.jemu.core.common.AudioGenerator;
import io.github.arkosammy12.jemu.core.drivers.AudioDriver;
import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static io.github.arkosammy12.jemu.core.common.SystemHost.intToByteArray;

public class DMGAPU<E extends GameBoyEmulator> extends AudioGenerator<E> implements Bus {

    public static final int NR10_ADDR = 0xFF10;
    public static final int NR11_ADDR = 0xFF11;
    public static final int NR12_ADDR = 0xFF12;
    public static final int NR13_ADDR = 0xFF13;
    public static final int NR14_ADDR = 0xFF14;

    public static final int NR21_ADDR = 0xFF16;
    public static final int NR22_ADDR = 0xFF17;
    public static final int NR23_ADDR = 0xFF18;
    public static final int NR24_ADDR = 0xFF19;
    public static final int NR30_ADDR = 0xFF1A;
    public static final int NR31_ADDR = 0xFF1B;
    public static final int NR32_ADDR = 0xFF1C;
    public static final int NR33_ADDR = 0xFF1D;
    public static final int NR34_ADDR = 0xFF1E;

    public static final int NR41_ADDR = 0xFF20;
    public static final int NR42_ADDR = 0xFF21;
    public static final int NR43_ADDR = 0xFF22;
    public static final int NR44_ADDR = 0xFF23;
    public static final int NR50_ADDR = 0xFF24;
    public static final int NR51_ADDR = 0xFF25;
    public static final int NR52_ADDR = 0xFF26;

    public static final int WAVERAM_START = 0xFF30;
    public static final int WAVERAM_END = 0xFF3F;

    private static final int UNUSED_BITS_NR10 = 0b10000000;
    private static final int UNUSED_BITS_NRX1 = 0b00111111;
    private static final int UNUSED_BITS_NRX4 = 0b10111111;
    private static final int UNUSED_BITS_NR30 = 0b01111111;
    private static final int UNUSED_BITS_NR32 = 0b10011111;
    private static final int UNUSED_BITS_NR52 = 0b01110000;

    private static final double MAX_VOLUME = 15.0f;
    private static final double SAMPLE_SCALE = Short.MAX_VALUE;
    private static final double HIGH_PASS_CAPACITOR_CONSTANT = 0.999958;
    private static final double LOW_PASS_CAPACITOR_CONSTANT = 0.01664;

    private final short[] leftChannelSamples = new short[GameBoyEmulator.T_CYCLES_PER_FRAME];
    private final short[] rightChannelSamples = new short[GameBoyEmulator.T_CYCLES_PER_FRAME];
    private int currentSampleIndex = 0;

    private int frameSequencerStep;

    protected int nr50;
    protected int nr51;
    private int nr52;

    protected final Channel1 channel1 = new Channel1();
    protected final Channel2 channel2 = new Channel2();
    protected final DMGAPU<?>.Channel3 channel3;
    protected final Channel4 channel4 = new Channel4();

    private double leftHighPassFilterCapacitor = 0;
    private double rightHighPassFilterCapacitor = 0;

    private double leftLowPassFilterCapacitor = 0;
    private double rightLowPassFilterCapacitor = 0;

    public DMGAPU(E emulator) {
        super(emulator);
        this.channel3 = this.createChannel3();
    }

    protected DMGAPU<?>.Channel3 createChannel3() {
        return new Channel3();
    }

    @Override
    public int readByte(int address) {
        if (address >= WAVERAM_START && address <= WAVERAM_END) {
            return this.channel3.readWaveRAM(address);
        } else {
            return switch (address) {
                case NR13_ADDR, NR23_ADDR, NR31_ADDR, NR33_ADDR, NR41_ADDR -> 0xFF;
                case NR10_ADDR -> this.channel1.getNR10() | UNUSED_BITS_NR10;
                case NR11_ADDR -> this.channel1.getNRX1() | UNUSED_BITS_NRX1;
                case NR12_ADDR -> this.channel1.getNRX2();
                case NR14_ADDR -> this.channel1.getNRX4() | UNUSED_BITS_NRX4;
                case NR21_ADDR -> this.channel2.getNRX1() | UNUSED_BITS_NRX1;
                case NR22_ADDR -> this.channel2.getNRX2();
                case NR24_ADDR -> this.channel2.getNRX4() | UNUSED_BITS_NRX4;
                case NR30_ADDR -> this.channel3.getNR30() | UNUSED_BITS_NR30;
                case NR32_ADDR -> this.channel3.getNRX2() | UNUSED_BITS_NR32;
                case NR34_ADDR -> this.channel3.getNRX4() | UNUSED_BITS_NRX4;
                case NR42_ADDR -> this.channel4.getNRX2();
                case NR43_ADDR -> this.channel4.getNRX3();
                case NR44_ADDR -> this.channel4.getNRX4() | UNUSED_BITS_NRX4;
                case NR50_ADDR -> this.nr50;
                case NR51_ADDR -> this.nr51;
                case NR52_ADDR -> this.nr52 | UNUSED_BITS_NR52;
                default -> throw new EmulatorException("Invalid address $%04X for GameBoy APU!".formatted(address));
            };
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= WAVERAM_START && address <= WAVERAM_END) {
            this.channel3.writeWaveRAM(address, value);
        } else if (this.getMasterAudioEnable() || address == NR52_ADDR || address == NR11_ADDR || address == NR21_ADDR || address == NR31_ADDR || address == NR41_ADDR) {
            switch (address) {
                case NR10_ADDR -> this.channel1.setNR10(value);
                case NR11_ADDR -> this.channel1.setNRX1(value);
                case NR12_ADDR -> this.channel1.setNRX2(value);
                case NR13_ADDR -> this.channel1.setNRX3(value);
                case NR14_ADDR -> this.channel1.setNRX4(value);
                case NR21_ADDR -> this.channel2.setNRX1(value);
                case NR22_ADDR -> this.channel2.setNRX2(value);
                case NR23_ADDR -> this.channel2.setNRX3(value);
                case NR24_ADDR -> this.channel2.setNRX4(value);
                case NR30_ADDR -> this.channel3.setNR30(value);
                case NR31_ADDR -> this.channel3.setNRX1(value);
                case NR32_ADDR -> this.channel3.setNRX2(value);
                case NR33_ADDR -> this.channel3.setNRX3(value);
                case NR34_ADDR -> this.channel3.setNRX4(value);
                case NR41_ADDR -> this.channel4.setNRX1(value);
                case NR42_ADDR -> this.channel4.setNRX2(value);
                case NR43_ADDR -> this.channel4.setNRX3(value);
                case NR44_ADDR -> this.channel4.setNRX4(value);
                case NR50_ADDR -> this.nr50 = value & 0xFF;
                case NR51_ADDR -> this.nr51 = value & 0xFF;
                case NR52_ADDR -> {
                    boolean oldApuPower = this.getMasterAudioEnable();
                    this.nr52 = (value & 0b10000000) | (this.nr52 & 0b00001111);
                    boolean newApuPower = this.getMasterAudioEnable();

                    if (!oldApuPower && newApuPower) {
                        this.onAPUOn();
                    } else if (oldApuPower && !newApuPower) {
                        this.onAPUOff();
                    }
                }
                default -> throw new EmulatorException("Invalid address $%04X for GameBoy APU!".formatted(address));
            }
        }
    }

    protected void onAPUOn() {
        this.emulator.getTimerController().onAPUPowerOn();
        this.frameSequencerStep = 0;
        this.channel1.waveDutyIndex = 0;
        this.channel2.waveDutyIndex = 0;

        this.channel3.waveSampleBuffer = 0;
        this.channel3.fetchedFirstByte = false;
        this.channel3.firstFetchConsumed = false;
    }

    @SuppressWarnings("DuplicatedCode")
    protected void onAPUOff() {
        this.channel1.nr10 = 0;
        this.channel1.nrx1 = 0;
        this.channel1.setNRX2(0);
        this.channel1.nrx3 = 0;
        this.channel1.nrx4 = 0;

        this.channel2.nrx1 = 0;
        this.channel2.setNRX2(0);
        this.channel2.nrx3 = 0;
        this.channel2.nrx4 = 0;

        this.channel3.setNR30(0);
        this.channel3.nrx1 = 0;
        this.channel3.nrx2 = 0;
        this.channel3.nrx3 = 0;
        this.channel3.nrx4 = 0;

        this.channel4.nrx1 = 0;
        this.channel4.setNRX2(0);
        this.channel4.nrx3 = 0;
        this.channel4.nrx4 = 0;

        this.nr50 = 0;
        this.nr51 = 0;
    }

    @Override
    public boolean isStereo() {
        return true;
    }

    @Override
    public AudioGenerator.@NotNull SampleSize getBytesPerSample() {
        return SampleSize.BYTES_2;
    }

    @Override
    public Optional<byte[]> getSampleFrame() {
        Optional<? extends AudioDriver> optionalAudioDriver = this.emulator.getHost().getAudioDriver();
        if (optionalAudioDriver.isEmpty()) {
            return Optional.empty();
        }

        AudioDriver audioDriver = optionalAudioDriver.get();
        int samplesPerFrame = audioDriver.getSamplesPerFrame();

        byte[] out = new byte[samplesPerFrame * 4];
        double step = (double) GameBoyEmulator.T_CYCLES_PER_FRAME / (double) samplesPerFrame;
        double pos = 0.0;

        for (int i = 0; i < samplesPerFrame; i++) {
            int index = Math.toIntExact(Math.round(pos));
            int nextIndex = Math.min(index + 1, GameBoyEmulator.T_CYCLES_PER_FRAME - 1);

            short left = this.leftChannelSamples[nextIndex];
            short right = this.rightChannelSamples[nextIndex];

            out[i * 4] = (byte) (((int) left >> 8) & 0xFF);
            out[(i * 4) + 1] = (byte) ((int) left & 0xFF);
            out[(i * 4) + 2] = (byte) (((int) right >> 8) & 0xFF);
            out[(i * 4) + 3] = (byte) ((int) right & 0xFF);

            pos += step;
        }
        this.currentSampleIndex = 0;
        return Optional.of(out);
    }

    public void cycle(boolean tickFrameSequencer) {
        if (tickFrameSequencer) {
            this.tickFrameSequencer();
        }
        for (int i = 0; i < 4; i++) {
            double ch1 = 0;
            double ch2 = 0;
            double ch3 = 0;
            double ch4 = 0;
            if (this.getMasterAudioEnable()) {
                ch1 = (double) this.channel1.tick();
                ch2 = (double) this.channel2.tick();
                ch3 = (double) this.channel3.tick();
                ch4 = (double) this.channel4.tick();
            }

            ch1 = ((ch1 - ((double) this.channel1.envelopeCurrentVolume / 2.0)) / MAX_VOLUME);
            ch2 = ((ch2 - ((double) this.channel2.envelopeCurrentVolume / 2.0)) / MAX_VOLUME);

            ch3 /= MAX_VOLUME;
            ch4 = (double) ((ch4 - ((double) this.channel4.envelopeCurrentVolume / 2.0)) / MAX_VOLUME);

            double left = 0;
            double right = 0;

            if (this.channel1.getLeft()) {
                left += ch1;
            }
            if (this.channel2.getLeft()) {
                left += ch2;
            }
            if (this.channel3.getLeft()) {
                left += ch3;
            }
            if (this.channel4.getLeft()) {
                left += ch4;
            }

            if (this.channel1.getRight()) {
                right += ch1;
            }
            if (this.channel2.getRight()) {
                right += ch2;
            }
            if (this.channel3.getRight()) {
                right += ch3;
            }
            if (this.channel4.getRight()) {
                right += ch4;
            }

            left *= (double) -1 * (double) (this.getLeftVolume() + 1) / 8.0f;
            right *= (double) -1 * (double) (this.getRightVolume() + 1) / 8.0f;

            left /= 4.0f;
            right /= 4.0f;

            boolean dacEnable = this.channel1.getDacEnable() || this.channel2.getDacEnable() || this.channel3.getDacEnable() || this.channel4.getDacEnable();

            this.leftChannelSamples[this.currentSampleIndex] = (short) Math.clamp((long)(this.leftBandPass(left, dacEnable) * SAMPLE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE);
            this.rightChannelSamples[this.currentSampleIndex] = (short) Math.clamp((long)(this.rightBandPass(right, dacEnable) * SAMPLE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE);
            this.currentSampleIndex = (this.currentSampleIndex + 1) % GameBoyEmulator.T_CYCLES_PER_FRAME;
        }
    }

    private double leftBandPass(double in, boolean dacEnable) {
        if (dacEnable) {
            this.leftHighPassFilterCapacitor = in - (in - this.leftHighPassFilterCapacitor) * HIGH_PASS_CAPACITOR_CONSTANT;
            double out = this.leftLowPassFilterCapacitor + LOW_PASS_CAPACITOR_CONSTANT * ((in - this.leftHighPassFilterCapacitor) - this.leftLowPassFilterCapacitor);
            this.leftLowPassFilterCapacitor = out;
            return out;
        } else {
            this.leftLowPassFilterCapacitor = 0;
            this.leftHighPassFilterCapacitor = 0;
            return 0;
        }
    }

    private double rightBandPass(double in, boolean dacEnable) {
        if (dacEnable) {
            this.rightHighPassFilterCapacitor = in - (in - this.rightHighPassFilterCapacitor) * HIGH_PASS_CAPACITOR_CONSTANT;
            double out = this.rightLowPassFilterCapacitor + LOW_PASS_CAPACITOR_CONSTANT * ((in - this.rightHighPassFilterCapacitor) - this.rightLowPassFilterCapacitor);
            this.rightLowPassFilterCapacitor = out;
            return out;
        } else {
            this.rightLowPassFilterCapacitor = 0;
            this.rightHighPassFilterCapacitor = 0;
            return 0;
        }
    }

    private void tickFrameSequencer() {
        if (!this.getMasterAudioEnable()) {
            return;
        }

        switch (this.frameSequencerStep) {
            case 0, 2, 4, 6 -> {
                this.channel1.clockLength();
                this.channel2.clockLength();
                this.channel3.clockLength();
                this.channel4.clockLength();
            }
        }

        if (this.frameSequencerStep == 7) {
            this.channel1.clockEnvelope();
            this.channel2.clockEnvelope();
            this.channel4.clockEnvelope();
        }

        if (this.frameSequencerStep == 2 || this.frameSequencerStep == 6) {
            this.channel1.clockSweep();
        }

        this.frameSequencerStep = (this.frameSequencerStep + 1) & 7;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isLengthClockStep() {
        return this.frameSequencerStep == 0 || this.frameSequencerStep == 2 || this.frameSequencerStep == 4 || this.frameSequencerStep == 6;
    }

    private boolean getMasterAudioEnable() {
        return (this.nr52 & (1 << 7)) != 0;
    }

    private int getLeftVolume() {
        return (this.nr50 >>> 4) & 0b111;
    }

    private int getRightVolume() {
        return this.nr50 & 0b111;
    }

    protected abstract class AudioChannel {

        protected int nrx1;
        protected int nrx2;
        protected int nrx3;
        protected int nrx4;

        public int lengthTimer;

        abstract protected void setEnabled(boolean enable);

        abstract protected boolean getEnabled();

        abstract protected boolean getLeft();

        abstract protected boolean getRight();

        protected void setNRX1(int value) {
            if (getMasterAudioEnable()) {
                this.nrx1 = value & 0xFF;
            }
        }

        protected int getNRX1() {
            return this.nrx1;
        }

        protected void setNRX2(int value) {
            this.nrx2 = value & 0xFF;
        }

        protected int getNRX2() {
            return this.nrx2;
        }

        protected void setNRX3(int value) {
            this.nrx3 = value & 0xFF;
        }

        protected int getNRX3() {
            return this.nrx3;
        }

        protected void setNRX4(int value) {

            boolean oldEnable = getLengthEnable();
            this.nrx4 = value & 0xFF;
            boolean newEnable = getLengthEnable();

            if (!oldEnable && newEnable && !isLengthClockStep()) {
                this.clockLength();
            }

            if (this.getTrigger()) {
                this.trigger();
            }
        }

        protected int getNRX4() {
            return this.nrx4;
        }

        protected boolean getTrigger() {
            return (this.nrx4 & (1 << 7)) != 0;
        }

        protected boolean getLengthEnable() {
            return (this.nrx4 & (1 << 6)) != 0;
        }

        abstract protected boolean getDacEnable();

        protected int getMaxLengthTimer() {
            return 64;
        }

        abstract protected int tick();

        protected void trigger() {
            if (this.getDacEnable()) {
                this.setEnabled(true);
            }
            if (this.lengthTimer == 0) {
                this.lengthTimer = this.getMaxLengthTimer();
                if (this.getLengthEnable() && !isLengthClockStep()) {
                    this.clockLength();
                }
            }

        }

        protected void clockLength() {
            if (!this.getLengthEnable()) {
                return;
            }
            if (this.lengthTimer <= 0) {
                return;
            }
            this.lengthTimer--;
            if (this.lengthTimer <= 0) {
                this.setEnabled(false);
            }
        }

    }

    protected class Channel2 extends AudioChannel {

        protected static final int[][] DUTY_CYCLES = {
                {0, 0, 0, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0, 1, 1},
                {1, 0, 0, 0, 0, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 0, 0}
        };

        protected int waveDutyIndex;
        protected int wavePeriodTimer;

        private int envelopePeriodTimer;
        protected int envelopeCurrentVolume;
        private boolean envelopeUpdating;

        @Override
        protected void setEnabled(boolean enable) {
            if (enable) {
                nr52 |= 1 << 1;
            } else {
                nr52 &= ~(1 << 1);
            }
        }

        @Override
        protected boolean getEnabled() {
            return (nr52 & (1 << 1)) != 0;
        }

        @Override
        protected boolean getLeft() {
            return (nr51 & (1 << 5)) != 0;
        }

        @Override
        protected boolean getRight() {
            return (nr51 & (1 << 1)) != 0;
        }

        private int getWaveDuty() {
            return (this.nrx1 >>> 6) & 0b11;
        }

        @Override
        protected void setNRX1(int value) {
            super.setNRX1(value);
            this.lengthTimer = 64 - (value & 0x3F);
        }

        public void setNRX2(int value) {

            int oldEnvelopeSweepPace = this.getEnvelopeSweepPace();
            boolean oldEnvelopeDirection = this.getEnvelopeDirection();

            super.setNRX2(value);
            if (!this.getDacEnable()) {
                this.setEnabled(false);
            }

            if (this.getEnabled()) {
                if (oldEnvelopeSweepPace == 0 && this.envelopeUpdating) {
                    this.envelopeCurrentVolume++;
                } else if (!oldEnvelopeDirection) {
                    this.envelopeCurrentVolume += 2;
                }
                if (oldEnvelopeDirection != this.getEnvelopeDirection()) {
                    this.envelopeCurrentVolume = 16 - this.envelopeCurrentVolume;
                }
                this.envelopeCurrentVolume &= 0xF;
            }
        }

        @Override
        protected boolean getDacEnable() {
            return (this.nrx2 & 0xF8) != 0;
        }

        private int getInitialVolume() {
            return (this.nrx2 >>> 4) & 0b1111;
        }

        private boolean getEnvelopeDirection() {
            return (this.nrx2 & (1 << 3)) != 0;
        }

        private int getEnvelopeSweepPace() {
            return this.nrx2 & 0b111;
        }

        int getPeriodFull() {
            return ((this.nrx4 & 0b111) << 8) | this.nrx3;
        }

        @Override
        protected int tick() {
            if (!this.getEnabled()) {
                if (this.getDacEnable()) {
                    return 0xF;
                } else {
                    return 0;
                }
            }
            this.wavePeriodTimer--;
            int period = this.getPeriodFull();
            if (this.wavePeriodTimer <= 0) {
                this.wavePeriodTimer = (2048 - period) * 4;
                this.waveDutyIndex = (this.waveDutyIndex + 1) % 8;
            }
            if (period > 2046) {
                return 0;
            }
            int amplitude = DUTY_CYCLES[this.getWaveDuty()][this.waveDutyIndex];
            return amplitude * this.envelopeCurrentVolume;
        }

        @Override
        protected void trigger() {
            super.trigger();
            this.wavePeriodTimer = Math.max(4, (2048 - this.getPeriodFull()) * 4);
            this.envelopePeriodTimer = this.getEnvelopeSweepPace();
            this.envelopeCurrentVolume = this.getInitialVolume();
            this.waveDutyIndex = 0;
            this.envelopeUpdating = true;
        }

        protected void clockEnvelope() {
            if (!this.getEnabled()) {
                return;
            }
            int envelopeSweepPace = this.getEnvelopeSweepPace();
            if (envelopeSweepPace == 0) {
                return;
            }
            if (this.envelopePeriodTimer > 0) {
                this.envelopePeriodTimer--;
            }
            if (this.envelopePeriodTimer == 0) {
                this.envelopePeriodTimer = envelopeSweepPace;
                boolean isUpwards = this.getEnvelopeDirection();
                if ((this.envelopeCurrentVolume < 0xF && isUpwards) || (this.envelopeCurrentVolume > 0x0 && !isUpwards)) {
                    this.envelopeCurrentVolume += isUpwards ? 1 : -1;
                } else {
                    this.envelopeUpdating = false;
                }
            }
        }

    }

    protected class Channel1 extends Channel2 {

        protected int nr10;

        private boolean sweepEnable;
        private int sweepShadow;
        private int sweepTimer;
        private boolean sweepNegateUsedSinceTrigger;

        @Override
        protected void setEnabled(boolean enable) {
            if (enable) {
                nr52 |= 1;
            } else {
                nr52 &= ~1;
            }
        }

        @Override
        protected boolean getEnabled() {
            return (nr52 & 1) != 0;
        }

        @Override
        protected boolean getLeft() {
            return (nr51 & (1 << 4)) != 0;
        }

        @Override
        protected boolean getRight() {
            return (nr51 & 1) != 0;
        }

        private void setNR10(int value) {
            boolean oldNegate = this.getSweepDirection();
            this.nr10 = value & 0xFF;
            if (oldNegate && !this.getSweepDirection() && sweepNegateUsedSinceTrigger) {
                this.setEnabled(false);
            }
        }

        private int getNR10() {
            return this.nr10;
        }

        private int getSweepFrequencyPace() {
            return (this.nr10 >>> 4) & 0b111;
        }

        private boolean getSweepDirection() {
            return (this.nr10 & (1 << 3)) != 0;
        }

        private int getSweepIndividualStep() {
            return this.nr10 & 0b111;
        }

        @Override
        protected void trigger() {
            super.trigger();
            int sweepFrequencyPace = this.getSweepFrequencyPace();
            int sweepIndividualStep = this.getSweepIndividualStep();

            this.sweepShadow = this.getPeriodFull();
            this.sweepTimer = sweepFrequencyPace == 0 ? 8 : sweepFrequencyPace;
            this.sweepEnable = sweepFrequencyPace != 0 || sweepIndividualStep != 0;
            this.sweepNegateUsedSinceTrigger = false;
            if (sweepIndividualStep != 0) {
                int newPeriod = this.calculateSweepNewPeriod();
                if (this.getSweepDirection()) {
                    this.sweepNegateUsedSinceTrigger = true;
                }
                if (newPeriod > 2047) {
                    this.setEnabled(false);
                }
            }
        }

        private void clockSweep() {
            if (!this.getEnabled()) {
                return;
            }
            if (!this.sweepEnable) {
                return;
            }
            if (this.sweepTimer > 0) {
                this.sweepTimer--;
            }
            if (this.sweepTimer == 0) {
                int sweepFrequencyPace = this.getSweepFrequencyPace();
                this.sweepTimer = (sweepFrequencyPace == 0) ? 8 : sweepFrequencyPace;
                if (sweepFrequencyPace == 0) {
                    return;
                }
                int newPeriod = this.calculateSweepNewPeriod();
                if (this.getSweepDirection()) {
                    this.sweepNegateUsedSinceTrigger = true;
                }
                if (newPeriod > 2047) {
                    this.setEnabled(false);
                    return;
                }
                if (this.getSweepIndividualStep() != 0) {
                    this.sweepShadow = newPeriod;
                    this.nrx3 = newPeriod & 0xFF;
                    this.nrx4 = (this.nrx4 & 0b11111000) | ((newPeriod >>> 8) & 0b111);
                    int check = this.calculateSweepNewPeriod();
                    if (check > 2047) {
                        this.setEnabled(false);
                    }
                }
            }
        }

        private int calculateSweepNewPeriod() {
            int delta = this.sweepShadow >>> this.getSweepIndividualStep();
            return this.getSweepDirection() ? this.sweepShadow - delta : this.sweepShadow + delta;
        }
    }

    protected class Channel3 extends AudioChannel {

        protected final byte[] waveRAM = intToByteArray(new int[] {
                0xE2, 0xB7, 0x10, 0x95,
                0xC8, 0x6B, 0x0A, 0xF7,
                0x02, 0xF6, 0x63, 0xCB,
                0x59, 0xE3, 0x90, 0x2F
        });

        private int nr30;

        protected int waveSampleBuffer;
        protected int waveRamIndex;
        protected int wavePeriodTimer;
        protected int currentOutputLevel;

        protected boolean fetchedFirstByte;
        protected boolean firstFetchConsumed;

        @Override
        protected void setEnabled(boolean enable) {
            if (enable) {
                nr52 |= 1 << 2;
            } else {
                nr52 &= ~(1 << 2);
            }
        }

        @Override
        protected boolean getEnabled() {
            return (nr52 & (1 << 2)) != 0;
        }

        @Override
        protected boolean getLeft() {
            return (nr51 & (1 << 6)) != 0;
        }

        @Override
        protected boolean getRight() {
            return (nr51 & (1 << 2)) != 0;
        }

        @Override
        protected void setNRX1(int value) {
            super.setNRX1(value);
            this.lengthTimer = 256 - value;
        }

        public void setNR30(int value) {
            this.nr30 = value & 0xFF;
            if (!this.getDacEnable()) {
                this.setEnabled(false);
            }
        }

        private int getNR30() {
            return this.nr30;
        }

        @Override
        protected boolean getDacEnable() {
            return (this.nr30 & (1 << 7)) != 0;
        }

        protected int getMaxLengthTimer() {
            return 256;
        }

        protected int getOutputLevel() {
            return (this.nrx2 >>> 5) & 0b11;
        }

        protected int getPeriodFull() {
            return ((this.nrx4 & 0b111) << 8) | this.nrx3;
        }

        protected int readWaveRAM(int address) {
            boolean originalFirstFetchConsumed = this.firstFetchConsumed;
            this.firstFetchConsumed = this.fetchedFirstByte;
            if (this.getEnabled()) {
                if (this.wavePeriodTimer <= 2 && originalFirstFetchConsumed) {
                    return (int) this.waveRAM[((this.waveRamIndex - 1) & 31) / 2] & 0xFF;
                } else {
                    return 0xFF;
                }
            } else {
                return (int) this.waveRAM[address - WAVERAM_START] & 0xFF;
            }
        }

        protected void writeWaveRAM(int address, int value) {
            boolean originalFirstFetchConsumed = this.firstFetchConsumed;
            this.firstFetchConsumed = this.fetchedFirstByte;
            if (this.getEnabled()) {
                if (this.wavePeriodTimer <= 2 && originalFirstFetchConsumed) {
                    this.waveRAM[((this.waveRamIndex - 1) & 31) / 2] = (byte) value;
                }
            } else {
                this.waveRAM[address - WAVERAM_START] = (byte) value;
            }
        }

        @Override
        protected int tick() {
            if (!this.getEnabled()) {
                int amplitude;
                if (this.waveRamIndex % 2 == 0) {
                    amplitude = (this.waveSampleBuffer >>> 4) & 0xF;
                } else {
                    amplitude = this.waveSampleBuffer & 0xF;
                }
                return amplitude >>> this.getShiftAmount();
            }

            this.wavePeriodTimer--;
            if (this.wavePeriodTimer <= 0) {
                this.wavePeriodTimer = (2048 - this.getPeriodFull()) * 2;
                this.waveRamIndex = (this.waveRamIndex + 1) % 32;
                this.waveSampleBuffer = (int) this.waveRAM[this.waveRamIndex / 2] & 0xFF;
                this.currentOutputLevel = this.getOutputLevel();
                this.fetchedFirstByte = true;
            }

            int amplitude;
            if (this.waveRamIndex % 2 == 0) {
                amplitude = (this.waveSampleBuffer >>> 4) & 0xF;
            } else {
                amplitude = this.waveSampleBuffer & 0xF;
            }

            return amplitude >>> this.getShiftAmount();
        }

        protected int getShiftAmount() {
            return switch (this.currentOutputLevel) {
                case 0 -> 4;
                case 1 -> 0;
                case 2 -> 1;
                case 3 -> 2;
                default -> throw new EmulatorException("Invalid CH3 output level \"%d\" for the GameBoy APU!".formatted(this.currentOutputLevel));
            };
        }


        @Override
        protected void trigger() {
            this.checkWaveRamCorruption();
            super.trigger();
            this.wavePeriodTimer = (2048 - this.getPeriodFull()) * 2;
            this.currentOutputLevel = this.getOutputLevel();
            this.waveRamIndex = 0;
        }

        protected void checkWaveRamCorruption() {
            if (this.getEnabled() && this.wavePeriodTimer == 4) {
                int coarseReadByteIndex = ((this.waveRamIndex - 1) & 31) / 2;
                if (coarseReadByteIndex <= 3) {
                    this.waveRAM[0] = this.waveRAM[coarseReadByteIndex];
                } else {
                    int beginIndex = coarseReadByteIndex & ~0b11;
                    for (int i = beginIndex, j = 0; i <= beginIndex + 3; i++, j++) {
                        this.waveRAM[j] = this.waveRAM[i];
                    }
                }
            }
        }

    }

    protected class Channel4 extends AudioChannel {

        private int envelopePeriodTimer;
        private int envelopeCurrentVolume;
        private boolean envelopeUpdating;

        private int wavePeriodTimer;
        private int lfsr;

        @Override
        protected void setEnabled(boolean enable) {
            if (enable) {
                nr52 |= 1 << 3;
            } else {
                nr52 &= ~(1 << 3);
            }
        }

        @Override
        protected boolean getEnabled() {
            return (nr52 & (1 << 3)) != 0;
        }

        @Override
        protected boolean getLeft() {
            return (nr51 & (1 << 7)) != 0;
        }

        @Override
        protected boolean getRight() {
            return (nr51 & (1 << 3)) != 0;
        }

        private int getInitialVolume() {
            return (this.nrx2 >>> 4) & 0b1111;
        }

        @Override
        public void setNRX1(int value) {
            super.setNRX1(value);
            this.lengthTimer = 64 - (value & 0x3F);
        }

        @Override
        public void setNRX2(int value) {

            int oldPeriod = this.getEnvelopeSweepPace();
            boolean oldIncrease = this.getEnvelopeDirection();

            super.setNRX2(value);
            if (!this.getDacEnable()) {
                this.setEnabled(false);
            }

            if (this.getEnabled()) {
                if (oldPeriod == 0 && this.envelopeUpdating) {
                    this.envelopeCurrentVolume++;
                } else if (!oldIncrease) {
                    this.envelopeCurrentVolume += 2;
                }
                if (oldIncrease != this.getEnvelopeDirection()) {
                    this.envelopeCurrentVolume = 16 - this.envelopeCurrentVolume;
                }
                this.envelopeCurrentVolume &= 0xF;
            }
        }

        @Override
        protected boolean getDacEnable() {
            return (this.nrx2 & 0xF8) != 0;
        }

        private boolean getEnvelopeDirection() {
            return (this.nrx2 & (1 << 3)) != 0;
        }

        private int getEnvelopeSweepPace() {
            return this.nrx2 & 0b111;
        }

        private int getClockShift() {
            return (this.nrx3 >>> 4) & 0b1111;
        }

        private boolean getLFSRWidth() {
            return (this.nrx3 & (1 << 3)) != 0;
        }

        private int getClockDivider() {
            return this.nrx3 & 0b111;
        }

        @Override
        protected int tick() {
            if (!this.getEnabled()) {
                if (this.getDacEnable()) {
                    return 0xF;
                } else {
                    return 0;
                }
            }

            this.wavePeriodTimer--;
            if (this.wavePeriodTimer <= 0) {
                int clockDivider = this.getClockDivider();
                this.wavePeriodTimer = (clockDivider > 0 ? (clockDivider << 4) : 8) << this.getClockShift();
                int xorResult = (this.lfsr & 0b01) ^ ((this.lfsr & 0b10) >>> 1);
                this.lfsr = ((this.lfsr >>> 1) | (xorResult << 14)) & 0x7FFF;
                if (this.getLFSRWidth()) {
                    this.lfsr = (this.lfsr & (~(1 << 6))) & 0x7FFF;
                    this.lfsr = (this.lfsr | (xorResult << 6)) & 0x7FFF;
                }
            }
            int amplitude = ~this.lfsr & 0x01;
            return amplitude * this.envelopeCurrentVolume;
        }

        @Override
        protected void trigger() {
            super.trigger();
            int clockDivider = this.getClockDivider();
            this.wavePeriodTimer = (clockDivider > 0 ? (clockDivider << 4) : 8) << this.getClockShift();
            this.envelopeCurrentVolume = this.getInitialVolume();
            this.lfsr = 0x7FFF;
            this.envelopeUpdating = true;
        }

        protected void clockEnvelope() {
            if (!this.getEnabled()) {
                return;
            }
            int envelopeSweepPace = this.getEnvelopeSweepPace();
            if (envelopeSweepPace == 0) {
                return;
            }
            if (this.envelopePeriodTimer > 0) {
                this.envelopePeriodTimer--;
            }
            if (this.envelopePeriodTimer == 0) {
                this.envelopePeriodTimer = envelopeSweepPace;
                boolean isUpwards = this.getEnvelopeDirection();
                if ((this.envelopeCurrentVolume < 0xF && isUpwards) || (this.envelopeCurrentVolume > 0x0 && !isUpwards)) {
                    this.envelopeCurrentVolume += isUpwards ? 1 : -1;
                } else {
                    this.envelopeUpdating = false;
                }
            }
        }
    }

}
