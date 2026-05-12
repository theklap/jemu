package io.github.arkosammy12.jemu.core.nes;

import io.github.arkosammy12.jemu.core.common.Bus;
import io.github.arkosammy12.jemu.core.cpu.NES6502;

import static io.github.arkosammy12.jemu.core.nes.RP2C02.OAMDATA_ADDR;

// TODO: PAL implementation
public class RP2A03<E extends NESEmulator> implements Bus {

    public static final int SQ1_VOL_ADDR = 0x4000;
    public static final int SQ1_SWEEP_ADDR = 0x4001;
    public static final int SQ1_LO_ADDR = 0x4002;
    public static final int SQ1_HI_ADDR = 0x4003;
    public static final int SQ2_VOL_ADDR = 0x4004;
    public static final int SQ2_SWEEP_ADDR = 0x4005;
    public static final int SQ2_LO_ADDR = 0x4006;
    public static final int SQ2_HI_ADDR = 0x4007;
    public static final int TRI_LINEAR_ADDR = 0x4008;

    public static final int TRI_LO_ADDR = 0x400A;
    public static final int TRI_HI_ADDR = 0x400B;
    public static final int NOISE_VOL_ADDR = 0x400C;

    public static final int NOISE_LO_ADDR = 0x400E;
    public static final int NOISE_HI_ADDR = 0x400F;
    public static final int DMC_FREQ_ADDR = 0x4010;
    public static final int DMC_RAW_ADDR = 0x4011;
    public static final int DMC_START_ADDR = 0x4012;
    public static final int DMC_LEN_ADDR = 0x4013;

    public static final int OAMDMA_ADDR = 0x4014;
    public static final int SND_CHN_ADDR = 0x4015;
    public static final int JOY1_ADDR = 0x4016;
    public static final int JOY2_ADDR = 0x4017;

    private final E emulator;
    private final NES6502 cpu;
    private final NESAPU<?> apu;
    private final NESController<?> controller;

    private int oamDmaTransferredBytes = 256;
    private int oamDmaSourceAddressHighByte;
    private int oamDmaCurrentData = -1;

    private APUHalfCycleType apuHalfCycleType = APUHalfCycleType.GET;

    private int scheduleDmcDmaHaltCountdown;
    private DmcDmaStep dmcDmaStep = DmcDmaStep.NONE;
    private int dmcDmaAddress;
	private boolean dmcDmaRequestPending;

    private int internalDataBus;

    public RP2A03(E emulator, int apuSampleBufferSize) {
        this.emulator = emulator;
        this.cpu = new NES6502(emulator);
        this.apu = new NESAPU<>(emulator, apuSampleBufferSize);
        this.controller = new NESController<>(emulator);
    }

    public NES6502 getCpu() {
        return this.cpu;
    }

    public NESAPU<?> getApu() {
        return this.apu;
    }

    public NESController<?> getController() {
        return this.controller;
    }

    public APUHalfCycleType getCurrentApuHalfCycleType() {
        return this.apuHalfCycleType;
    }

    @Override
    public int readByte(int address) {
        if (!this.isDmaUsingExternalBusOnThisCycle()) {
            int readByte = this.emulator.getCpuBus().readByte(address);
            if (readByte >= 0) {
                this.internalDataBus = readByte & 0xFF;
            }
        }
        return this.internalDataBus;
    }

    @Override
    public void writeByte(int address, int value) {
        this.writeByteRicohCore(address, value);
    }

    private void writeByteRicohCore(int address, int value) {
        this.internalDataBus = value;
        this.emulator.getCpuBus().writeByte(address, value);
    }

    private int readByteDMA(int address) {
        int combinedAddress = this.getCombinedRicohAddress(address);
        int activatedRegisterByte = -1;

        if (combinedAddress >= 0x4000 && combinedAddress <= 0x401F) {
            activatedRegisterByte = this.readByteIO(combinedAddress);
        }

        int readByte;
        if (address >= 0x4000 && address <= 0x401F) {
            readByte = activatedRegisterByte;
        } else {
            readByte = this.emulator.getCpuBus().readByte(address);
            if (activatedRegisterByte >= 0) {
                if ((combinedAddress == JOY1_ADDR || combinedAddress == JOY2_ADDR) && readByte >= 0) {
                    readByte = (activatedRegisterByte & 0x1F) | (readByte & 0xE0);
                } else {
                    readByte = activatedRegisterByte;
                }
            }
        };

        if (readByte >= 0) {
            this.internalDataBus = readByte & 0xFF;
        }

        return this.internalDataBus;
    }

    public int readByteIO(int address) {
        if ((address >= SQ1_VOL_ADDR && address <= TRI_LINEAR_ADDR) || (address >= TRI_LO_ADDR && address <= NOISE_VOL_ADDR) || (address >= NOISE_LO_ADDR && address <= DMC_LEN_ADDR) || address == SND_CHN_ADDR) {
            int ret = this.apu.readByte(address);
            if (address == SND_CHN_ADDR) {
                ret = (ret & ~0b00100000) | (this.internalDataBus & 0b00100000);
            }
            return ret;
        } else if (address == OAMDMA_ADDR) {
            return -1;
        } else if (address == JOY1_ADDR) {
            return (this.controller.readJoy1() & ~0xE0) | (this.internalDataBus & 0xE0);
        } else if (address == JOY2_ADDR) {
            return (this.controller.readJoy2() & ~0xE0) | (this.internalDataBus & 0xE0);
        } else {
            return -1;
        }
    }

    public void writeByteIO(int address, int value) {
        if ((address >= SQ1_VOL_ADDR && address <= TRI_LINEAR_ADDR) || (address >= TRI_LO_ADDR && address <= NOISE_VOL_ADDR) || (address >= NOISE_LO_ADDR && address <= DMC_LEN_ADDR) || address == SND_CHN_ADDR || address == JOY2_ADDR) {
            this.apu.writeByte(address, value);
        } else if (address == OAMDMA_ADDR) {
            this.oamDmaSourceAddressHighByte = value & 0xFF;
            this.oamDmaTransferredBytes = 0;
        } else if (address == JOY1_ADDR) {
            this.controller.writeJoy1(value);
        }
    }

    public void cycleHalf() {
		boolean isHalted = this.cpu.isHalted();

		switch (this.cpu.getHalfCyclePhase()) {
			case PHI_1 -> this.cpu.cycle();
			case PHI_2 -> {
				this.cpu.cycle();

				this.controller.cycle();
                this.emulator.getCartridge().cycle();

				if (this.scheduleDmcDmaHaltCountdown > 0) {
					this.scheduleDmcDmaHaltCountdown--;

					if (this.scheduleDmcDmaHaltCountdown <= 0) {
						// If the start of DMC DMA coincides with the second-to-last or last OAM DMA GET, delay the former by an extra cycle
						if (this.apuHalfCycleType == APUHalfCycleType.GET && this.oamDmaCurrentData < 0 && (this.oamDmaTransferredBytes == 254 || this.oamDmaTransferredBytes == 255)) {
							this.scheduleDmcDmaHaltCountdown = 1;
						} else {
							this.startDmcDma();
						}
					}
				}

				this.apu.cycleHalf();
				this.cycleDma(isHalted);

				this.apuHalfCycleType = this.apuHalfCycleType.getOpposite();
			}
		}
	}

    private void startDmcDma() {
		this.scheduleDmcDmaHaltCountdown = 0;
		this.dmcDmaStep = DmcDmaStep.DUMMY;
	}

    private void cycleDma(boolean isHalted) {
        if (!isHalted || (this.oamDmaTransferredBytes >= 256 && this.dmcDmaStep == DmcDmaStep.NONE)) {
            return;
        }

        // TODO: Proper bus isolation in the specific case (which I still don't yet understand) that makes the DMA units not update the data bus
		switch (this.apuHalfCycleType) {
			case GET -> {
				switch (this.dmcDmaStep) {
					case NONE -> this.tickOamDmaGetIfOngoing();
					case DUMMY -> {
						// Transition DMC DMA to its alignment cycle if there's no ongoing OAM DMA
						if (this.oamDmaTransferredBytes < 256) {
							this.tickOamDmaGetIfOngoing();
							this.dmcDmaStep = DmcDmaStep.GET;
						} else {
							this.dmcDmaStep = DmcDmaStep.ALIGNMENT;
						}
					}
					// Needed so DMC DMA doesn't perform its read immediately after its dummy cycle whenever there's no OAM DMA by this point
					case ALIGNMENT -> this.dmcDmaStep = DmcDmaStep.GET;
					case GET -> {
                        // TODO: DMC DM bus conflicts...
						this.apu.writeDmcDma(this.readByteDMA(this.dmcDmaAddress));
						this.dmcDmaStep = DmcDmaStep.NONE;
						this.dmcDmaRequestPending = false;
					}
				}
			}
			case PUT -> {
				switch (this.dmcDmaStep) {
					case DUMMY -> {
						boolean finalOamPut = this.oamDmaCurrentData >= 0 && this.oamDmaTransferredBytes == 255;
						if (!finalOamPut) {
							// If DMC DMA starts by this point in the OAM DMA transfer, this cycle is the DMC DMA alignment cycle.
							// Do not transition DMC DMA to its GET step yet
							if (!(this.dmcDmaRequestPending && this.oamDmaCurrentData >= 0 && this.oamDmaTransferredBytes == 254)) {
								this.dmcDmaStep = DmcDmaStep.GET;
							}
						}
					}
					case ALIGNMENT -> this.dmcDmaStep = DmcDmaStep.GET;
				}

				if (this.oamDmaCurrentData >= 0 && this.oamDmaTransferredBytes < 256) {
					this.emulator.getCpuBus().writeByte(OAMDATA_ADDR, this.oamDmaCurrentData);
					this.oamDmaTransferredBytes++;
					this.oamDmaCurrentData = -1;
				}
			}
		}
	}

    // Assumes that the CPU is cycled before the DMA units
	private boolean isDmaUsingExternalBusOnThisCycle() {
		return switch (this.apuHalfCycleType) {
			case GET -> switch (this.dmcDmaStep) {
				case NONE, DUMMY, ALIGNMENT -> this.isOamDmaGetCycle();
				case GET -> this.cpu.isHalted();
			};
			case PUT -> switch (this.dmcDmaStep) {
				case NONE, DUMMY, ALIGNMENT, GET -> this.isOamDmaPutCycle();
			};
		};
	}

    private boolean isOamDmaGetCycle() {
        return this.oamDmaTransferredBytes < 256 && this.cpu.isHalted();
    }

    private boolean isOamDmaPutCycle() {
        return this.oamDmaCurrentData >= 0 && this.oamDmaTransferredBytes < 256 && this.cpu.isHalted();
    }

    private void tickOamDmaGetIfOngoing() {
		if (this.oamDmaTransferredBytes < 256) {
			this.oamDmaCurrentData = this.readByteDMA((this.oamDmaSourceAddressHighByte << 8) | (this.oamDmaTransferredBytes & 0xFF));
		}
	}

    void triggerDmcDma(NESAPU.DmcDmaType dmcDmaType, int address) {
		// Do not replace or stack an already pending or ongoing DMC DMA transfer.
		if (this.dmcDmaRequestPending || this.scheduleDmcDmaHaltCountdown > 0 || this.dmcDmaStep != DmcDmaStep.NONE) {
			return;
		}

		this.dmcDmaRequestPending = true;
		this.dmcDmaAddress = address & 0xFFFF;
		this.scheduleDmcDmaHaltCountdown = switch (dmcDmaType) {
			case LOAD -> switch (this.apuHalfCycleType) {
				case GET -> 4;
				case PUT -> 3;
			};
			case RELOAD -> switch (this.apuHalfCycleType) {
				case GET -> 2;
				case PUT -> 1;
			};
		};
	}
	
    public boolean getIRQSignal() {
        return this.apu.getIRQSignal();
    }

    public boolean getRDYSignal() {
        return this.oamDmaTransferredBytes < 256 || this.dmcDmaStep != DmcDmaStep.NONE;
    }

    private int getCombinedRicohAddress(int address) {
        return (this.cpu.getLastAddress() & 0xFFE0) | (address & 0x1F);
    }

    public enum APUHalfCycleType {
        GET,
        PUT;

        private APUHalfCycleType getOpposite() {
            return switch (this) {
                case GET -> PUT;
                case PUT -> GET;
            };
        }
    }

    private enum DmcDmaStep {
		NONE,
		DUMMY,
		// Alignment state needed for already finished OAM DMA while DMC DMA still needs an alignment cycle
		ALIGNMENT,
		GET
	}

}