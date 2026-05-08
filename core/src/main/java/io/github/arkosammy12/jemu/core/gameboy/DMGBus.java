package io.github.arkosammy12.jemu.core.gameboy;

import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.common.Bus;

import static io.github.arkosammy12.jemu.core.gameboy.DMGAPU.*;
import static io.github.arkosammy12.jemu.core.gameboy.DMGPPU.*;
import static io.github.arkosammy12.jemu.core.gameboy.DMGSerialController.SB_ADDR;
import static io.github.arkosammy12.jemu.core.gameboy.DMGSerialController.SC_ADDR;
import static io.github.arkosammy12.jemu.core.gameboy.DMGTimerController.*;
import static io.github.arkosammy12.jemu.core.gameboy.GameBoyJoypad.JOYP_ADDR;

public class DMGBus<E extends GameBoyEmulator> implements Bus {

    // Bootix boot-rom for the DMG. Courtesy of Ashiepaws https://github.com/Ashiepaws/Bootix
    protected static final int[] BOOTIX = {
            0x31, 0xfe, 0xff, 0x21, 0xff, 0x9f, 0xaf, 0x32, 0xcb, 0x7c, 0x20, 0xfa,
            0x0e, 0x11, 0x21, 0x26, 0xff, 0x3e, 0x80, 0x32, 0xe2, 0x0c, 0x3e, 0xf3,
            0x32, 0xe2, 0x0c, 0x3e, 0x77, 0x32, 0xe2, 0x11, 0x04, 0x01, 0x21, 0x10,
            0x80, 0x1a, 0xcd, 0xb8, 0x00, 0x1a, 0xcb, 0x37, 0xcd, 0xb8, 0x00, 0x13,
            0x7b, 0xfe, 0x34, 0x20, 0xf0, 0x11, 0xcc, 0x00, 0x06, 0x08, 0x1a, 0x13,
            0x22, 0x23, 0x05, 0x20, 0xf9, 0x21, 0x04, 0x99, 0x01, 0x0c, 0x01, 0xcd,
            0xb1, 0x00, 0x3e, 0x19, 0x77, 0x21, 0x24, 0x99, 0x0e, 0x0c, 0xcd, 0xb1,
            0x00, 0x3e, 0x91, 0xe0, 0x40, 0x06, 0x10, 0x11, 0xd4, 0x00, 0x78, 0xe0,
            0x43, 0x05, 0x7b, 0xfe, 0xd8, 0x28, 0x04, 0x1a, 0xe0, 0x47, 0x13, 0x0e,
            0x1c, 0xcd, 0xa7, 0x00, 0xaf, 0x90, 0xe0, 0x43, 0x05, 0x0e, 0x1c, 0xcd,
            0xa7, 0x00, 0xaf, 0xb0, 0x20, 0xe0, 0xe0, 0x43, 0x3e, 0x83, 0xcd, 0x9f,
            0x00, 0x0e, 0x27, 0xcd, 0xa7, 0x00, 0x3e, 0xc1, 0xcd, 0x9f, 0x00, 0x11,
            0x8a, 0x01, 0xf0, 0x44, 0xfe, 0x90, 0x20, 0xfa, 0x1b, 0x7a, 0xb3, 0x20,
            0xf5, 0x18, 0x49, 0x0e, 0x13, 0xe2, 0x0c, 0x3e, 0x87, 0xe2, 0xc9, 0xf0,
            0x44, 0xfe, 0x90, 0x20, 0xfa, 0x0d, 0x20, 0xf7, 0xc9, 0x78, 0x22, 0x04,
            0x0d, 0x20, 0xfa, 0xc9, 0x47, 0x0e, 0x04, 0xaf, 0xc5, 0xcb, 0x10, 0x17,
            0xc1, 0xcb, 0x10, 0x17, 0x0d, 0x20, 0xf5, 0x22, 0x23, 0x22, 0x23, 0xc9,
            0x3c, 0x42, 0xb9, 0xa5, 0xb9, 0xa5, 0x42, 0x3c, 0x00, 0x54, 0xa8, 0xfc,
            0x42, 0x4f, 0x4f, 0x54, 0x49, 0x58, 0x2e, 0x44, 0x4d, 0x47, 0x20, 0x76,
            0x31, 0x2e, 0x32, 0x00, 0x3e, 0xff, 0xc6, 0x01, 0x0b, 0x1e, 0xd8, 0x21,
            0x4d, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x3e, 0x01, 0xe0, 0x50
    };

    public static final int ROM0_START = 0x0000;
    public static final int ROM0_END = 0x3FFF;

    public static final int ROMX_START = 0x4000;
    public static final int ROMX_END = 0x7FFF;

    public static final int VRAM_START = 0x8000;
    public static final int VRAM_END = 0x9FFF;

    public static final int SRAM_START = 0xA000;
    public static final int SRAM_END = 0xBFFF;

    public static final int WRAM0_START = 0xC000;
    public static final int WRAM0_END = 0xCFFF;

    public static final int WRAMX_START = 0xD000;
    public static final int WRAMX_END = 0xDFFF;

    public static final int ECHO_START = 0xE000;
    public static final int ECHO_END = 0xFDFF;

    public static final int OAM_START = 0xFE00;
    public static final int OAM_END = 0xFE9F;

    public static final int UNUSED_START = 0xFEA0;
    public static final int UNUSED_END = 0xFEFF;

    public static final int IO_START = 0xFF00;
    public static final int IO_END = 0xFF7F;

    public static final int HRAM_START = 0xFF80;
    public static final int HRAM_END = 0xFFFE;

    public static final int IF_ADDR = 0xFF0F;
    public static final int OAM_DMA_ADDR = 0xFF46;
    public static final int BANK_ADDR = 0xFF50;
    public static final int IE_ADDR = 0xFFFF;

    protected final E emulator;

    protected final byte[][] workRam;

    private int interruptFlag;
    private int interruptEnable;

    private int oamDmaControl;
    private int oamTransferDelay;
    protected boolean oamTransferInProgress;
    private int oamTransferredBytes;

    protected boolean enableBootRom = true;

    public DMGBus(E emulator) {
        this.emulator = emulator;
        this.workRam = this.createWorkRam();
    }

    protected byte[][] createWorkRam() {
        return new byte[1][0x2000];
    }

    public boolean isBootRomEnabled() {
        return this.enableBootRom;
    }

    public int getIE() {
        return this.interruptEnable;
    }

    public void setIF(int value) {
        this.interruptFlag = value & 0xFF;
    }

    public int getIF() {
        return interruptFlag;
    }

    @Override
    public int readByte(int address) {
        if (this.isOAMDMABusConflict(address)) {
            // TODO: Perhaps this value is only returned when reading from OAM. Otherwise return the current value being read by OAM. Check numism test ROM for info.
            return 0xFF;
        } if (this.enableBootRom && address >= 0x0000 && address <= 0x00FF) {
            return BOOTIX[address];
        } else if ((address >= ROM0_START && address <= ROMX_END) || (address >= SRAM_START && address <= SRAM_END)) {
            return this.emulator.getCartridge().readByte(address);
        } else if ((address >= VRAM_START && address <= VRAM_END) || (address >= OAM_START && address <= OAM_END)) {
            return this.emulator.getVideoGenerator().readByte(address);
        } else if (address >= WRAM0_START && address <= WRAMX_END) {
            return (int) this.workRam[0][address - WRAM0_START] & 0xFF;
        } else if (address >= ECHO_START && address <= ECHO_END) {
            return (int) this.workRam[0][address & 0x1FFF] & 0xFF;
        } else if (address >= UNUSED_START && address <= UNUSED_END) {
            return 0x00;
        } else if ((address >= IO_START && address <= IO_END) || address == IE_ADDR) {
            return switch (address) {
                case OAM_DMA_ADDR -> this.oamDmaControl;
                case BANK_ADDR -> (this.enableBootRom ? 0 : 1) | 0b11111110;
                case JOYP_ADDR -> this.emulator.getSystemController().readJoypad();
                case SB_ADDR, SC_ADDR -> this.emulator.getSerialController().readByte(address);
                case DIV_ADDR, TIMA_ADDR, TMA_ADDR, TAC_ADDR -> this.emulator.getTimerController().readByte(address);
                case IF_ADDR -> this.interruptFlag | 0b11100000;
                case IE_ADDR -> this.interruptEnable;
                default -> {
                    if ((address >= NR10_ADDR && address <= NR14_ADDR) || (address >= NR21_ADDR && address <= NR34_ADDR) || (address >= NR41_ADDR && address <= NR52_ADDR) || (address >= WAVERAM_START && address <= WAVERAM_END)) {
                        yield this.emulator.getAudioGenerator().readByte(address);
                    } else if ((address >= LCDC_ADDR && address <= LYC_ADDR) || (address >= BGP_ADDR && address <= WX_ADDR)) {
                        yield this.emulator.getVideoGenerator().readByte(address);
                    } else {
                        yield 0xFF;
                    }
                }
            };
        } else if (address >= HRAM_START && address <= HRAM_END) {
            return this.emulator.getCpu().readHRam(address - HRAM_START);
        } else {
            throw new EmulatorException("Invalid GameBoy memory address $%04X!".formatted(address));
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (this.oamTransferInProgress && address < 0xFF00) {
            return;
        }
        if ((address >= ROM0_START && address <= ROMX_END) || (address >= SRAM_START && address <= SRAM_END)) {
            this.emulator.getCartridge().writeByte(address, value);
        } else if ((address >= VRAM_START && address <= VRAM_END) || (address >= OAM_START && address <= OAM_END)) {
            this.emulator.getVideoGenerator().writeByte(address, value);
        } else if (address >= WRAM0_START && address <= WRAMX_END) {
            this.workRam[0][address - WRAM0_START] = (byte) value;
        } else if (address >= ECHO_START && address <= ECHO_END) {
            this.workRam[0][address & 0x1FFF] = (byte) value;
        } else if (address >= UNUSED_START && address <= UNUSED_END) {
            // TODO: TRIGGER OAM BUG
        } else if ((address >= IO_START && address <= IO_END) || address == IE_ADDR) {
            switch (address) {
                case OAM_DMA_ADDR -> {
                    this.oamDmaControl = value & 0xFF;
                    this.oamTransferDelay = 2;
                }
                case BANK_ADDR -> this.enableBootRom = false;
                case JOYP_ADDR -> this.emulator.getSystemController().writeJoyP(value);
                case SB_ADDR, SC_ADDR -> this.emulator.getSerialController().writeByte(address, value);
                case DIV_ADDR, TIMA_ADDR, TMA_ADDR, TAC_ADDR -> this.emulator.getTimerController().writeByte(address, value);
                case IF_ADDR -> this.interruptFlag = value & 0xFF;
                case IE_ADDR -> this.interruptEnable = value & 0xFF;
                default -> {
                    if ((address >= NR10_ADDR && address <= NR14_ADDR) || (address >= NR21_ADDR && address <= NR34_ADDR) || (address >= NR41_ADDR && address <= NR52_ADDR) || (address >= WAVERAM_START && address <= WAVERAM_END)) {
                        this.emulator.getAudioGenerator().writeByte(address, value);
                    } else if ((address >= LCDC_ADDR && address <= LYC_ADDR) || (address >= BGP_ADDR && address <= WX_ADDR)) {
                        this.emulator.getVideoGenerator().writeByte(address, value);
                    }
                }
            }
        } else if (address >= HRAM_START && address <= HRAM_END) {
            this.emulator.getCpu().writeHRam(address - HRAM_START, value);
        } else {
            throw new EmulatorException("Invalid GameBoy memory address $%04X!".formatted(address));
        }
    }

    public void cycleOAMDMA() {
        if (this.oamTransferInProgress) {
            int sourceAddress = (this.oamDmaControl << 8) | (this.oamTransferredBytes);
            int oamByte = this.readByteOAMDMA(sourceAddress);
            this.emulator.getVideoGenerator().writeOamDma(0xFE00 | this.oamTransferredBytes, oamByte);
            this.oamTransferredBytes++;
            if (this.oamTransferredBytes > 0x9F) {
                this.oamTransferInProgress = false;
            }
        }
        if (this.oamTransferDelay > 0) {
            this.oamTransferDelay--;
            if (this.oamTransferDelay <= 0) {
                this.oamTransferInProgress = true;
                this.oamTransferredBytes = 0;
            }
        }

    }

    protected int readByteOAMDMA(int address) {
        if (this.enableBootRom && address >= 0x0000 && address <= 0x00FF) {
            return BOOTIX[address];
        } else if ((address >= ROM0_START && address <= ROMX_END) || (address >= SRAM_START && address <= SRAM_END)) {
            return this.emulator.getCartridge().readByte(address);
        } else if (address >= VRAM_START && address <= VRAM_END) {
            return this.emulator.getVideoGenerator().readByte(address);
        } else if (address >= WRAM0_START && address <= WRAMX_END) {
            return (int) this.workRam[0][address - WRAM0_START] & 0xFF;
        } else if (address >= ECHO_START && address <= ECHO_END) {
            return (int) this.workRam[0][address & 0x1FFF] & 0xFF;
        } else if (address >= 0xFE00 && address <= 0xFFFF) {
            return (int) this.workRam[0][address & 0x1FFF] & 0xFF;
        } else {
            throw new EmulatorException("Invalid GameBoy memory address %04X!".formatted(address));
        }
    }

    private boolean isOAMDMABusConflict(int address) {
        if (address >= HRAM_START && address <= HRAM_END) {
            return false;
        }
        boolean oamBusConflict = false;
        if (this.oamTransferInProgress) {
            if (address >= OAM_START && address <= OAM_END) {
                return true;
            }
            int sourceAddress = (this.oamDmaControl << 8) | (this.oamTransferredBytes);
            if (address >= 0x0000 && address <= 0x7FFF && sourceAddress >= 0x0000 && sourceAddress <= 0x7FFF) { // External bus
                oamBusConflict = true;
            } else if (address >= 0xA000 && address <= 0xFDFF && sourceAddress >= 0xA000 && sourceAddress <= 0xFDFF) { // External bus
                oamBusConflict = true;
            } else if (address >= 0x8000 && address <= 0x9FFF  && sourceAddress >= 0x8000 && sourceAddress <= 0x9FFF) { // VRAM bus
                oamBusConflict = true;
            }
        }
        return oamBusConflict;
    }

}
