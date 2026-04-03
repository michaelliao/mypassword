package org.puppylab.mypassword.ui.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.ui.model.AppState;
import org.puppylab.mypassword.ui.model.AppState.Mode;
import org.puppylab.mypassword.ui.model.Category;
import org.puppylab.mypassword.ui.view.EmptyView;
import org.puppylab.mypassword.ui.view.IdentityDetailView;
import org.puppylab.mypassword.ui.view.IdentityEditView;
import org.puppylab.mypassword.ui.view.ItemListView;
import org.puppylab.mypassword.ui.view.LoginDetailView;
import org.puppylab.mypassword.ui.view.LoginEditView;
import org.puppylab.mypassword.ui.view.NoteDetailView;
import org.puppylab.mypassword.ui.view.NoteEditView;
import org.puppylab.mypassword.ui.view.ToolbarView;
import org.puppylab.mypassword.ui.view.UnlockView;

public class MainController {

    private final AppState     state = new AppState();
    private final VaultManager vaultManager;

    // ── unlock layer ──────────────────────────────────────────────────
    private final UnlockView  unlockView;
    private final Composite   topContainer;
    private final StackLayout topStack;
    private final Composite   mainContent;

    // ── content layer ─────────────────────────────────────────────────
    private final ToolbarView        toolbar;
    private final ItemListView       listView;
    private final EmptyView          emptyView;
    private final LoginDetailView    loginDetailView;
    private final NoteDetailView     noteDetailView;
    private final IdentityDetailView identityDetailView;
    private final LoginEditView      loginEditView;
    private final NoteEditView       noteEditView;
    private final IdentityEditView   identityEditView;
    private final Composite          rightContainer;
    private final StackLayout        rightStack;

    // active composites – set before switchMode(DETAIL/EDIT)
    private Composite activeDetailComposite;
    private Composite activeEditComposite;

    // ── per-type in-memory stores (keyed by id) ───────────────────────
    private final Map<Long, LoginItemData>    loginStore    = new LinkedHashMap<>();
    private final Map<Long, NoteItemData>     noteStore     = new LinkedHashMap<>();
    private final Map<Long, IdentityItemData> identityStore = new LinkedHashMap<>();

    public MainController(VaultManager vaultManager, UnlockView unlockView, Composite topContainer,
            StackLayout topStack, Composite mainContent, ToolbarView toolbar, ItemListView listView,
            EmptyView emptyView, LoginDetailView loginDetailView, NoteDetailView noteDetailView,
            IdentityDetailView identityDetailView, LoginEditView loginEditView, NoteEditView noteEditView,
            IdentityEditView identityEditView, Composite rightContainer, StackLayout rightStack) {
        this.vaultManager = vaultManager;
        this.unlockView = unlockView;
        this.topContainer = topContainer;
        this.topStack = topStack;
        this.mainContent = mainContent;
        this.toolbar = toolbar;
        this.listView = listView;
        this.emptyView = emptyView;
        this.loginDetailView = loginDetailView;
        this.noteDetailView = noteDetailView;
        this.identityDetailView = identityDetailView;
        this.loginEditView = loginEditView;
        this.noteEditView = noteEditView;
        this.identityEditView = identityEditView;
        this.rightContainer = rightContainer;
        this.rightStack = rightStack;
    }

    public void init() {
        unlockView.setOnSubmit(this::onUnlockSubmit);
        topStack.topControl = unlockView.composite;
        topContainer.layout(true, true);

        toolbar.setOnAddNew(this::onAddNew);
        toolbar.setOnSearch(this::onSearch);
        toolbar.setOnLock(this::onLock);

        listView.setOnSelectionChanged(this::onSelectionChanged);
        listView.setOnCategoryChanged(this::onCategoryChanged);

        loginDetailView.setOnEdit(this::onEditCurrent);
        noteDetailView.setOnEdit(this::onEditCurrent);
        identityDetailView.setOnEdit(this::onEditCurrent);

        loginEditView.setOnSave(this::onSaveLogin);
        loginEditView.setOnCancel(this::onCancel);

        noteEditView.setOnSave(this::onSaveNote);
        noteEditView.setOnCancel(this::onCancel);

        identityEditView.setOnSave(this::onSaveIdentity);
        identityEditView.setOnCancel(this::onCancel);
    }

    // ── unlock ────────────────────────────────────────────────────────

    private void onUnlockSubmit(String password) {
        SecretKey dek = vaultManager.unlockVault(password.toCharArray());
        if (dek == null) {
            unlockView.showError("Incorrect password. Please try again.");
            return;
        }
        Session.current().setKey(dek);
        state.unlocked = true;
        unlockView.clearError();
        topStack.topControl = mainContent;
        topContainer.layout(true, true);

        loadItems();
        switchMode(Mode.EMPTY);
    }

    // ── content-layer event handlers ──────────────────────────────────

    private void onAddNew(int type) {
        listView.clearSelection();
        state.selectedItem = null;
        switch (type) {
        case ItemType.LOGIN -> {
            loginEditView.edit(null);
            activeEditComposite = loginEditView.composite;
        }
        case ItemType.NOTE -> {
            noteEditView.edit(null);
            activeEditComposite = noteEditView.composite;
        }
        case ItemType.IDENTITY -> {
            identityEditView.edit(null);
            activeEditComposite = identityEditView.composite;
        }
        default -> {
            // FIXME: popup error message dialog:
        }
        }
        switchMode(Mode.EDIT);
    }

    private void onLock() {
        Session.current().lock();
        state.unlocked = false;
        state.allItems.clear();
        state.selectedItem = null;
        loginStore.clear();
        noteStore.clear();
        identityStore.clear();
        topStack.topControl = unlockView.composite;
        topContainer.layout(true, true);
    }

    private void onSearch(String query) {
        String q = query == null ? "" : query.strip().toLowerCase();
        List<AbstractItemData> source = q.isEmpty() ? state.allItems
                : state.allItems.stream().filter(i -> contains(i.title(), q)).toList();
        listView.setAllItems(source);
    }

    private void onCategoryChanged(Category category) {
        if (state.mode == Mode.EDIT) {
            int r = askSaveDiscard();
            if (r == SWT.CANCEL)
                return;
            if (r == SWT.YES)
                triggerCurrentSave();
        }
        state.category = category;
        state.selectedItem = null;
        switchMode(Mode.EMPTY);
    }

    private void onSelectionChanged(AbstractItemData item) {
        if (state.mode == Mode.EDIT) {
            int r = askSaveDiscard();
            if (r == SWT.CANCEL)
                return;
            if (r == SWT.YES)
                triggerCurrentSave();
        }
        state.selectedItem = item;
        if (item == null) {
            switchMode(Mode.EMPTY);
            return;
        }
        switch (item.item_type) {
        case ItemType.LOGIN -> {
            LoginItemData d = loginStore.get(item.id);
            if (d != null)
                loginDetailView.show(d);
            activeDetailComposite = loginDetailView.composite;
        }
        case ItemType.NOTE -> {
            NoteItemData d = noteStore.get(item.id);
            if (d != null)
                noteDetailView.show(d);
            activeDetailComposite = noteDetailView.composite;
        }
        case ItemType.IDENTITY -> {
            IdentityItemData d = identityStore.get(item.id);
            if (d != null)
                identityDetailView.show(d);
            activeDetailComposite = identityDetailView.composite;
        }
        default -> {
            // FIXME: display error view
        }
        }
        switchMode(Mode.DETAIL);
    }

    private void onEditCurrent() {
        if (state.selectedItem == null)
            return;
        switch (state.selectedItem.item_type) {
        case ItemType.LOGIN -> {
            loginEditView.edit(loginStore.get(state.selectedItem.id));
            activeEditComposite = loginEditView.composite;
        }
        case ItemType.NOTE -> {
            noteEditView.edit(noteStore.get(state.selectedItem.id));
            activeEditComposite = noteEditView.composite;
        }
        case ItemType.IDENTITY -> {
            identityEditView.edit(identityStore.get(state.selectedItem.id));
            activeEditComposite = identityEditView.composite;
        }
        default -> throw new IllegalArgumentException("Unexpected value: " + state.selectedItem.item_type);
        }
        switchMode(Mode.EDIT);
    }

    private void onSaveLogin(LoginItemData data) {
        boolean isNew = data.id == 0;
        if (isNew)
            data.id = System.currentTimeMillis();
        loginStore.put(data.id, data);
        commitItem(data, isNew);
        loginDetailView.show(data);
        activeDetailComposite = loginDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onSaveNote(NoteItemData data) {
        boolean isNew = data.id == 0;
        if (isNew)
            data.id = System.currentTimeMillis();
        noteStore.put(data.id, data);
        commitItem(data, isNew);
        noteDetailView.show(data);
        activeDetailComposite = noteDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onSaveIdentity(IdentityItemData data) {
        boolean isNew = data.id == 0;
        if (isNew)
            data.id = System.currentTimeMillis();
        identityStore.put(data.id, data);
        commitItem(data, isNew);
        identityDetailView.show(data);
        activeDetailComposite = identityDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onCancel() {
        switchMode(state.selectedItem != null ? Mode.DETAIL : Mode.EMPTY);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void commitItem(AbstractItemData vaultItem, boolean isNew) {
        state.selectedItem = vaultItem;
        if (isNew) {
            state.allItems.add(vaultItem);
            listView.setAllItems(state.allItems);
            listView.selectItem(vaultItem.id);
        } else {
            state.allItems.replaceAll(i -> i.id == vaultItem.id ? vaultItem : i);
            listView.updateItem(vaultItem);
        }
    }

    private void loadItems() {
        SecretKey key = Session.current().getKey();
        if (key == null)
            return;
        List<AbstractItemData> items = vaultManager.getItems(key);
        state.allItems.clear();
        loginStore.clear();
        noteStore.clear();
        identityStore.clear();
        for (AbstractItemData item : items) {
            state.allItems.add(item);
            switch (item) {
            case LoginItemData d -> loginStore.put(d.id, d);
            case NoteItemData d -> noteStore.put(d.id, d);
            case IdentityItemData d -> identityStore.put(d.id, d);
            default -> {
            }
            }
        }
        listView.setAllItems(state.allItems);
    }

    /** Returns SWT.YES (save), SWT.NO (discard), or SWT.CANCEL. */
    private int askSaveDiscard() {
        Shell parent = topContainer.getShell();
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText("Unsaved Changes");

        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 20;
        gl.marginHeight = 20;
        gl.verticalSpacing = 16;
        dialog.setLayout(gl);

        Label msg = new Label(dialog, SWT.NONE);
        msg.setText("You have unsaved changes.");

        Composite btnRow = new Composite(dialog, SWT.NONE);
        btnRow.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.spacing = 8;
        btnRow.setLayout(rl);

        int[] result = { SWT.CANCEL };

        Button btnSave = new Button(btnRow, SWT.PUSH);
        btnSave.setText(" Save ");
        btnSave.addListener(SWT.Selection, _ -> {
            result[0] = SWT.YES;
            dialog.close();
        });

        Button btnDiscard = new Button(btnRow, SWT.PUSH);
        btnDiscard.setText(" Discard ");
        btnDiscard.addListener(SWT.Selection, _ -> {
            result[0] = SWT.NO;
            dialog.close();
        });

        Button btnCancel = new Button(btnRow, SWT.PUSH);
        btnCancel.setText(" Cancel ");
        btnCancel.addListener(SWT.Selection, _ -> {
            result[0] = SWT.CANCEL;
            dialog.close();
        });

        dialog.setDefaultButton(btnSave);
        dialog.pack();

        Rectangle pb = parent.getBounds();
        Point ps = dialog.getSize();
        dialog.setLocation(pb.x + (pb.width - ps.x) / 2, pb.y + (pb.height - ps.y) / 2);

        dialog.open();
        Display display = parent.getDisplay();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        return result[0];
    }

    private void triggerCurrentSave() {
        if (activeEditComposite == loginEditView.composite)
            loginEditView.triggerSave();
        else if (activeEditComposite == noteEditView.composite)
            noteEditView.triggerSave();
        else if (activeEditComposite == identityEditView.composite)
            identityEditView.triggerSave();
    }

    private void switchMode(Mode mode) {
        state.mode = mode;
        rightStack.topControl = switch (mode) {
        case EMPTY -> emptyView.composite;
        case DETAIL -> activeDetailComposite;
        case EDIT -> activeEditComposite;
        };
        rightContainer.layout(true, true);
    }

    private boolean contains(String text, String query) {
        return text != null && text.toLowerCase().contains(query);
    }

    private String notNull(String s) {
        return s != null ? s : "";
    }
}
