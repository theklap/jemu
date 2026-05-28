package io.github.arkosammy12.jemu.core.nes;

import io.github.arkosammy12.jemu.core.common.Bus;
import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;
import io.github.arkosammy12.jemu.core.nes.mappers.*;

public abstract class NESCartridge<E extends NESEmulator> implements Bus {

    protected final E emulator;
    protected final INESFile iNESFile;
    protected final NametableArrangement iNESFileNametableArrangement;

    private final byte[] vram;

    public NESCartridge(E emulator, INESFile iNESFile) {
        // TODO: SRAM saving support for cartridges that have it
        this.emulator = emulator;
        this.iNESFile = iNESFile;
        this.iNESFileNametableArrangement = this.iNESFile.getNametableArrangement() ? NESCartridge.NametableArrangement.HORIZONTAL : NESCartridge.NametableArrangement.VERTICAL;
        this.vram = new byte[switch (this.getVRAMSize()) {
            case KB_2 -> 0x800;
            case KB_4 -> 0x1000;
        }];
    }

    public static <E extends NESEmulator> NESCartridge<E> getCartridge(E emulator, INESFile iNESFile) {
        int mapperNumber = iNESFile.getMapperNumber();
        int subMapperNumber = iNESFile.getSubmapperNumber();
        return switch (mapperNumber) {
            case 0 -> new NROMCartridge<>(emulator, iNESFile);
            case 1 -> new MMC1Cartridge<>(emulator, iNESFile);
            case 2 -> new UxROMCartridge<>(emulator, iNESFile);
            case 3 -> new CNROMCartridge<>(emulator, iNESFile);
            case 4 -> {
                if (iNESFile.getSubmapperNumber() == 1) {
                    yield new MMC6Cartridge<>(emulator, iNESFile);
                } else {
                    yield new MMC3Cartridge<>(emulator, iNESFile);
                }
            }
            case 7 -> new AxROMCartridge<>(emulator, iNESFile);
            case 9 -> new MMC2Cartridge<>(emulator, iNESFile);
            case 10 -> new MMC4Cartridge<>(emulator, iNESFile);
            // TODO: For VRC2 and VRC4 iNES compatibility, place registers in two places to satisfy both submapper possibilities for a single iNES mapper
            case 21 -> switch (subMapperNumber) {
                case 1, 2 -> new VRC4Cartridge<>(emulator, iNESFile);
                default -> throw new EmulatorException("Invalid iNES mapper %d and submapper number %d combination!".formatted(mapperNumber, subMapperNumber));
            };
            case 22 -> {
                if (subMapperNumber == 0) {
                    yield new VRC2Cartridge<>(emulator, iNESFile);
                } else {
                    throw new EmulatorException("Invalid iNES mapper %d and submapper number %d combination!".formatted(mapperNumber, subMapperNumber));
                }
            }
            case 23, 25 -> switch (subMapperNumber) {
                case 1, 2 -> new VRC4Cartridge<>(emulator, iNESFile);
                case 3 -> new VRC2Cartridge<>(emulator, iNESFile);
                default -> throw new EmulatorException("Invalid iNES mapper %d and submapper number %d combination!".formatted(mapperNumber, subMapperNumber));
            };
            case 24, 26 -> new VRC6Cartridge<>(emulator, iNESFile);
            case 71 -> new INESMapper71Cartridge<>(emulator, iNESFile);
            case 218 -> new INESMapper218Cartridge<>(emulator, iNESFile);
            default -> throw new EmulatorException("Unimplemented iNES mapper number %d!".formatted(mapperNumber));
        };
    }

    public INESFile getINESFile() {
        return this.iNESFile;
    }

    abstract public int readBytePPU(int address);

    abstract public void writeBytePPU(int address, int value);

    protected VRAMSize getVRAMSize() {
        return VRAMSize.KB_2;
    }

    public void onPPUHalfDot() {

    }

    public void observePPUAddress(int address) {

    }

    protected int readByteVRAM(int address) {
        return (int) this.vram[address] & 0xFF;
    }

    protected void writeByteVRAM(int address, int value) {
        this.vram[address] = (byte) value;
    }

    protected int mapNametableAddress(int address) {
        return this.mapNametableAddress(address, this.iNESFileNametableArrangement);
    }

    protected int mapNametableAddress(int address, NametableArrangement nametableArrangement) {
        return switch (nametableArrangement) {
            case HORIZONTAL -> (address & (1 << 10)) | (address & 0x3FF);
            case VERTICAL -> ((address & (1 << 11)) >>> 1) | (address & 0x3FF);
            case SINGLE_SCREEN_LOWER_BANK -> address & 0x3FF;
            case SINGLE_SCREEN_UPPER_BANK -> 0x400 | (address & 0x3FF);
            case FOUR_SCREEN -> address & 0xFFF;
        };
    }

    public void cycle() {

    }

    public boolean getIRQSignal() {
        return false;
    }

    public enum NametableArrangement {
        HORIZONTAL,
        VERTICAL,
        SINGLE_SCREEN_LOWER_BANK,
        SINGLE_SCREEN_UPPER_BANK,
        FOUR_SCREEN
    }

    protected enum VRAMSize {
        KB_2,
        KB_4
    }

}
