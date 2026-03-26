package org.puppylab.mypassword.ui.view;

import java.util.function.Consumer;

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

public class NoteEditView {

    public final Composite composite;

    private final ScrolledComposite sc;
    private final Composite         content;
    private final Text titleField;
    private final Text contentField;

    private Consumer<NoteItemData> onSave;
    private Runnable               onCancel;

    private long editingId = 0;

    public NoteEditView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        RowLayout rl = new RowLayout();
        rl.spacing = 10;
        actions.setLayout(rl);

        Button btnSave = new Button(actions, SWT.PUSH);
        btnSave.setText(" Save ");
        btnSave.addListener(SWT.Selection, e -> { if (onSave != null) onSave.accept(collectData()); });

        Button btnCancel = new Button(actions, SWT.PUSH);
        btnCancel.setText(" Cancel ");
        btnCancel.addListener(SWT.Selection, e -> { if (onCancel != null) onCancel.run(); });

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

        titleField = createField(content, "Title:");

        Label lbl = new Label(content, SWT.NONE);
        lbl.setText("Content:");
        lbl.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        contentField = new Text(content, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData td = new GridData(SWT.FILL, SWT.TOP, true, false);
        td.heightHint = 200;
        contentField.setLayoutData(td);

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            content.layout(true, true);
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    /** Populate form for editing an existing note, or pass null for a new note. */
    public void edit(NoteItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            contentField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(notNull(item.title));
            contentField.setText(notNull(item.content));
        }
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    public void setOnSave(Consumer<NoteItemData> listener)   { this.onSave   = listener; }
    public void setOnCancel(Runnable listener)                { this.onCancel = listener; }

    private NoteItemData collectData() {
        NoteItemData data = new NoteItemData();
        data.id      = editingId;
        data.title   = titleField.getText().strip();
        data.content = contentField.getText();
        return data;
    }

    private Text createField(Composite parent, String labelText) {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Text t = new Text(parent, SWT.BORDER);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return t;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
