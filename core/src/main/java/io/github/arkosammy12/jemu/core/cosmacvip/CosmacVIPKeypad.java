package io.github.arkosammy12.jemu.core.cosmacvip;

import io.github.arkosammy12.jemu.core.common.SystemController;

public class CosmacVIPKeypad<E extends CosmacVipEmulator> extends SystemController<E> {

    private final boolean[] keys = new boolean[16];
    private int latchedKey = 0;
    private boolean efx;

    public CosmacVIPKeypad(E emulator) {
        super(emulator);
    }

    @Override
    public void onActionPressed(Action action) {
        if (!(action instanceof Actions vipActions)) {
            return;
        }
        this.keys[vipActions.key] = true;
    }

    @Override
    public void onActionReleased(Action action) {
        if (!(action instanceof Actions vipActions)) {
            return;
        }
        this.keys[vipActions.key] = false;
    }

    public boolean getEFX() {
        return this.efx;
    }

    public void cycle() {
        this.efx = this.keys[this.latchedKey];
    }

    public void setLatchedKey(int value) {
        this.latchedKey = value & 0xF;
    }

    public enum Actions implements Action {
        KEY_0("0", 0x0),
        KEY_1("1", 0x1),
        KEY_2("2", 0x2),
        KEY_3("3", 0x3),
        KEY_4("4", 0x4),
        KEY_5("5", 0x5),
        KEY_6("6", 0x6),
        KEY_7("7", 0x7),
        KEY_8("8", 0x8),
        KEY_9("9", 0x9),
        KEY_A("A", 0xA),
        KEY_B("B", 0xB),
        KEY_C("C", 0xC),
        KEY_D("D", 0xD),
        KEY_E("E", 0xE),
        KEY_F("F", 0xF);

        private final String label;
        private final int key;

        Actions(String label, int key) {
            this.label = label;
            this.key = key;
        }

        @Override
        public String getLabel() {
            return this.label;
        }

    }

}
