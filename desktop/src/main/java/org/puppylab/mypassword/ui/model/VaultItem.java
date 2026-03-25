package org.puppylab.mypassword.ui.model;

/**
 * Display-only projection of any vault item, used by ItemListView.
 * The controller builds this from the typed data objects (LoginItemData, etc.).
 */
public record VaultItem(
        long     id,
        ItemType type,
        String   title,
        String   subtitle,   // username for logins, empty for notes/identities
        boolean  favorite,
        boolean  deleted) {
}
