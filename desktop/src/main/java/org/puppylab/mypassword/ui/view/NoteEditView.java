package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.NoteItemData;

public class NoteEditView extends AbstractEditView<NoteItemData> {

    private Text titleField;
    private Text contentField;

    private long editingId = 0;

    public NoteEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField(content, "Title:", SWT.BORDER);
        contentField = createAreaField(content, "Content:");
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    @Override
    protected void doEdit(NoteItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            contentField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(notNull(item.title));
            contentField.setText(notNull(item.content));
        }
    }

    @Override
    protected NoteItemData collectData() {
        NoteItemData data = new NoteItemData();
        data.id = editingId;
        data.title = titleField.getText().strip();
        data.content = contentField.getText();
        return data;
    }

    private String notNull(String s) {
        return s != null ? s : "";
    }
}
