package io.github.arkosammy12.jemu.core.nes.mappers;

import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.nes.NESCartridge;
import io.github.arkosammy12.jemu.core.nes.NESEmulator;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;
import io.github.arkosammy12.jemu.core.nes.ines.NES20File;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import static io.github.arkosammy12.jemu.core.nes.RP2C02.CIRAM_MIRROR_END;
import static io.github.arkosammy12.jemu.core.nes.RP2C02.CIRAM_START;
import static io.github.arkosammy12.jemu.core.nes.RP2C02.PALETTE_RAM_MIRROR_END;
import static io.github.arkosammy12.jemu.core.nes.RP2C02.PALETTE_RAM_START;

public class MMC3Cartridge<E extends NESEmulator> extends NESCartridge<E> {

    private static final int A12 = 1 << 12;

    private final byte[] programRom;
    private final byte[] programRam;
    private final byte[] characterRom;
    private final byte[] characterRam;

    private final Supplier<NametableArrangement> nametableArrangementSupplier;

    private int bankSelect;
    private NametableArrangement nametableArrangement = NametableArrangement.HORIZONTAL;
    private int prgRamProtect;
    private int irqCounterReload;
    private boolean irqEnabled;

    private int R0;
    private int R1;
    private int R2;
    private int R3;
    private int R4;
    private int R5;
    private int R6;
    private int R7;

    private int irqCounter;
    private boolean irqReload;
    private boolean irqSignal;
    private int previousPPUAddress;
    private int cyclesDown = 3;

    public MMC3Cartridge(E emulator, INESFile iNESFile) {
        super(emulator, iNESFile);

        byte[] programRomData = iNESFile.getProgramRom();
        this.programRom = Arrays.copyOf(programRomData, programRomData.length);

        int programRamSize = iNESFile.getProgramRamSize();
        if (iNESFile instanceof NES20File nes20File) {
            programRamSize += nes20File.getNonVolatileProgramRamSizeBytes();
        }

        this.programRam = programRamSize > 0 ? new byte[programRamSize] : null;

        Optional<byte[]> characterRomOptional = iNESFile.getCharacterRom();
        if (characterRomOptional.isEmpty()) {
            this.characterRom = null;
            int characterRamSize = iNESFile.getCharacterRamSize();
            if (iNESFile instanceof NES20File nes20File) {
                characterRamSize += nes20File.getNonVolatileCharacterRamSizeBytes();
            }
            this.characterRam = new byte[characterRamSize];
        } else {
            byte[] characterRomData = characterRomOptional.get();
            this.characterRom = Arrays.copyOf(characterRomData, characterRomData.length);
            this.characterRam = null;
        }

        if (iNESFile.hasAlternativeNametableLayout()) {
            this.nametableArrangementSupplier = () -> NametableArrangement.FOUR_SCREEN;
        } else {
            this.nametableArrangementSupplier = () -> this.nametableArrangement;
        }
    }

    @Override
    protected VRAMSize getVRAMSize() {
        return this.iNESFile.hasAlternativeNametableLayout() ? VRAMSize.KB_4 : VRAMSize.KB_2;
    }

    @Override
    public int readBytePPU(int address) {
        this.handlePPUAddressUpdate(address);
        if (address >= 0x0000 && address <= 0x1FFF) {
            if (this.characterRom == null) {
                return (int) this.characterRam[this.mapChrAddress(address) % this.characterRam.length] & 0xFF;
            } else {
                return (int) this.characterRom[this.mapChrAddress(address) % this.characterRom.length] & 0xFF;
            }
        } else if (address >= CIRAM_START && address <= CIRAM_MIRROR_END) {
            return this.readByteVRAM(this.mapNametableAddress(address));
        } else if (address >= PALETTE_RAM_START && address <= PALETTE_RAM_MIRROR_END) {
            return address & 0xFF;
        } else {
            throw new EmulatorException("Invalid NES MMC1 cartridge PPU read address $%04X!".formatted(address));
        }
    }

    @Override
    public void writeBytePPU(int address, int value) {
        this.handlePPUAddressUpdate(address);
        if (address >= 0x0000 && address <= 0x1FFF) {
            if (this.characterRam != null) {
                this.characterRam[this.mapChrAddress(address) % this.characterRam.length] = (byte) value;
            }
        } else if (address >= CIRAM_START && address <= CIRAM_MIRROR_END) {
            this.writeByteVRAM(this.mapNametableAddress(address), value);
        } else if (address >= PALETTE_RAM_START && address <= PALETTE_RAM_MIRROR_END) {

        } else {
            throw new EmulatorException("Invalid NES MMC1 cartridge PPU write address $%04X!".formatted(address));
        }
    }

    @Override
    protected int mapNametableAddress(int address) {
        return this.mapNametableAddress(address, this.nametableArrangementSupplier.get());
    }

    @Override
    public int readByte(int address) {
        if (address >= 0x6000 && address <= 0x7FFF) {
            if (this.programRam != null && this.isPrgRamEnabled()) {
                return (int) this.programRam[(address - 0x6000) % this.programRam.length] & 0xFF;
            } else {
                return -1;
            }
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            return (int) this.programRom[this.mapPrgRomAddress(address) % this.programRom.length] & 0xFF;
        } else {
            return -1;
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= 0x6000 && address <= 0x7FFF) {
            if (this.programRam != null && this.allowPrgRamWrites() && this.isPrgRamEnabled()) {
                this.programRam[(address - 0x6000) % this.programRam.length] = (byte) value;
            }
        } else if (address >= 0x8000 && address <= 0x9FFF) {
            if ((address & 1) == 0) {
                this.bankSelect = value & 0xFF;
            } else {
                switch (this.bankSelect & 0b111) {
                    case 0 -> this.R0 = value & 0xFE;
                    case 1 -> this.R1 = value & 0xFE;
                    case 2 -> this.R2 = value & 0xFF;
                    case 3 -> this.R3 = value & 0xFF;
                    case 4 -> this.R4 = value & 0xFF;
                    case 5 -> this.R5 = value & 0xFF;
                    case 6 -> this.R6 = value & 0xFF; // Support oversized MMC3
                    case 7 -> this.R7 = value & 0xFF; // Support oversized MMC3
                }
            }
        } else if (address >= 0xA000 && address <= 0xBFFF) {
            if ((address & 1) == 0) {
                this.nametableArrangement = (value & 1) != 0 ? NametableArrangement.VERTICAL : NametableArrangement.HORIZONTAL;
            } else {
                this.prgRamProtect = value & 0xFF;
            }
        } else if (address >= 0xC000 && address <= 0xDFFF) {
            if ((address & 1) == 0) {
                this.irqCounterReload = value & 0xFF;
            } else {
                this.irqCounter = 0;
                this.irqReload = true;
            }
        } else if (address >= 0xE000 && address <= 0xFFFF) {
            if ((address & 1) == 0) {
                this.irqEnabled = false;
                this.irqSignal = false;
            } else {
                this.irqEnabled = true;
            }
        }
    }

    @Override
    public void cycle() {
        if ((this.previousPPUAddress & A12) == 0) {
            this.cyclesDown++;
        } else {
            this.cyclesDown = 0;
        }
    }

    private boolean isPrgRamEnabled() {
        return (this.prgRamProtect & (1 << 7)) != 0;
    }

    private boolean allowPrgRamWrites() {
        return (this.prgRamProtect & (1 << 6)) == 0;
    }

    private int mapChrAddress(int address) {
        address &= 0x1FFF;
        if ((this.bankSelect & (1 << 7)) == 0) {
            if (address <= 0x07FF) {
                return (address & 0x7FF) | (this.R0 << 10);
            } else if (address <= 0x0FFF) {
                return (address & 0x7FF) | (this.R1 << 10);
            } else if (address <= 0x13FF) {
                return (address & 0x3FF) | (this.R2 << 10);
            } else if (address <= 0x17FF) {
                return (address & 0x3FF) | (this.R3 << 10);
            } else if (address <= 0x1BFF) {
                return (address & 0x3FF) | (this.R4 << 10);
            } else {
                return (address & 0x3FF) | (this.R5 << 10);
            }
        } else {
            if (address <= 0x03FF) {
                return (address & 0x3FF) | (this.R2 << 10);
            } else if (address <= 0x07FF) {
                return (address & 0x3FF) | (this.R3 << 10);
            } else if (address <= 0x0BFF) {
                return (address & 0x3FF) | (this.R4 << 10);
            } else if (address <= 0x0FFF) {
                return (address & 0x3FF) | (this.R5 << 10);
            } else if (address <= 0x17FF) {
                return (address & 0x7FF) | (this.R0 << 10);
            } else {
                return (address & 0x7FF) | (this.R1 << 10);
            }
        }
    }

    private int mapPrgRomAddress(int address) {
        address &= 0x7FFF;
        if ((this.bankSelect & (1 << 6)) == 0) {
            if (address <= 0x1FFF) {
                return (address & 0x1FFF) | (this.R6 << 13);
            } else if (address <= 0x3FFF) {
                return (address & 0x1FFF) | (this.R7 << 13);
            } else if (address <= 0x5FFF) {
                return (address & 0x1FFF) | (0xFE << 13);
            } else {
                return (address & 0x1FFF) | (0xFF << 13);
            }
        } else {
            if (address <= 0x1FFF) {
                return (address & 0x1FFF) | (0xFE << 13);
            } else if (address <= 0x3FFF) {
                return (address & 0x1FFF) | (this.R7 << 13);
            } else if (address <= 0x5FFF) {
                return (address & 0x1FFF) | (this.R6 << 13);
            } else {
                return (address & 0x1FFF) | (0xFF << 13);
            }
        }
    }

    private void handlePPUAddressUpdate(int address) {
        if (address != this.previousPPUAddress) {
            if ((address & A12) != 0 && (this.previousPPUAddress & A12) == 0 && this.cyclesDown >= 4) {
                if (this.irqCounter == 0 || this.irqReload) {
                    this.irqCounter = this.irqCounterReload;
                    this.irqReload = false;
                } else {
                    this.irqCounter--;
                }

                if (this.irqCounter == 0 && this.irqEnabled) {
                    this.irqSignal = true;
                }
            }
            this.previousPPUAddress = address & 0xFFFF;
        }
    }

    @Override
    public boolean getIRQSignal() {
        return this.irqSignal;
    }

}
