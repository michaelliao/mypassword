package org.puppylab.mypassword.ui.model;

import java.util.ArrayList;
import java.util.List;

public class AppState {

    public enum Mode {
        EMPTY, DETAIL, EDIT
    }

    public List<VaultItem> allItems     = new ArrayList<>();
    public VaultItem       selectedItem = null;
    public Category        category     = Category.ALL;
    public Mode            mode         = Mode.EMPTY;
}
