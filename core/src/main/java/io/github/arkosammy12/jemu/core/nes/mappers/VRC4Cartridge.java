package io.github.arkosammy12.jemu.core.nes.mappers;

import io.github.arkosammy12.jemu.core.nes.NESEmulator;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;

public class VRC4Cartridge<E extends NESEmulator> extends VRC2Cartridge<E> {

    public VRC4Cartridge(E emulator, INESFile iNESFile) {
        super(emulator, iNESFile);
    }

    /*
    @Override
    protected int getA0Bit() {
        return switch (this.iNESFile.getMapperNumber()) {
            case 21 -> switch ()
            case 23 -> {}
            case 25 -> {}
            default -> throw new EmulatorException("Invalid mapper number %d for VRC4!".formatted(this.iNESFile.getMapperNumber()));
        };
    }

    @Override
    protected int getA1Bit() {
        return switch (this.iNESFile.getMapperNumber()) {
            case 21 -> {}
            case 23 -> {}
            case 25 -> {}
            default -> throw new EmulatorException("Invalid mapper number %d for VRC4!".formatted(this.iNESFile.getMapperNumber()));
        };
    }
     */

}
