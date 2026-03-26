package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.layout.FillLayout;
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

    private final ScrolledComposite sc;
    private final Composite         content;
    private final Label titleValue;
    private final Text  contentValue;

    private Runnable onEdit;

    public NoteDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> { if (onEdit != null) onEdit.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setLayout(new FillLayout(SWT.VERTICAL));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new GridLayout(2, false));

        titleValue = createLabelField(content, "Title:");

        Label lbl = new Label(content, SWT.NONE);
        lbl.setText("Content:");
        lbl.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        contentValue = new Text(content, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData td = new GridData(SWT.FILL, SWT.TOP, true, false);
        td.heightHint = 200;
        contentValue.setLayoutData(td);

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            content.layout(true, true);
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    public void show(NoteItemData item) {
        titleValue.setText(notNull(item.title));
        contentValue.setText(notNull(item.content));
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    public void setOnEdit(Runnable listener) { this.onEdit = listener; }

    private Label createLabelField(Composite parent, String labelText) {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Label value = new Label(parent, SWT.WRAP);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
