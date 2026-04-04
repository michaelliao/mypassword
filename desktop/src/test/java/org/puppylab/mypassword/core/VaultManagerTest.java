package org.puppylab.mypassword.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.rpc.BadRequestException;
import org.puppylab.mypassword.rpc.ErrorCode;

public class VaultManagerTest {

    Path         dbFile;
    VaultManager vaultManager;
    SecretKey    key;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = File.createTempFile("test-mypassword-", ".db").toPath();
        Files.delete(dbFile); // delete db file to force init schema
        this.vaultManager = new VaultManager(new DbManager(dbFile));
        this.key = this.vaultManager.initVault("password");
    }

    @AfterEach
    void tearDown() throws Exception {
        vaultManager.close();
        Files.deleteIfExists(dbFile);
    }

    // --- vault lifecycle ---

    @Test
    void testIsInitialized() {
        assertTrue(vaultManager.isInitialized());
    }

    @Test
    void testInitVaultTwiceThrows() {
        assertThrows(IllegalStateException.class, () -> vaultManager.initVault("password"));
    }

    @Test
    void testUnlockVaultWithCorrectPassword() {
        SecretKey unlocked = vaultManager.unlockVault("password".toCharArray());
        assertNotNull(unlocked);
        assertArrayEquals(key.getEncoded(), unlocked.getEncoded());
    }

    @Test
    void testUnlockVaultWithWrongPassword() {
        SecretKey unlocked = vaultManager.unlockVault("BadPassword".toCharArray());
        assertNull(unlocked);
    }

    // --- create & get ---

    @Test
    void testCreateAndGetLoginItem() {
        LoginItemData login = newLogin("GitHub", "alice", "s3cret");
        AbstractItemData created = vaultManager.createItem(key, login);

        assertInstanceOf(LoginItemData.class, created);
        LoginItemData result = (LoginItemData) created;
        assertTrue(result.id != 0);
        assertEquals("GitHub", result.data.title);
        assertEquals("alice", result.data.username);
        assertEquals("s3cret", result.data.password);
        assertEquals(ItemType.LOGIN, result.item_type);
        assertFalse(result.deleted);
        assertFalse(result.favorite);

        // verify getItem returns same data:
        AbstractItemData fetched = vaultManager.getItem(key, result.id);
        assertEquals(result.id, fetched.id);
    }

    @Test
    void testCreateAndGetNoteItem() {
        AbstractItemData created = vaultManager.createItem(key, newNote("My Note", "some content"));

        assertInstanceOf(NoteItemData.class, created);
        NoteItemData result = (NoteItemData) created;
        assertEquals("My Note", result.data.title);
        assertEquals("some content", result.data.content);
    }

    @Test
    void testGetItems() {
        vaultManager.createItem(key, newLogin("Site1", "u1", "p1"));
        vaultManager.createItem(key, newLogin("Site2", "u2", "p2"));
        vaultManager.createItem(key, newNote("Note1", "content"));

        List<AbstractItemData> items = vaultManager.getItems(key);
        assertEquals(3, items.size());
    }

    @Test
    void testGetItemNotFound() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.getItem(key, 999L));
        assertEquals(ErrorCode.DATA_NOT_FOUND, ex.errorCode);
    }

    // --- create validation ---

    @Test
    void testCreateItemNullFields() {
        LoginItemData login = new LoginItemData();
        login.item_type = ItemType.LOGIN;
        login.data = null;

        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.createItem(key, login));
        assertEquals(ErrorCode.BAD_FIELD, ex.errorCode);
    }

    @Test
    void testCreateItemBlankTitle() {
        LoginItemData login = newLogin("", "user", "pass");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.createItem(key, login));
        assertEquals(ErrorCode.BAD_FIELD, ex.errorCode);
    }

    // --- update ---

    @Test
    void testUpdateItem() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Old Title", "alice", "pass"));

        LoginItemData updated = newLogin("New Title", "bob", "newpass");
        updated.id = created.id;
        updated.item_type = ItemType.LOGIN;
        AbstractItemData result = vaultManager.updateItem(key, updated);

        assertInstanceOf(LoginItemData.class, result);
        LoginItemData loginResult = (LoginItemData) result;
        assertEquals("New Title", loginResult.data.title);
        assertEquals("bob", loginResult.data.username);
        assertEquals(created.id, loginResult.id);
    }

    @Test
    void testUpdateItemNotFound() {
        LoginItemData login = newLogin("Title", "user", "pass");
        login.id = 999L;
        login.item_type = ItemType.LOGIN;

        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.updateItem(key, login));
        assertEquals(ErrorCode.DATA_NOT_FOUND, ex.errorCode);
    }

    @Test
    void testUpdateItemTypeMismatch() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Title", "user", "pass"));

        NoteItemData note = newNote("Title", "content");
        note.id = created.id;
        note.item_type = ItemType.NOTE;

        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.updateItem(key, note));
        assertEquals(ErrorCode.BAD_FIELD, ex.errorCode);
    }

    // --- delete & restore ---

    @Test
    void testDeleteItem() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Title", "user", "pass"));
        AbstractItemData deleted = vaultManager.deleteItem(key, created.id);

        assertTrue(deleted.deleted);
        assertEquals(created.id, deleted.id);
    }

    @Test
    void testDeleteItemIdempotent() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Title", "user", "pass"));
        vaultManager.deleteItem(key, created.id);
        AbstractItemData deleted2 = vaultManager.deleteItem(key, created.id); // should not throw
        assertTrue(deleted2.deleted);
    }

    @Test
    void testDeleteItemNotFound() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.deleteItem(key, 999L));
        assertEquals(ErrorCode.DATA_NOT_FOUND, ex.errorCode);
    }

    @Test
    void testRestoreItem() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Title", "user", "pass"));
        vaultManager.deleteItem(key, created.id);
        AbstractItemData restored = vaultManager.restoreItem(key, created.id);

        assertFalse(restored.deleted);
        assertEquals(created.id, restored.id);
    }

    @Test
    void testRestoreItemIdempotent() {
        AbstractItemData created = vaultManager.createItem(key, newLogin("Title", "user", "pass"));
        AbstractItemData restored = vaultManager.restoreItem(key, created.id); // not deleted, should not throw
        assertFalse(restored.deleted);
    }

    @Test
    void testRestoreItemNotFound() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> vaultManager.restoreItem(key, 999L));
        assertEquals(ErrorCode.DATA_NOT_FOUND, ex.errorCode);
    }

    // --- helpers ---

    static LoginItemData newLogin(String title, String username, String password) {
        LoginItemData item = new LoginItemData();
        item.item_type = ItemType.LOGIN;
        LoginFieldsData fields = new LoginFieldsData();
        fields.title = title;
        fields.username = username;
        fields.password = password;
        item.data = fields;
        return item;
    }

    static NoteItemData newNote(String title, String content) {
        NoteItemData item = new NoteItemData();
        item.item_type = ItemType.NOTE;
        NoteFieldsData fields = new NoteFieldsData();
        fields.title = title;
        fields.content = content;
        item.data = fields;
        return item;
    }
}
