package org.puppylab.mypassword.util;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.EncryptUtils;
import org.puppylab.mypassword.core.data.AbstractFields;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.core.entity.Item;

public class ConvertUtils {

    /**
     * Convert db entity Item to LoginItemData, NoteItemData or IdentityItemData by
     * item_type.
     */
    public static AbstractItemData toItemData(SecretKey key, Item item) {
        byte[] d_data = decryptItemData(key, item);
        AbstractItemData itemData = switch (item.item_type) {
        case ItemType.LOGIN -> {
            var data = new LoginItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, LoginFieldsData.class);
            yield data;
        }
        case ItemType.NOTE -> {
            var data = new NoteItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, NoteFieldsData.class);
            yield data;
        }
        case ItemType.IDENTITY -> {
            var data = new IdentityItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, IdentityFieldsData.class);
            yield data;
        }
        default -> {
            throw new IllegalArgumentException("Invalid item type: " + item.item_type);
        }
        };
        // copy plain fields:
        itemData.id = item.id;
        itemData.item_type = item.item_type;
        itemData.deleted = item.deleted;
        itemData.favorite = item.favorite;
        itemData.updated_at = item.updated_at;
        return itemData;
    }

    public static void encrypt(SecretKey key, Item item, AbstractFields fields) {
        String jsonData = JsonUtils.toJson(fields);
        byte[] data = jsonData.getBytes(StandardCharsets.UTF_8);
        byte[] iv = EncryptUtils.generateIV();
        byte[] encrypted = EncryptUtils.encrypt(data, key, iv);
        item.b64_encrypted_data = Base64Utils.b64(encrypted);
        item.b64_encrypted_data_iv = Base64Utils.b64(iv);
    }

    public static byte[] decryptItemData(SecretKey key, Item item) {
        byte[] encrypted = Base64Utils.b64(item.b64_encrypted_data);
        byte[] iv = Base64Utils.b64(item.b64_encrypted_data_iv);
        return EncryptUtils.decrypt(encrypted, key, iv);
    }

}
