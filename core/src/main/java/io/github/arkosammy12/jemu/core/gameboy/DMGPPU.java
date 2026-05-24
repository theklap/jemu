package io.github.arkosammy12.jemu.core.gameboy;

import io.github.arkosammy12.jemu.core.common.Bus;
import io.github.arkosammy12.jemu.core.common.VideoGenerator;
import io.github.arkosammy12.jemu.core.cpu.SM83;
import io.github.arkosammy12.jemu.core.exceptions.EmulatorException;
import io.github.arkosammy12.jemu.core.util.ShiftRegister;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import java.util.Arrays;

import static io.github.arkosammy12.jemu.core.gameboy.DMGBus.*;

public class DMGPPU<E extends GameBoyEmulator> extends VideoGenerator<E> implements Bus {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 144;

    public static final int LCDC_ADDR = 0xFF40;
    public static final int STAT_ADDR = 0xFF41;
    public static final int SCY_ADDR = 0xFF42;
    public static final int SCX_ADDR = 0xFF43;
    public static final int LY_ADDR = 0xFF44;
    public static final int LYC_ADDR = 0xFF45;
    public static final int BGP_ADDR = 0xFF47;
    public static final int OBP0_ADDR = 0xFF48;
    public static final int OBP1_ADDR = 0xFF49;
    public static final int WY_ADDR = 0xFF4A;
    public static final int WX_ADDR = 0xFF4B;

    private static final int CYCLES_PER_SCANLINE = 456;
    private static final int SCANLINES_PER_FRAME = 154;

    private static final int[] DMG_PALETTE = {
            0x9BBC0F,
            0x8BAC0F,
            0x306230,
            0x0F380F
    };

    protected final byte[] vram = new byte[0x2000];
    private final byte[] oam = new byte[0x00A0]; // TODO: OAM BUG (ONLY FOR DMG) GODDAMMIT!

    private int lcdControl;
    private int ppuStatus; // TODO: STAT WRITE BUG (ONLY FOR DMG)!!!!
    protected int scrollY;
    protected int scrollX;
    private int lcdY;
    private int lcdYCompare;
    protected int backgroundPalette;
    protected int objectPalette0;
    protected int objectPalette1;
    private int windowY;
    private int windowX;

    // TODO: Implement the PPU behavior when the CPU is in STOP mode for the DMG and CGB
    protected final int[][] lcd;

    protected Mode currentMode = Mode.MODE_0_HBLANK;
    private int scanlineCycle;
    private int dotCycleIndex;
    protected int scanlineNumber;
    private int statModeForInterrupt;

    protected boolean enablePixelWrites;
    private int enablePixelWritesDelay;

    private boolean oldStatInterruptLine;

    private boolean windowPixelRendered;

    protected int pixelX;
    protected int discardedPixels;
    protected int windowLine;
    private boolean windowYCondition;
    private boolean windowXCondition;

    protected final Integer[] spriteBuffer = new Integer[10];
    private int scannedEntries = 0;

    protected final IntArrayFIFOQueue backgroundFifo = new IntArrayFIFOQueue(8);
    protected int bgFifoStep = 0;
    protected boolean bgFifoFirstFetch = true;
    protected int bgFifoFetcherX;
    protected int bgFifoCurrentTileNumber;
    protected int bgFifoTileDataEffectiveAddress;
    protected int bgFifoTileDataLow;
    protected int bgFifoTileDataHigh;

    protected final ShiftRegister spriteFifo = new ShiftRegister(8, 32);
    protected int spriteFifoCurrentEntryIndex;
    protected int spriteFifoStep = 0;
    protected int spriteFifoCurrentTileNumber;
    protected int spriteFifoTileDataEffectiveAddress;
    protected int spriteFifoTileDataLow;
    protected int spriteFifoTileDataHigh;

    private boolean armOamBugRead;
    private boolean armOamBugWrite;

    public DMGPPU(E emulator) {
        super(emulator);
        this.lcd = new int[this.getImageWidth()][this.getImageHeight()];
        for (int[] ints : this.lcd) {
            Arrays.fill(ints, this.getLcdOffColor());
        }
        Arrays.fill(this.spriteBuffer, null);
    }

    @Override
    public int getImageWidth() {
        return WIDTH;
    }

    @Override
    public int getImageHeight() {
        return HEIGHT;
    }

    protected int getLcdOffColor() {
        return 0x9BBC0F;
    }

    @Override
    public int readByte(int address) {
        if (address >= OAM_START && address <= OAM_END) {
            this.checkArmOamBugRead(address);
            int ppuMode = this.getPpuMode();
            if (Mode.MODE_0_HBLANK.matchesValue(ppuMode) || Mode.MODE_1_VBLANK.matchesValue(ppuMode) || !this.getLcdPpuEnable()) {
                return (int) this.oam[address - OAM_START] & 0xFF;
            } else {
                return 0xFF;
            }

        } else if (address >= VRAM_START && address <= VRAM_END) {
            if (!Mode.MODE_3_DRAWING.matchesValue(this.getPpuMode()) || !this.getLcdPpuEnable()) {
                return (int) this.vram[address - VRAM_START] & 0xFF;
            } else {
                return 0xFF;
            }
        } else {
            return switch (address) {
                case LCDC_ADDR -> this.lcdControl;
                case STAT_ADDR -> this.ppuStatus | 0b10000000;
                case SCY_ADDR -> this.scrollY;
                case SCX_ADDR -> this.scrollX;
                case LY_ADDR -> this.lcdY;
                case LYC_ADDR -> this.lcdYCompare;
                case BGP_ADDR -> this.backgroundPalette;
                case OBP0_ADDR -> this.objectPalette0;
                case OBP1_ADDR -> this.objectPalette1;
                case WY_ADDR -> this.windowY;
                case WX_ADDR -> this.windowX;
                default -> throw new EmulatorException("Invalid address $%04X for GameBoy PPU!".formatted(address));
            };
        }
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= OAM_START && address <= OAM_END) {
            this.checkArmOamBugWrite(address);
            int ppuMode = this.getPpuMode();
            if (Mode.MODE_0_HBLANK.matchesValue(ppuMode) || Mode.MODE_1_VBLANK.matchesValue(ppuMode) || !this.getLcdPpuEnable()) {
              this.oam[address - OAM_START] = (byte) value;
            }
        } else if (address >= VRAM_START && address <= VRAM_END) {
            if (!Mode.MODE_3_DRAWING.matchesValue(this.getPpuMode()) || !this.getLcdPpuEnable()) {
                this.vram[address - VRAM_START] = (byte) value;
            }
        } else {
            switch (address) {
                case LCDC_ADDR -> {
                    boolean oldLcdEnable = this.getLcdPpuEnable();
                    this.lcdControl = value & 0xFF;
                    boolean newLcdEnable = this.getLcdPpuEnable();
                    if (oldLcdEnable != newLcdEnable) {
                        this.scanlineNumber = 0;
                        this.lcdY = 0;
                        this.scanlineCycle = 0;
                        this.currentMode = Mode.MODE_0_HBLANK;
                        this.setPpuMode(Mode.MODE_0_HBLANK.getValue());
                        this.setStatModeForInterrupt(Mode.MODE_0_HBLANK.getValue());
                    }
                    if (!oldLcdEnable && newLcdEnable) {
                        this.onLcdOn();
                    } else if (oldLcdEnable && !newLcdEnable) {
                        this.onLcdOff();
                    }
                }
                case STAT_ADDR -> this.ppuStatus = (value & 0b11111000) | (this.ppuStatus & 0b111);
                case SCY_ADDR -> this.scrollY = value & 0xFF;
                case SCX_ADDR -> this.scrollX = value & 0xFF;
                case LY_ADDR -> {}
                case LYC_ADDR -> this.lcdYCompare = value & 0xFF;
                case BGP_ADDR -> this.backgroundPalette = value & 0xFF;
                case OBP0_ADDR -> this.objectPalette0 = value & 0xFF;
                case OBP1_ADDR -> this.objectPalette1 = value & 0xFF;
                case WY_ADDR -> this.windowY = value & 0xFF;
                case WX_ADDR -> this.windowX = value & 0xFF;
                default -> throw new EmulatorException("Invalid address $%04X for GameBoy PPU!".formatted(address));
            }
        }
    }

    public void checkArmOamBugRead(int address) {
        if (address >= OAM_START && address <= OAM_END && this.getLcdPpuEnable()) {
            this.armOamBugRead = true;
        }
    }

    public void checkArmOamBugWrite(int address) {
        if (address >= OAM_START && address <= OAM_END && this.getLcdPpuEnable()) {
            this.armOamBugWrite = true;
        }
    }

    private void onLcdOn() {
        this.enablePixelWritesDelay = 2;
        this.scanlineCycle = 4;
    }

    private void onLcdOff() {
        this.enablePixelWrites = false;
        this.enablePixelWritesDelay = -1;
        this.armOamBugRead = false;
        this.armOamBugWrite = false;
        for (int[] ints : this.lcd) {
            Arrays.fill(ints, this.getLcdOffColor());
        }
        this.emulator.getHost().getVideoDriver().ifPresent(driver -> driver.outputFrame(this.lcd));
    }

    public void cycle() {
        this.cycleDot(0);
        this.cycleDot(1);
        this.cycleDot(2);
        this.cycleDot(3);
    }

    private void cycleDot(int tCycle) {
        if (!this.getLcdPpuEnable()) {
            return;
        }
        this.nextState();

        switch (this.currentMode) {
            case MODE_0_HBLANK -> this.onHBlank();
            case MODE_1_VBLANK -> this.onVBlank();
            case MODE_2_OAM_SCAN -> this.onOamScan();
            case MODE_3_DRAWING -> this.onDrawing();
        }

        if (tCycle == 2) {
            // TODO: Reincorporate once passing all oam bug tests from blargg's
            if (this.armOamBugRead) {
                //this.doOamBugRead();
            } else if (this.armOamBugWrite) {
                //this.doOamBugWrite();
            }
            this.armOamBugRead = false;
            this.armOamBugWrite = false;
        }

        this.scanlineCycle++;
        if (this.scanlineCycle >= CYCLES_PER_SCANLINE) {
            this.scanlineCycle = 0;
            this.dotCycleIndex = 0;

            int originalScanlineNumber = this.scanlineNumber;
            this.scanlineNumber = (this.scanlineNumber + 1) % SCANLINES_PER_FRAME;
            if (originalScanlineNumber != 153) {
                this.lcdY = (this.lcdY + 1) % SCANLINES_PER_FRAME;
                this.clearLyEqualsLycFlag();
            }

            if (this.windowPixelRendered) {
                this.windowPixelRendered = false;
                this.windowLine = (this.windowLine + 1) % SCANLINES_PER_FRAME;
            }
        }

        if (this.scanlineCycle >= 3) {
            if (this.lcdY == this.lcdYCompare) {
                this.setLyEqualsLycFlag();
            } else {
                this.clearLyEqualsLycFlag();
            }
        }

        boolean statInterruptLine = false;
        statInterruptLine |= this.getLycInterruptSelect() && this.getLyEqualsLycFlag();
        statInterruptLine |= this.getMode0InterruptSelect() && Mode.MODE_0_HBLANK.matchesValue(this.statModeForInterrupt);
        statInterruptLine |= this.getMode1InterruptSelect() && Mode.MODE_1_VBLANK.matchesValue(this.statModeForInterrupt);
        statInterruptLine |= this.getMode2InterruptSelect() && Mode.MODE_2_OAM_SCAN.matchesValue(this.statModeForInterrupt);
        if (!this.oldStatInterruptLine && statInterruptLine) {
            this.triggerStatInterrupt();
        }
        this.oldStatInterruptLine = statInterruptLine;
    }

    private void nextState() {
        Mode oldMode = this.currentMode;
        if (this.scanlineCycle == 0) {
            if (this.scanlineNumber >= 144) {
                this.currentMode = Mode.MODE_1_VBLANK;
            } else {
                this.currentMode = Mode.MODE_2_OAM_SCAN;
            }
        } else if (this.scanlineCycle == 80) {
            if (this.scanlineNumber >= 144) {
                this.currentMode = Mode.MODE_1_VBLANK;
            } else {
                this.currentMode = Mode.MODE_3_DRAWING;
            }
        } else if (this.pixelX >= 168) {
            this.currentMode = Mode.MODE_0_HBLANK;
        }
        if (oldMode != this.currentMode) {
            this.dotCycleIndex = 0;
        }
    }

    private void onVBlank() {
        switch (this.dotCycleIndex) {
            case 0 -> {
                this.dotCycleIndex = 1;
            }
            case 1 -> {
                this.dotCycleIndex = 2;
            }
            case 2 -> {
                this.dotCycleIndex = 3;
            }
            case 3 -> {
                if (this.scanlineNumber == 144) {

                    this.setPpuMode(Mode.MODE_1_VBLANK.getValue());
                    this.setStatModeForInterrupt(Mode.MODE_1_VBLANK.getValue());
                    this.triggerVBlankInterrupt();
                    this.windowYCondition = false;
                    this.windowLine = 0;

                    // For some reason, Mooneye test vblank_stat_intr-GS.gb expects this
                    if (this.getMode2InterruptSelect()) {
                        this.triggerStatInterrupt();
                    }

                    if (this.enablePixelWritesDelay > 0) {
                        this.enablePixelWritesDelay--;
                        if (this.enablePixelWritesDelay == 0) {
                            this.enablePixelWrites = true;
                        }
                    }

                    this.emulator.getHost().getVideoDriver().ifPresent(driver -> driver.outputFrame(this.lcd));
                } else if (this.scanlineNumber == 153) {
                    this.lcdY = 0;
                    this.clearLyEqualsLycFlag();
                }
                this.dotCycleIndex = 4;
            }
            case 4 -> {}
        }
    }

    private void onHBlank() {
        switch (this.dotCycleIndex) {
            case 0 -> {
                this.onHBlankStart();
                this.dotCycleIndex = 1;
            }
            case 1 -> {
                this.dotCycleIndex = 2;
            }
            case 2 -> {
                this.dotCycleIndex = 3;
            }
            case 3 -> {
                this.setStatModeForInterrupt(Mode.MODE_0_HBLANK.getValue());
                this.setPpuMode(Mode.MODE_0_HBLANK.getValue());
                this.dotCycleIndex = 4;
            }
            case 4 -> {}
        }
    }

    protected void onHBlankStart() {

        this.scannedEntries = 0;

        this.pixelX = 0;
        this.discardedPixels = 0;
        this.windowXCondition = false;

        this.backgroundFifo.clear();
        this.bgFifoStep = 0;
        this.bgFifoFirstFetch = true;
        this.bgFifoFetcherX = 0;
        this.bgFifoCurrentTileNumber = 0;
        this.bgFifoTileDataEffectiveAddress = 0;
        this.bgFifoTileDataLow = 0;
        this.bgFifoTileDataHigh = 0;

        Arrays.fill(this.spriteBuffer, null);

        this.spriteFifo.clear();

        this.spriteFifoStep = 0;
        this.spriteFifoCurrentEntryIndex = -1;
        this.spriteFifoCurrentTileNumber = 0;
        this.spriteFifoTileDataEffectiveAddress = 0;
        this.spriteFifoTileDataLow = 0;
        this.spriteFifoTileDataHigh = 0;
    }

    private void onOamScan() {
        switch (this.dotCycleIndex) {
            case 0 -> {
                if (this.scanlineNumber == this.windowY) {
                    this.windowYCondition = true;
                }
                this.dotCycleIndex = 1;
            }
            case 1 -> {
                this.tickOamScan();
                this.dotCycleIndex = 2;
            }
            case 2 -> {
                this.dotCycleIndex = 3;
            }
            case 3 -> {
                this.setPpuMode(Mode.MODE_2_OAM_SCAN.getValue());
                this.setStatModeForInterrupt(Mode.MODE_2_OAM_SCAN.getValue());
                this.tickOamScan();
                this.dotCycleIndex = 4;
            }
            case 4 -> {
                this.dotCycleIndex = 5;
            }
            case 5 -> {
                this.tickOamScan();
                this.dotCycleIndex = 4;
            }
        }
    }

    // TODO: The OAM bus has a 16-bit address bus. During OAM scan, the PPU reads only the X and Y bytes to determine whether they are in range.
    // During sprite fetching, the PPU fetches the attribute and tile index bytes at once.
    // Store only the X and Y bytes in the sprite buffer alongside the OAM index from which to calculate the attribute and tile index bytes fetch addresses.
    private void tickOamScan() {
        int spriteY = this.getOAMByte(0xFE00 + (this.scannedEntries * 4));
        int spriteX = this.getOAMByte(0xFE00 + (this.scannedEntries * 4) + 1);
        int tileIndex = this.getOAMByte(0xFE00 + (this.scannedEntries * 4) + 2);
        int spriteAttributes = this.getOAMByte(0xFE00 + (this.scannedEntries * 4) + 3);
        for (int i = 0; i < 10; i++) {
            if (this.spriteBuffer[i] == null) {
                if ((this.scanlineNumber + 16 >= spriteY) && (this.scanlineNumber + 16 < spriteY + (this.getObjectSize() ? 16 : 8))) {
                    this.spriteBuffer[i] = createSpriteBufferEntry(spriteY, spriteX, tileIndex, spriteAttributes);
                }
                break;
            }
        }
        this.scannedEntries++;
    }

    private void doOamBugRead() {
        int cur = ((this.scannedEntries / 2) + 1) * 8;
        if (cur < 8 || cur >= 160 || !Mode.MODE_2_OAM_SCAN.matchesValue(this.currentMode.getValue())) {
            return;
        }
        int prev = cur - 8;
        int rowGroup = cur & 0x18;
        if (rowGroup == 0x08 || rowGroup == 0x18) {
            this.oam[prev] = (byte) ((this.getOAMByteFromIndex(prev) | (this.getOAMByteFromIndex(cur) & this.getOAMByteFromIndex(prev + 4))) & 0xFF);
            this.oam[prev + 1] = (byte) ((this.getOAMByteFromIndex(prev + 1) | (this.getOAMByteFromIndex(cur + 1) & this.getOAMByteFromIndex(prev + 5))) & 0xFF);
            for (int i = 0; i <= 7; i++) {
                this.oam[cur + i] = this.oam[prev + i];
            }
        } else if (rowGroup == 0x10 && cur < 0x98) {
            int prev2 = cur - 16;
            this.oam[prev] = (byte) (((this.getOAMByteFromIndex(prev) & (this.getOAMByteFromIndex(prev2) | this.getOAMByteFromIndex(cur) | this.getOAMByteFromIndex(prev + 4))) | (this.getOAMByteFromIndex(prev2) & this.getOAMByteFromIndex(cur) & this.getOAMByteFromIndex(prev + 4))) & 0xFF);
            this.oam[prev + 1] = (byte) (((this.getOAMByteFromIndex(prev + 1) & (this.getOAMByteFromIndex(prev2 + 1) | this.getOAMByteFromIndex(cur + 1) | this.getOAMByteFromIndex(prev + 5))) | (this.getOAMByteFromIndex(prev2 + 1) & this.getOAMByteFromIndex(cur + 1) & this.getOAMByteFromIndex(prev + 5))) & 0xFF);
            for (int i = 0; i <= 7; i++) {
                this.oam[prev2 + i] = this.oam[prev + i];
                this.oam[cur + i] = this.oam[prev + i];
            }
        } else if (rowGroup == 0x00 && cur < 0x98) {
            // TODO: Implement
        }
    }

    private void doOamBugWrite() {
        int cur = ((this.scannedEntries / 2) + 1) * 8;
        if (cur < 8 || cur >= 160 || !Mode.MODE_2_OAM_SCAN.matchesValue(this.currentMode.getValue())) {
            return;
        }
        int prev = cur - 8;
        this.oam[cur] = (byte) (bitwiseMajority(this.getOAMByteFromIndex(cur), this.getOAMByteFromIndex(prev), this.getOAMByteFromIndex(prev + 4)) & 0xFF);
        this.oam[cur + 1] = (byte) (bitwiseMajority(this.getOAMByteFromIndex(cur + 1), this.getOAMByteFromIndex(prev + 1),  this.getOAMByteFromIndex(prev + 5)) & 0xFF);
        for (int i = 2; i <= 7; i++) {
            this.oam[cur + i] = this.oam[prev + i];
        }
    }

    private static int bitwiseMajority(int x, int y, int z) {
        return (x & y) | (x & z) | (y & z);
    }

    private void onDrawing() {
        switch (this.dotCycleIndex) {
            case 0 -> {
                this.tickDraw();
                this.dotCycleIndex = 1;
            }
            case 1 -> {
                this.tickDraw();
                this.dotCycleIndex = 2;
            }
            case 2 -> {
                this.tickDraw();
                this.dotCycleIndex = 3;
            }
            case 3 -> {
                this.setPpuMode(Mode.MODE_3_DRAWING.getValue());
                this.setStatModeForInterrupt(Mode.MODE_3_DRAWING.getValue());
                this.tickDraw();
                this.dotCycleIndex = 4;
            }
            case 4 -> {
                this.tickDraw();
            }
        }
    }

    //private int dotsSpentInSpritePlusStalling;

    private void tickDraw() {
        boolean originalWindowCondition = this.isRenderingWindow();
        if (this.pixelX == this.windowX + 1 && this.getWindowEnable()) {
            this.windowXCondition = true;
        }

        if (!originalWindowCondition && this.isRenderingWindow()) {
            this.bgFifoStep = 0;
            this.bgFifoFetcherX = 0;
            this.windowPixelRendered = true;
            this.backgroundFifo.clear();
        }

        int currentSpriteEntryIndex = this.getSpriteEntryIndexMatchingX(this.pixelX);
        boolean fetchingSprite = this.isFetchingSprites(currentSpriteEntryIndex);

        if (!fetchingSprite) {
            this.tickPixelShifter();
        }

        if (!fetchingSprite || this.backgroundFifo.isEmpty() || (this.bgFifoStep <= 4)) {
            this.tickBackgroundFifo();
        } else {
            if (this.spriteFifoCurrentEntryIndex < 0) {
                this.spriteFifoCurrentEntryIndex = currentSpriteEntryIndex;
            }
            this.tickSpriteFifo();
        }

    }

    protected void tickBackgroundFifo() {
        switch (this.bgFifoStep) {
            case 0 -> {
                this.bgFifoStep = 1;
            }
            case 1 -> {
                if (this.isRenderingWindow()) {
                    int tileMapBase = this.getWindowTileMap() ? 0x9C00 : 0x9800;
                    int tileX = this.bgFifoFetcherX & 0x1F;
                    int tileY = this.windowLine >>> 3;
                    int tileMapIndex = tileX + (tileY * 32);
                    tileMapIndex &= 0x3FF;
                    int address = tileMapBase + tileMapIndex;
                    this.bgFifoCurrentTileNumber = this.getVRAMByte(address);
                } else {
                    int tileMapBase = this.getBackgroundTileMap() ? 0x9C00 : 0x9800;
                    int tileX = ((this.pixelX + this.scrollX) >> 3) & 0x1F;
                    int tileY = ((this.scanlineNumber + this.scrollY) & 0xFF) >>> 3;
                    int tileMapIndex = tileX + (tileY * 32);
                    tileMapIndex &= 0x3FF;
                    int address = tileMapBase + tileMapIndex;
                    this.bgFifoCurrentTileNumber = this.getVRAMByte(address);
                }
                this.bgFifoStep = 2;
            }
            case 2 -> {
                this.bgFifoStep = 3;
            }
            case 3 -> {
                if (this.bgFifoFirstFetch) {
                    this.bgFifoFirstFetch = false;
                    for (int i = 7; i >= 0; i--) {
                        this.backgroundFifo.enqueue(0);
                    }
                    this.bgFifoStep = 0;
                } else {
                    int effectiveAddress;
                    if (this.getBackgroundAndWindowTiles()) {
                        effectiveAddress = 0x8000 + (this.bgFifoCurrentTileNumber * 16);
                    } else {
                        byte signedTileNumber =  (byte) this.bgFifoCurrentTileNumber;
                        effectiveAddress = 0x9000 + ((int) signedTileNumber * 16);
                    }

                    if (this.isRenderingWindow()) {
                        effectiveAddress = (effectiveAddress + (2 * (this.windowLine % 8))) & 0xFFFF;
                    } else {
                        effectiveAddress = (effectiveAddress + (2 * ((this.scanlineNumber + this.scrollY) % 8))) & 0xFFFF;
                    }

                    this.bgFifoTileDataEffectiveAddress = effectiveAddress;
                    this.bgFifoTileDataLow = this.getVRAMByte(effectiveAddress);

                    this.bgFifoStep = 4;
                }
            }
            case 4 -> {
                this.bgFifoStep = 5;
            }
            case 5 -> {
                this.bgFifoTileDataHigh = this.getVRAMByte((this.bgFifoTileDataEffectiveAddress + 1) & 0xFFFF);
                if (this.backgroundFifo.isEmpty()) {
                    this.pushBgPixels();
                    this.bgFifoStep = 0;
                } else {
                    this.bgFifoStep = 6;
                }
            }
            case 6 -> {
                if (this.backgroundFifo.isEmpty()) {
                    this.pushBgPixels();
                    this.bgFifoStep = 0;
                } else {
                    this.bgFifoStep = 6;
                }
            }
        }
    }

    protected void pushBgPixels() {
        for (int i = 7; i >= 0; i--) {
            int low = (this.bgFifoTileDataLow >>> i) & 1;
            int high = (this.bgFifoTileDataHigh >>> i) & 1;
            this.backgroundFifo.enqueue((high << 1) | low);
        }
        this.bgFifoFetcherX++;
    }

    protected boolean isFetchingSprites(int currentSpriteEntryIndex) {
        return currentSpriteEntryIndex >= 0 && this.getObjectEnable();
    }

    @SuppressWarnings("DuplicatedCode")
    protected void tickSpriteFifo() {
        switch (this.spriteFifoStep) {
            case 0 -> {
                this.spriteFifoStep = 1;
            }
            case 1 -> {
                this.spriteFifoCurrentTileNumber = getTileIndexFromSpriteEntry(this.spriteBuffer[this.spriteFifoCurrentEntryIndex]);
                this.spriteFifoStep = 2;
            }
            case 2 -> {
                this.spriteFifoStep = 3;
            }
            case 3 -> {
                boolean objSize = this.getObjectSize();
                int spriteEntry = this.spriteBuffer[this.spriteFifoCurrentEntryIndex];
                int spriteAttributes = getSpriteAttributesFromEntry(spriteEntry);
                boolean yFlip = getYFlipFromObjAttributes(spriteAttributes);
                int spriteY = getSpriteYFromSpriteEntry(spriteEntry);
                int tileIndex = this.spriteFifoCurrentTileNumber;

                int width = objSize ? 15 : 7;
                if (objSize) {
                    tileIndex &= ~1;
                }

                int row = ((this.scanlineNumber + 16) - spriteY) % (width + 1);
                if (row < 0) {
                    row += (width + 1);
                }

                int offset = yFlip ? (width - row) * 2 : row * 2;
                this.spriteFifoTileDataEffectiveAddress = (0x8000 + tileIndex * 16 + offset) & 0xFFFF;

                this.spriteFifoTileDataLow = this.getVRAMByte(this.spriteFifoTileDataEffectiveAddress);
                this.spriteFifoStep = 4;
            }
            case 4 -> {
                this.spriteFifoStep = 5;
            }
            case 5 -> {
                this.spriteFifoTileDataHigh = this.getVRAMByte((this.spriteFifoTileDataEffectiveAddress + 1) & 0xFFFF);

                int spriteEntry = this.spriteBuffer[this.spriteFifoCurrentEntryIndex];
                int spriteX = getSpriteXFromSpriteEntry(spriteEntry);
                int spriteAttributes = getSpriteAttributesFromEntry(spriteEntry);
                boolean xFlip = getXFlipFromObjAttributes(spriteAttributes);
                boolean priority = getPriorityFromObjAttributes(spriteAttributes);
                boolean palette = getDmgPaletteFromObjAttributes(spriteAttributes);

                for (int i = 0; i < 8; i++) {
                    if (spriteX + i < 8) {
                        continue;
                    }
                    int bit = xFlip ? 1 << i : 1 << (7 - i);
                    int low = (this.spriteFifoTileDataLow & bit) != 0 ? 1 : 0;
                    int high = (this.spriteFifoTileDataHigh & bit) != 0 ? 1 : 0;
                    int colorNumber = (low | (high << 1));
                    if (colorNumber == 0) {
                        continue;
                    }

                    int currentQueuedPixel = this.spriteFifo.get(i);
                    if (getDmgColorNumberFromObjPixelEntry(currentQueuedPixel) == 0) {
                        this.spriteFifo.set(i, createDmgObjPixelEntry(colorNumber, priority, palette));
                    }
                }

                this.spriteBuffer[this.spriteFifoCurrentEntryIndex] = null;
                this.spriteFifoCurrentEntryIndex = -1;
                this.spriteFifoStep = 0;

                //this.spriteCount++;
            }
        }
    }

    //protected int spriteCount;

    protected void tickPixelShifter() {
        if (this.backgroundFifo.isEmpty()) {
            return;
        }

        int bgPixel = this.backgroundFifo.dequeueInt();
        if (!this.getBackgroundAndWindowEnable()) {
            bgPixel = 0;
        }
        int bgPaletteIndex = (this.backgroundPalette >> (bgPixel * 2)) & 0b11;
        Integer finalPixel = DMG_PALETTE[bgPaletteIndex];

        int bgDiscardTarget = this.scrollX % 8;
        if (!this.isRenderingWindow() && this.discardedPixels < bgDiscardTarget) {
            this.discardedPixels++;
            finalPixel = null;
        }

        int objPixel = this.spriteFifo.shiftHead(0);
        int objColorNumber = getDmgColorNumberFromObjPixelEntry(objPixel);
        if (!this.getObjectEnable()) {
            objColorNumber = 0;
        }
        if (objColorNumber != 0 && !(getDmgPriorityForObjPixelEntry(objPixel) && bgPixel != 0)) {
            int objPaletteIndex = ((getDmgPaletteForObjPixelEntry(objPixel) ? this.objectPalette1 : this.objectPalette0) >>> (objColorNumber * 2)) & 0b11;
            finalPixel = DMG_PALETTE[objPaletteIndex];
        }

        // TODO: Emulate color shown in the LCD during CPU STOP mode depending on which mode the STOP mode lands on. Same for CGB
        if (finalPixel != null) {
            if (this.pixelX >= 8 && this.enablePixelWrites) {
                this.lcd[this.pixelX - 8][this.scanlineNumber] = finalPixel;
            }
            this.pixelX++;
        }
    }

    protected boolean getLcdPpuEnable() {
        return (this.lcdControl & 0b10000000) != 0;
    }

    protected boolean getWindowTileMap() {
        return (this.lcdControl & 0b01000000) != 0;
    }

    private boolean getWindowEnable() {
        return (this.lcdControl & 0b00100000) != 0;
    }

    protected boolean getBackgroundAndWindowTiles() {
        return (this.lcdControl & 0b00010000) != 0;
    }

    protected boolean getBackgroundTileMap() {
        return (this.lcdControl & 0b00001000) != 0;
    }

    protected boolean getObjectSize() {
        return (this.lcdControl & 0b00000100) != 0;
    }

    protected boolean getObjectEnable() {
        return (this.lcdControl & 0b00000010) != 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean getBackgroundAndWindowEnable() {
        return (this.lcdControl & 0b00000001) != 0;
    }

    private boolean getLycInterruptSelect() {
        return (this.ppuStatus & 0b01000000) != 0;
    }

    private boolean getMode2InterruptSelect() {
        return (this.ppuStatus & 0b00100000) != 0;
    }

    private boolean getMode1InterruptSelect() {
        return (this.ppuStatus & 0b00010000) != 0;
    }

    private boolean getMode0InterruptSelect() {
        return (this.ppuStatus & 0b00001000) != 0;
    }

    public boolean getLyEqualsLycFlag() {
        return (this.ppuStatus & 0b00000100) != 0;
    }

    protected int getPpuMode() {
        return this.ppuStatus & 0b11;
    }

    private void setPpuMode(int mode) {
        this.ppuStatus = (this.ppuStatus & 0b11111100) | (mode & 0b11);
    }

    private void setStatModeForInterrupt(int mode) {
        if (!(mode >= 0 && mode <= 3)) {
            throw new EmulatorException(new IllegalArgumentException("Invalid GameBoy PPU STAT mode for interrupt value %d!".formatted(mode)));
        }
        this.statModeForInterrupt = mode;
    }

    private void setLyEqualsLycFlag() {
        this.ppuStatus |= 0b100;
    }

    private void clearLyEqualsLycFlag() {
        this.ppuStatus &= ~0b100;
    }

    public void writeOAMDMA(int address, int value) {
        if (address >= OAM_START && address <= OAM_END) {
            this.oam[address - OAM_START] = (byte) value;
        } else {
            throw new EmulatorException("Invalid GameBoy OAM address \"%04X\"!".formatted(address));
        }
    }

    private int getOAMByte(int address) {
        if (address >= OAM_START && address <= OAM_END) {
            return (int) this.oam[address - OAM_START] & 0xFF;
        } else {
            throw new EmulatorException("Invalid GameBoy OAM address \"%04X\"!".formatted(address));
        }
    }

    private int getOAMByteFromIndex(int index) {
        if (index >= 0 && index < this.oam.length) {
            return (int) this.oam[index] & 0xFF;
        } else {
            throw new EmulatorException("Invalid GameBoy OAM index %d!".formatted(index));
        }
    }

    protected int getVRAMByte(int address) {
        if (address >= VRAM_START && address <= VRAM_END) {
            return (int) this.vram[address - VRAM_START] & 0xFF;
        } else {
            throw new EmulatorException("Invalid GameBoy VRAM address \"%04X\"!".formatted(address));
        }
    }

    private int getSpriteEntryIndexMatchingX(int x) {
        for (int i = 0; i < 10; i++) {
            Integer spriteEntry = this.spriteBuffer[i];
            if (spriteEntry != null && getSpriteXFromSpriteEntry(spriteEntry) == x) {
                return i;
            }
        }
        return -1;
    }

    protected boolean isRenderingWindow() {
        return this.windowXCondition && this.windowYCondition;
    }

    private void triggerVBlankInterrupt() {
        DMGBus<?> bus = this.emulator.getBus();
        bus.setIF(bus.getIF() | SM83.VBLANK_MASK);
    }

    private void triggerStatInterrupt() {
        DMGBus<?> bus = this.emulator.getBus();
        bus.setIF(bus.getIF() | SM83.LCD_MASK);
    }

    private static int createSpriteBufferEntry(int spriteY, int spriteX, int tileIndex, int spriteAttributes) {
        return ((spriteAttributes & 0xFF) << 24) | ((tileIndex & 0xFF) << 16) | ((spriteX & 0xFF) << 8) | (spriteY & 0xFF);
    }

    protected static int getSpriteAttributesFromEntry(int entry) {
        return (entry >>> 24) & 0xFF;
    }

    protected static int getTileIndexFromSpriteEntry(int entry) {
        return (entry >>> 16) & 0xFF;
    }

    protected static int getSpriteXFromSpriteEntry(int entry) {
        return (entry >>> 8) & 0xFF;
    }

    protected static int getSpriteYFromSpriteEntry(int entry) {
        return (entry) & 0xFF;
    }

    protected static boolean getPriorityFromObjAttributes(int spriteAttributes) {
        return (spriteAttributes & 0b10000000) != 0;
    }

    protected static boolean getYFlipFromObjAttributes(int spriteAttributes) {
        return (spriteAttributes & 0b01000000) != 0;
    }

    protected static boolean getXFlipFromObjAttributes(int spriteAttributes) {
        return (spriteAttributes & 0b00100000) != 0;
    }

    private static boolean getDmgPaletteFromObjAttributes(int spriteAttributes) {
        return (spriteAttributes & 0b00010000) != 0;
    }

    private static int createDmgObjPixelEntry(int colorNumber, boolean priority, boolean palette) {
        return ((palette ? 1 : 0) << 16) | ((priority ? 1 : 0) << 8) | colorNumber;
    }

    protected static int getDmgColorNumberFromObjPixelEntry(int pixel) {
        return pixel & 0b11;
    }

    protected static boolean getDmgPriorityForObjPixelEntry(int pixel) {
        return ((pixel >>> 8) & 1) != 0;
    }

    protected static boolean getDmgPaletteForObjPixelEntry(int pixel) {
        return ((pixel >>> 16) & 1) != 0;
    }

    public enum Mode {
        MODE_0_HBLANK(0),
        MODE_1_VBLANK(1),
        MODE_2_OAM_SCAN(2),
        MODE_3_DRAWING(3);

        private final int value;

        Mode(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public boolean matchesValue(int value) {
            return value == this.value;
        }

    }

}