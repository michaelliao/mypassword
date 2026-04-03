package org.puppylab.mypassword.core.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "item_type", visible = true)
@JsonSubTypes({
        // subclass type:
        @JsonSubTypes.Type(value = LoginItemData.class, name = "" + ItemType.LOGIN),
        @JsonSubTypes.Type(value = NoteItemData.class, name = "" + ItemType.NOTE),
        @JsonSubTypes.Type(value = IdentityItemData.class, name = "" + ItemType.IDENTITY), })
public abstract class AbstractItemData {

    public long    id;
    public boolean favorite;
    public boolean deleted;
    public long    updated_at;
    public int     item_type;

    public abstract AbstractFields fields();

    public abstract String title();

    public abstract String subtitle();
}
