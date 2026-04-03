package org.puppylab.mypassword.ui.model;

import java.util.ArrayList;
import java.util.List;

import org.puppylab.mypassword.core.data.AbstractItemData;

public class AppState {

    public enum Mode {
        EMPTY, DETAIL, EDIT
    }

    public boolean                unlocked     = false;
    public List<AbstractItemData> allItems     = new ArrayList<>();
    public AbstractItemData       selectedItem = null;
    public Category               category     = Category.ALL;
    public Mode                   mode         = Mode.EMPTY;
}
