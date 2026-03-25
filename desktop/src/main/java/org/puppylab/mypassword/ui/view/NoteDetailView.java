package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.NoteItemData;

public class NoteDetailView {

    public final Composite composite;

    private final Label titleValue;
    private final Text  contentValue;

    private Runnable onEdit;

    public NoteDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> { if (onEdit != null) onEdit.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        titleValue = createLabelField(composite, "Title:");

        // content label row
        Composite contentRow = new Composite(composite, SWT.NONE);
        contentRow.setLayout(new GridLayout(2, false));
        contentRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label lbl = new Label(contentRow, SWT.NONE);
        lbl.setText("Content:");
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        contentValue = new Text(contentRow, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        contentValue.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    public void show(NoteItemData item) {
        titleValue.setText(notNull(item.title));
        contentValue.setText(notNull(item.content));
        composite.layout(true, true);
    }

    public void setOnEdit(Runnable listener) { this.onEdit = listener; }

    private Label createLabelField(Composite parent, String labelText) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Label value = new Label(row, SWT.NONE);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
