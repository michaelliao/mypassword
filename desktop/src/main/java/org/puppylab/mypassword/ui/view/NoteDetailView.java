package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.util.StringUtils;

public class NoteDetailView extends AbstractDetailView<NoteItemData> {

    private Label titleValue;
    private Label contentValue;

    public NoteDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleValue = createField(i18n("field.title"));
        contentValue = createField(i18n("field.content"));
    }

    @Override
    protected void setData(NoteItemData item) {
        titleValue.setText(StringUtils.normalize(item.data.title));
        contentValue.setText(StringUtils.normalize(item.data.content));
    }
}
