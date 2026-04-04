package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.util.StringUtils;

public class NoteEditView extends AbstractEditView<NoteItemData> {

    private Text titleField;
    private Text contentField;

    private NoteItemData editingItem = null;

    public NoteEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField(i18n("field.title"), SWT.BORDER);
        contentField = createAreaField(i18n("field.content"));
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    @Override
    protected void setData(NoteItemData item) {
        editingItem = item;
        if (item == null) {
            titleField.setText("");
            contentField.setText("");
        } else {
            titleField.setText(StringUtils.normalize(item.data.title));
            contentField.setText(StringUtils.normalize(item.data.content));
        }
    }

    @Override
    protected NoteItemData collectData() {
        NoteItemData data = editingItem != null ? editingItem : new NoteItemData();
        data.item_type = ItemType.NOTE;
        data.data = new NoteFieldsData();
        data.data.title = titleField.getText().strip();
        data.data.content = contentField.getText();
        return data;
    }
}
