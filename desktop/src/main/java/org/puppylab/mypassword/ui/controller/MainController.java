package org.puppylab.mypassword.ui.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Composite;
import org.puppylab.mypassword.rpc.data.IdentityItemData;
import org.puppylab.mypassword.rpc.data.ItemType;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.rpc.data.NoteItemData;
import org.puppylab.mypassword.ui.model.AppState;
import org.puppylab.mypassword.ui.model.AppState.Mode;
import org.puppylab.mypassword.ui.model.Category;
import org.puppylab.mypassword.ui.model.VaultItem;
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

    private static final String DUMMY_PASSWORD = "password";

    private final AppState state = new AppState();

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

    public MainController(
            UnlockView       unlockView,
            Composite        topContainer,
            StackLayout      topStack,
            Composite        mainContent,
            ToolbarView      toolbar,
            ItemListView     listView,
            EmptyView        emptyView,
            LoginDetailView  loginDetailView,
            NoteDetailView   noteDetailView,
            IdentityDetailView identityDetailView,
            LoginEditView    loginEditView,
            NoteEditView     noteEditView,
            IdentityEditView identityEditView,
            Composite        rightContainer,
            StackLayout      rightStack) {
        this.unlockView         = unlockView;
        this.topContainer       = topContainer;
        this.topStack           = topStack;
        this.mainContent        = mainContent;
        this.toolbar            = toolbar;
        this.listView           = listView;
        this.emptyView          = emptyView;
        this.loginDetailView    = loginDetailView;
        this.noteDetailView     = noteDetailView;
        this.identityDetailView = identityDetailView;
        this.loginEditView      = loginEditView;
        this.noteEditView       = noteEditView;
        this.identityEditView   = identityEditView;
        this.rightContainer     = rightContainer;
        this.rightStack         = rightStack;
    }

    public void init() {
        unlockView.setOnSubmit(this::onUnlockSubmit);
        topStack.topControl = unlockView.composite;
        topContainer.layout();

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
        if (!DUMMY_PASSWORD.equals(password)) {
            unlockView.showError("Incorrect password. Please try again.");
            return;
        }
        state.unlocked = true;
        unlockView.clearError();
        topStack.topControl = mainContent;
        topContainer.layout();

        loadItems();
        switchMode(Mode.EMPTY);
    }

    // ── content-layer event handlers ──────────────────────────────────

    private void onAddNew(ItemType type) {
        listView.clearSelection();
        state.selectedItem = null;
        switch (type) {
            case LOGIN    -> { loginEditView.edit(null);    activeEditComposite = loginEditView.composite; }
            case NOTE     -> { noteEditView.edit(null);     activeEditComposite = noteEditView.composite; }
            case IDENTITY -> { identityEditView.edit(null); activeEditComposite = identityEditView.composite; }
        }
        switchMode(Mode.EDIT);
    }

    private void onLock() {
        state.unlocked = false;
        state.allItems.clear();
        state.selectedItem = null;
        loginStore.clear();
        noteStore.clear();
        identityStore.clear();
        topStack.topControl = unlockView.composite;
        topContainer.layout();
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
            case LOGIN -> {
                LoginItemData d = loginStore.get(item.id());
                if (d != null) loginDetailView.show(d);
                activeDetailComposite = loginDetailView.composite;
            }
            case NOTE -> {
                NoteItemData d = noteStore.get(item.id());
                if (d != null) noteDetailView.show(d);
                activeDetailComposite = noteDetailView.composite;
            }
            case IDENTITY -> {
                IdentityItemData d = identityStore.get(item.id());
                if (d != null) identityDetailView.show(d);
                activeDetailComposite = identityDetailView.composite;
            }
        }
        switchMode(Mode.DETAIL);
    }

    private void onEditCurrent() {
        if (state.selectedItem == null) return;
        switch (state.selectedItem.type()) {
            case LOGIN -> {
                loginEditView.edit(loginStore.get(state.selectedItem.id()));
                activeEditComposite = loginEditView.composite;
            }
            case NOTE -> {
                noteEditView.edit(noteStore.get(state.selectedItem.id()));
                activeEditComposite = noteEditView.composite;
            }
            case IDENTITY -> {
                identityEditView.edit(identityStore.get(state.selectedItem.id()));
                activeEditComposite = identityEditView.composite;
            }
        }
        switchMode(Mode.EDIT);
    }

    private void onSaveLogin(LoginItemData data) {
        boolean isNew = data.id == 0;
        if (isNew) data.id = System.currentTimeMillis();
        loginStore.put(data.id, data);
        VaultItem vaultItem = new VaultItem(data.id, ItemType.LOGIN,
                data.title, notNull(data.username), false, false);
        commitItem(vaultItem, isNew);
        loginDetailView.show(data);
        activeDetailComposite = loginDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onSaveNote(NoteItemData data) {
        boolean isNew = data.id == 0;
        if (isNew) data.id = System.currentTimeMillis();
        noteStore.put(data.id, data);
        VaultItem vaultItem = new VaultItem(data.id, ItemType.NOTE,
                data.title, "", false, false);
        commitItem(vaultItem, isNew);
        noteDetailView.show(data);
        activeDetailComposite = noteDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onSaveIdentity(IdentityItemData data) {
        boolean isNew = data.id == 0;
        if (isNew) data.id = System.currentTimeMillis();
        identityStore.put(data.id, data);
        VaultItem vaultItem = new VaultItem(data.id, ItemType.IDENTITY,
                data.title, notNull(data.name), false, false);
        commitItem(vaultItem, isNew);
        identityDetailView.show(data);
        activeDetailComposite = identityDetailView.composite;
        switchMode(Mode.DETAIL);
    }

    private void onCancel() {
        switchMode(state.selectedItem != null ? Mode.DETAIL : Mode.EMPTY);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void commitItem(VaultItem vaultItem, boolean isNew) {
        if (isNew) {
            state.allItems.add(vaultItem);
        } else {
            state.allItems.replaceAll(i -> i.id() == vaultItem.id() ? vaultItem : i);
        }
        state.selectedItem = vaultItem;
        listView.setAllItems(state.allItems);
    }

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

    private void switchMode(Mode mode) {
        state.mode = mode;
        rightStack.topControl = switch (mode) {
            case EMPTY  -> emptyView.composite;
            case DETAIL -> activeDetailComposite;
            case EDIT   -> activeEditComposite;
        };
        rightContainer.layout();
    }

    private boolean contains(String text, String query) {
        return text != null && text.toLowerCase().contains(query);
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
