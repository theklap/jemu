package io.github.arkosammy12.jemu.core.gameboy.mbcs;

import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.gameboy.GameBoyCartridge;
import io.github.arkosammy12.jemu.core.gameboy.GameBoyEmulator;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import java.util.Optional;


public class MBC0 extends GameBoyCartridge {

    private final byte[] rom = new byte[0x8000];
    private final byte @Nullable [] sRam;
    private final boolean hasBattery;

    public MBC0(GameBoyEmulator emulator, int cartridgeType) {
        super(emulator, cartridgeType);

        if (cartridgeType == 0x08 || cartridgeType == 0x09) {
            this.sRam = switch (this.ramSizeHeader) {
                case 0x00 -> null;
                case 0x01 -> new byte[0x800];
                case 0x02 -> new byte[0x2000];
                default -> throw new EmulatorException("Incompatible RAM size header $%02X for MBC0 GameBoy cartridge type!".formatted(this.ramSizeHeader));
            };
        } else {
            this.sRam = null;
        }

        this.hasBattery = cartridgeType == 0x09;

        try {
            System.arraycopy(this.originalRom, 0, this.rom, 0, this.rom.length);
        } catch (Exception e) {
            throw new EmulatorException("Error initializing GameBoy cartridge ROM!", e);
        }

        if (this.hasBattery) {
            this.readSaveData().ifPresent(saveData -> {
                if (this.sRam == null) {
                    return;
                }
                try {
                    System.arraycopy(saveData, 0, this.sRam, 0, Math.min(saveData.length, this.sRam.length));
                } catch (Exception e) {
                    Logger.error("Error reading save data for GameBoy MBC0 cartridge: {}", e);
                }
            });
        }

    }

    @Override
    public int readByte(int address) {
        if (address >= 0x0000 && address <= 0x7FFF) {
            return (int) this.rom[address] & 0xFF;
        } else if (address >= 0xA000 && address <= 0xBFFF) {
            address -= 0xA000;
            if (this.sRam != null && address < this.sRam.length) {
                return (int) this.sRam[address] & 0xFF;
            } else {
                return 0xFF;
            }
        } else {
            throw new EmulatorException("Invalid GameBoy MBC0 cartridge read address $%04X!".formatted(address));
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= 0xA000 && address <= 0xBFFF) {
            address -= 0xA000;
            if (this.sRam != null && address < this.sRam.length) {
                this.sRam[address] = (byte) value;
            }
        }
    }

    @Override
    protected Optional<byte[]> getSaveData() {
        return Optional.ofNullable(this.hasBattery ? this.sRam : null);
    }

}

