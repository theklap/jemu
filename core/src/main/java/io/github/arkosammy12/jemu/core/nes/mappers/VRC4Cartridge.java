package io.github.arkosammy12.jemu.core.nes.mappers;

import io.github.arkosammy12.jemu.core.nes.NESEmulator;
import io.github.arkosammy12.jemu.core.nes.ines.INESFile;

public class VRC4Cartridge<E extends NESEmulator> extends VRC2Cartridge<E> {

    public VRC4Cartridge(E emulator, INESFile iNESFile) {
        super(emulator, iNESFile);
    }

}
