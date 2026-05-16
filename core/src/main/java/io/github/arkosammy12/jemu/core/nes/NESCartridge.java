package io.github.arkosammy12.jemu.core.nes;

import io.github.arkosammy12.jemu.core.common.Bus;
import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;
import io.github.arkosammy12.jemu.core.nes.mappers.*;

public abstract class NESCartridge<E extends NESEmulator> implements Bus {

    protected final E emulator;
    protected final INESFile iNESFile;
    private final NametableArrangement iNESFileNametableArrangement;

    private final byte[] vRam = new byte[0x800];

    public NESCartridge(E emulator, INESFile iNESFile) {
        this.emulator = emulator;
        this.iNESFile = iNESFile;
        this.iNESFileNametableArrangement = this.iNESFile.getNametableArrangement();
    }

    public static <E extends NESEmulator> NESCartridge<E> getCartridge(E emulator, INESFile iNESFile) {
        int mapperNumber = iNESFile.getMapperNumber();
        return switch (mapperNumber) {
            case 0 -> new NROMCartridge<>(emulator, iNESFile);
            case 1 -> new MMC1Cartridge<>(emulator, iNESFile);
            case 2 -> new UXROMCartridge<>(emulator, iNESFile);
            case 3 -> new CNROMCartridge<>(emulator, iNESFile);
            case 7 -> new AXROMCartridge<>(emulator, iNESFile);
            default -> throw new EmulatorException("Unimplemented iNES mapper number %d!".formatted(mapperNumber));
        };
    }

    public INESFile getINESFile() {
        return this.iNESFile;
    }

    abstract public int readBytePPU(int address);

    abstract public void writeBytePPU(int address, int value);

    protected int readByteVRAM(int address) {
        return (int) this.vRam[address] & 0xFF;
    }

    protected void writeByteVRAM(int address, int value) {
        this.vRam[address] = (byte) value;
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

}
