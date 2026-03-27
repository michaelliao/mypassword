package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.NoteFieldsData;
import org.puppylab.mypassword.rpc.data.NoteItemData;
import org.puppylab.mypassword.rpc.util.StringUtils;

public class NoteEditView extends AbstractEditView<NoteItemData> {

    private Text titleField;
    private Text contentField;

    private long editingId = 0;

    public NoteEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField("Title:", SWT.BORDER);
        contentField = createAreaField("Content:");
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    @Override
    protected void setData(NoteItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            contentField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.data.title));
            contentField.setText(StringUtils.normalize(item.data.content));
        }
    }

    @Override
    protected NoteItemData collectData() {
        NoteItemData data = new NoteItemData();
        data.id = editingId;
        data.data = new NoteFieldsData();
        data.data.title = titleField.getText().strip();
        data.data.content = contentField.getText();
        return data;
    }
}
