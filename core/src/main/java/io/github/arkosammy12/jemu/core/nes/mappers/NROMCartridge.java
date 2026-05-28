package io.github.arkosammy12.jemu.core.nes.mappers;

import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.nes.NESCartridge;
import io.github.arkosammy12.jemu.core.nes.NESEmulator;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;

import java.util.Arrays;
import java.util.Optional;

import static io.github.arkosammy12.jemu.core.nes.RP2C02.*;
import static io.github.arkosammy12.jemu.core.nes.ines.INESFile.KB_8;

public class NROMCartridge<E extends NESEmulator> extends NESCartridge<E> {

    private final byte[] programROM;
    private final byte[] programRAM;
    private final byte[] characterROM;
    private final byte[] characterRAM;

    public NROMCartridge(E emulator, INESFile iNESFile) {
        super(emulator, iNESFile);

        int programRamSize = Math.clamp((long) iNESFile.getProgramRamSize(), 0, KB_8);
        this.programRAM = programRamSize > 0 ? new byte[programRamSize] : null;

        byte[] programRomData = iNESFile.getProgramRom();
        this.programROM = Arrays.copyOf(programRomData, programRomData.length);

        Optional<byte[]> characterRomOptional = iNESFile.getCharacterRom();
        if (characterRomOptional.isEmpty()) {
            this.characterROM = null;
            this.characterRAM = new byte[iNESFile.getCharacterRamSize()];
        } else {
            byte[] characterRomData = characterRomOptional.get();
            this.characterROM = Arrays.copyOf(characterRomData, characterRomData.length);
            this.characterRAM = null;
        }

    }

    @Override
    public int readBytePPU(int address) {
        if (address >= CHR_START && address <= CHR_END) {
            if (this.characterROM == null) {
                return (int) this.characterRAM[(address & 0x1FFF) % this.characterRAM.length] & 0xFF;
            } else {
                return (int) this.characterROM[(address & 0x1FFF) % this.characterROM.length] & 0xFF;
            }
        } else if (address >= CIRAM_START && address <= CIRAM_MIRROR_END) {
            return this.readByteVRAM(this.mapNametableAddress(address));
        } else if (address >= PALETTE_RAM_START && address <= PALETTE_RAM_MIRROR_END) {
            return address & 0xFF;
        } else {
            throw new EmulatorException("Invalid NES NROM cartridge PPU read address $%04X!".formatted(address));
        }
    }

    @Override
    public void writeBytePPU(int address, int value) {
        if (address >= CHR_START && address <= CHR_END) {
            if (this.characterRAM != null) {
                this.characterRAM[(address & 0x1FFF) % this.characterRAM.length] = (byte) value;
            }
        } else if (address >= CIRAM_START && address <= CIRAM_MIRROR_END) {
            this.writeByteVRAM(this.mapNametableAddress(address), value);
        } else if (address >= PALETTE_RAM_START && address <= PALETTE_RAM_MIRROR_END) {

        } else {
            throw new EmulatorException("Invalid NES NROM cartridge PPU write address $%04X!".formatted(address));
        }
    }

    @Override
    public int readByte(int address) {
        if (address >= 0x6000 && address <= 0x7FFF) {
            if (this.programRAM != null) {
                return (int) this.programRAM[(address & 0x1FFF) % this.programRAM.length] & 0xFF;
            } else {
                return -1;
            }
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            return (int) this.programROM[(address & 0x7FFF) % this.programROM.length] & 0xFF;
        } else {
            return -1;
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= 0x6000 && address <= 0x7FFF) {
            if (this.programRAM != null) {
                this.programRAM[(address & 0x1FFF) % this.programRAM.length] = (byte) value;
            }
        }
    }

}
