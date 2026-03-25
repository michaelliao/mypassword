package org.puppylab.mypassword.ui.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Composite;
import org.puppylab.mypassword.rpc.data.IdentityItemData;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.rpc.data.NoteItemData;
import org.puppylab.mypassword.ui.model.AppState;
import org.puppylab.mypassword.ui.model.AppState.Mode;
import org.puppylab.mypassword.ui.model.Category;
import org.puppylab.mypassword.ui.model.ItemType;
import org.puppylab.mypassword.ui.model.VaultItem;
import org.puppylab.mypassword.ui.view.DetailView;
import org.puppylab.mypassword.ui.view.EditView;
import org.puppylab.mypassword.ui.view.EmptyView;
import org.puppylab.mypassword.ui.view.ItemListView;
import org.puppylab.mypassword.ui.view.ToolbarView;
import org.puppylab.mypassword.ui.view.UnlockView;

public class MainController {

    private static final String DUMMY_PASSWORD = "password";

    private final AppState state = new AppState();

    // ── unlock layer ──────────────────────────────────────────────────
    private final UnlockView  unlockView;
    private final Composite   topContainer;
    private final StackLayout topStack;
    private final Composite   mainContent;

    // ── content layer ─────────────────────────────────────────────────
    private final ToolbarView  toolbar;
    private final ItemListView listView;
    private final EmptyView    emptyView;
    private final DetailView   detailView;
    private final EditView     editView;
    private final Composite    rightContainer;
    private final StackLayout  rightStack;

    // ── per-type in-memory stores (keyed by id) ───────────────────────
    private final Map<Long, LoginItemData>    loginStore    = new LinkedHashMap<>();
    private final Map<Long, NoteItemData>     noteStore     = new LinkedHashMap<>();
    private final Map<Long, IdentityItemData> identityStore = new LinkedHashMap<>();

    public MainController(
            UnlockView  unlockView,
            Composite   topContainer,
            StackLayout topStack,
            Composite   mainContent,
            ToolbarView  toolbar,
            ItemListView listView,
            EmptyView    emptyView,
            DetailView   detailView,
            EditView     editView,
            Composite    rightContainer,
            StackLayout  rightStack) {
        this.unlockView     = unlockView;
        this.topContainer   = topContainer;
        this.topStack       = topStack;
        this.mainContent    = mainContent;
        this.toolbar        = toolbar;
        this.listView       = listView;
        this.emptyView      = emptyView;
        this.detailView     = detailView;
        this.editView       = editView;
        this.rightContainer = rightContainer;
        this.rightStack     = rightStack;
    }

    public void init() {
        // wire unlock view first; vault content is loaded only after unlock
        unlockView.setOnSubmit(this::onUnlockSubmit);
        topStack.topControl = unlockView.composite;
        topContainer.layout();

        // wire content-layer listeners (safe to do before unlock)
        toolbar.setOnAddNew(this::onAddNew);
        toolbar.setOnSearch(this::onSearch);

        listView.setOnSelectionChanged(this::onSelectionChanged);
        listView.setOnCategoryChanged(this::onCategoryChanged);

        detailView.setOnEdit(this::onEditCurrent);

        editView.setOnSave(this::onSave);
        editView.setOnCancel(this::onCancel);
    }

    // ── unlock ────────────────────────────────────────────────────────

    private void onUnlockSubmit(String password) {
        if (!DUMMY_PASSWORD.equals(password)) {
            unlockView.showError("Incorrect password. Please try again.");
            return;
        }
        state.unlocked = true;
        unlockView.clearError();
        topStack.topControl = mainContent;
        topContainer.layout();

        // load vault content now that the vault is "open"
        loadItems();
        switchMode(Mode.EMPTY);
    }

    // ── content-layer event handlers ──────────────────────────────────

    private void onAddNew() {
        listView.clearSelection();
        state.selectedItem = null;
        editView.edit(null);
        switchMode(Mode.EDIT);
    }

    private void onSearch(String query) {
        String q = query == null ? "" : query.strip().toLowerCase();
        List<VaultItem> source = q.isEmpty() ? state.allItems
                : state.allItems.stream()
                        .filter(i -> contains(i.title(), q) || contains(i.subtitle(), q))
                        .toList();
        listView.setAllItems(source);
    }

    private void onCategoryChanged(Category category) {
        state.category     = category;
        state.selectedItem = null;
        switchMode(Mode.EMPTY);
    }

    private void onSelectionChanged(VaultItem item) {
        state.selectedItem = item;
        if (item == null) {
            switchMode(Mode.EMPTY);
            return;
        }
        switch (item.type()) {
            case LOGIN -> { LoginItemData d = loginStore.get(item.id()); if (d != null) detailView.show(d); }
            case NOTE, IDENTITY -> { /* TODO: detail views for note / identity */ }
        }
        switchMode(Mode.DETAIL);
    }

    private void onEditCurrent() {
        if (state.selectedItem == null) return;
        if (state.selectedItem.type() == ItemType.LOGIN) {
            editView.edit(loginStore.get(state.selectedItem.id()));
        }
        switchMode(Mode.EDIT);
    }

    private void onSave(LoginItemData data) {
        boolean isNew = data.id == 0;
        if (isNew) {
            data.id = System.currentTimeMillis(); // TODO: replace with daemon-assigned id
        }
        // TODO: call daemon API to create / update
        loginStore.put(data.id, data);

        VaultItem vaultItem = toVaultItem(data);
        if (isNew) {
            state.allItems.add(vaultItem);
        } else {
            state.allItems.replaceAll(i -> i.id() == data.id ? vaultItem : i);
        }
        state.selectedItem = vaultItem;
        listView.setAllItems(state.allItems);
        detailView.show(data);
        switchMode(Mode.DETAIL);
    }

    private void onCancel() {
        switchMode(state.selectedItem != null ? Mode.DETAIL : Mode.EMPTY);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void loadItems() {
        // TODO: replace with daemon API calls
        addLogin(1,  "Google",          "michael@gmail.com",      true,  false);
        addLogin(2,  "GitHub",          "michael-liao",           false, false);
        addLogin(3,  "Amazon",          "michael@gmail.com",      false, false);
        addLogin(4,  "Netflix",         "michael@gmail.com",      false, false);
        addLogin(5,  "Twitter / X",     "michael_liao",           false, true);
        addLogin(6,  "LinkedIn",        "michael.liao@work.com",  true,  false);
        addLogin(7,  "Dropbox",         "michael@gmail.com",      false, false);
        addLogin(8,  "Apple ID",        "michael@icloud.com",     false, false);
        addLogin(9,  "Microsoft",       "michael@outlook.com",    false, false);
        addLogin(10, "Steam",           "michael_games",          false, true);
        addLogin(11, "Spotify",         "michael@gmail.com",      true,  false);
        addLogin(12, "PayPal",          "michael@gmail.com",      false, false);
        addLogin(13, "Slack",           "michael.liao@work.com",  false, false);
        addLogin(14, "Notion",          "michael.liao@work.com",  false, false);
        addLogin(15, "Adobe",           "michael@gmail.com",      false, true);
        addLogin(16, "1Password",       "michael@gmail.com",      true,  false);
        addLogin(17, "Figma",           "michael.liao@work.com",  false, false);
        addLogin(18, "Cloudflare",      "michael@gmail.com",      false, false);
        addLogin(19, "Digital Ocean",   "michael@gmail.com",      false, false);
        addLogin(20, "Old Forum",       "michael1990",            false, true);

        addNote(21, "Wi-Fi Password",         "Router: TP-Link AX3000...",     true,  false);
        addNote(22, "Server SSH Keys",        "prod-01: ssh michael@...",       false, false);
        addNote(23, "Recovery Codes — Gmail", "1. 4829-3810\n2. 9271-...",      false, true);
        addNote(24, "API Keys",               "Stripe live key: sk_live_...",   false, false);
        addNote(25, "Software Licenses",      "JetBrains: ABCD-EFGH-...",       false, false);
        addNote(26, "Home Alarm Code",        "Front door: 4 digits...",        true,  false);
        addNote(27, "Bank Account Details",   "IBAN: GB29 NWBK...",             false, false);
        addNote(28, "Travel SIM PINs",        "UK SIM PIN: 1234...",            false, true);
        addNote(29, "Emergency Contacts",     "Police: 110, Fire: 119...",      false, false);
        addNote(30, "Old Passwords Archive",  "pre-2020 list...",               false, false);

        addIdentity(31, "Personal Passport", "Michael Liao", "E12345678", true,  false);
        addIdentity(32, "Work ID",           "Michael Liao", "",          false, false);
        addIdentity(33, "Old Passport",      "Michael Liao", "D98765432", false, true);
        addIdentity(34, "Driver License",    "Michael Liao", "",          false, false);
        addIdentity(35, "National ID",       "Michael Liao", "",          false, false);

        listView.setAllItems(state.allItems);
    }

    private void addLogin(long id, String title, String username, boolean fav, boolean del) {
        LoginItemData d = new LoginItemData();
        d.id = id; d.title = title; d.username = username;
        loginStore.put(id, d);
        state.allItems.add(new VaultItem(id, ItemType.LOGIN, title, username, fav, del));
    }

    private void addNote(long id, String title, String content, boolean fav, boolean del) {
        NoteItemData d = new NoteItemData();
        d.id = id; d.title = title; d.content = content;
        noteStore.put(id, d);
        state.allItems.add(new VaultItem(id, ItemType.NOTE, title, "", fav, del));
    }

    private void addIdentity(long id, String title, String name, String passport,
            boolean fav, boolean del) {
        IdentityItemData d = new IdentityItemData();
        d.id = id; d.title = title; d.name = name; d.passport_number = passport;
        identityStore.put(id, d);
        state.allItems.add(new VaultItem(id, ItemType.IDENTITY, title, name, fav, del));
    }

    private VaultItem toVaultItem(LoginItemData d) {
        return new VaultItem(d.id, ItemType.LOGIN, d.title,
                d.username != null ? d.username : "", false, false);
    }

    private void switchMode(Mode mode) {
        state.mode = mode;
        rightStack.topControl = switch (mode) {
            case EMPTY  -> emptyView.composite;
            case DETAIL -> detailView.composite;
            case EDIT   -> editView.composite;
        };
        rightContainer.layout();
    }

    private boolean contains(String text, String query) {
        return text != null && text.toLowerCase().contains(query);
    }
}
