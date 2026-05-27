package io.github.arkosammy12.jemu.core.gameboycolor;

import io.github.arkosammy12.jemu.core.cpu.CGBSM83;
import io.github.arkosammy12.jemu.core.cpu.SM83;
import io.github.arkosammy12.jemu.core.gameboy.*;

public class GameBoyColorEmulator extends GameBoyEmulator implements CGBSM83.SystemBus {

    private CGBSM83<?> cpu;
    private CGBBus<?> bus;
    private CGBPPU<?> ppu;
    private CGBAPU<?> apu;

    private CGBTimerController<?> timerController;

    private boolean dmgCompatibilityMode;
    private int key1;

    public GameBoyColorEmulator(GameBoyHost host) {
        super(host);
    }

    @Override
    protected CGBSM83<?> createCpu() {
        this.cpu = new CGBSM83<>(this);
        return this.cpu;
    }

    @Override
    public CGBSM83<?> getCpu() {
        return this.cpu;
    }

    @Override
    protected DMGBus<?> createBus() {
        this.bus = new CGBBus<>(this);
        return this.bus;
    }

    @Override
    public CGBBus<?> getBus() {
        return this.bus;
    }

    @Override
    protected CGBPPU<?> createPpu() {
        this.ppu = new CGBPPU<>(this);
        return this.ppu;
    }

    @Override
    public CGBPPU<?> getVideoGenerator() {
        return this.ppu;
    }

    @Override
    protected CGBAPU<?> createApu() {
        this.apu = new CGBAPU<>(this);
        return this.apu;
    }

    @Override
    public CGBAPU<?> getAudioGenerator() {
        return this.apu;
    }

    @Override
    protected CGBTimerController<?> createTimerController() {
        this.timerController = new CGBTimerController<>(this);
        return this.timerController;
    }

    @Override
    public CGBTimerController<?> getTimerController() {
        return this.timerController;
    }

    @Override
    protected CGBSerialController<?> createSerialController() {
        return new CGBSerialController<>(this);
    }

    public CPUSpeed getCPUSpeed() {
        return (this.key1 & 0x80) != 0 ? CPUSpeed.DOUBLE_SPEED : CPUSpeed.SINGLE_SPEED;
    }

    public boolean isDMGCompatibilityMode() {
        return this.dmgCompatibilityMode;
    }

    @Override
    protected void runCycle() {
        CGBSM83<?> cpu = this.getCpu();
        CGBPPU<?> ppu = this.getVideoGenerator();
        CGBAPU<?> apu = this.getAudioGenerator();
        GameBoyCartridge cartridge = this.getCartridge();
        CGBBus<?> bus = this.getBus();
        CGBTimerController<?> timerController = this.getTimerController();
        DMGSerialController<?> serialController = this.getSerialController();

        if ((this.key1 & 0x80) == 0) {
            boolean apuFrameSequencerTick = false;
            if (bus.haltCPU()) {
                if (cpu.getMode() != SM83.Mode.STOPPED) {
                    apuFrameSequencerTick = timerController.cycle();
                }
            } else {
                cpu.cycle();
                if (cpu.getMode() != SM83.Mode.STOPPED) {
                    apuFrameSequencerTick = timerController.cycle();
                }
                cpu.nextState();
            }

            ppu.cycle();
            apu.cycle(apuFrameSequencerTick);
            serialController.cycle();
            cartridge.cycle();
            bus.cycleOAMDMA();
            bus.cycleVDMA();
        } else {
            boolean apuFrameSequencerTick = false;
            if (bus.haltCPU()) {
                if (cpu.getMode() != SM83.Mode.STOPPED) {
                    apuFrameSequencerTick |= timerController.cycle();
                    apuFrameSequencerTick |= timerController.cycle();
                }
            } else {
                cpu.cycle();
                if (cpu.getMode() != SM83.Mode.STOPPED) {
                    apuFrameSequencerTick |= timerController.cycle();
                }
                cpu.nextState();

                cpu.cycle();
                if (cpu.getMode() != SM83.Mode.STOPPED) {
                    apuFrameSequencerTick |= timerController.cycle();
                }
                cpu.nextState();
            }

            ppu.cycle();
            apu.cycle(apuFrameSequencerTick);

            serialController.cycle();
            serialController.cycle();

            cartridge.cycle();

            bus.cycleOAMDMA();
            bus.cycleOAMDMA();

            bus.cycleVDMA();
        }
    }

    @Override
    public boolean isSpeedSwitchRequested() {
        return this.isSwitchSpeedArmed();
    }

    @Override
    public void onStopInstructionWithSpeedSwitch(boolean resetDiv) {
        this.onStopInstruction(resetDiv);
        if (this.isSwitchSpeedArmed()) {
            this.key1 = (this.key1 ^ 0x80) & 0xFE;
        }
    }

    public void writeKey0(int value) {
        if (this.getBus().isBootRomEnabled()) {
            this.dmgCompatibilityMode = (value & 0b100) != 0;
        }
    }

    public void writeKEY1(int value) {
        this.key1 = (this.key1 & 0x80) | (value & 1);
    }

    public int readKEY0() {
        return this.dmgCompatibilityMode ? 0xFF : 0xFB;
    }

    public int readKEY1() {
        return this.key1 | 0b01111110;
    }

    public boolean isSwitchSpeedArmed() {
        return (this.key1 & 1) != 0;
    }

    public enum CPUSpeed {
        SINGLE_SPEED,
        DOUBLE_SPEED
    }

}
