package org.puppylab.mypassword.ui.view;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public abstract class AbstractDetailView<T> {

    public final Composite composite;

    final ScrolledComposite sc;
    final Composite         content;

    private Runnable onEdit;

    public AbstractDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> {
            if (onEdit != null) {
                onEdit.run();
            }
        });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        sc.setLayout(new FillLayout(SWT.VERTICAL));

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new GridLayout(1, false));

        createFields();

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    protected abstract void createFields();

    public void show(T item) {
        setData(item);
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    protected abstract void setData(T item);

    public void setOnEdit(Runnable listener) {
        this.onEdit = listener;
    }

    protected Label createField(String labelText) {
        Composite parent = this.content;
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Label value = new Label(row, SWT.WRAP);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    /**
     * Creates a 2-column row whose right cell is a container for multiple value
     * labels. Pass the returned composite to {@link #setMultiValueField}.
     */
    protected Composite createMultiValueField(String labelText) {
        Composite row = new Composite(content, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Composite valuesContainer = new Composite(row, SWT.NONE);
        GridLayout vl = new GridLayout(1, false);
        vl.marginHeight = 0;
        vl.marginWidth = 0;
        vl.verticalSpacing = 2;
        valuesContainer.setLayout(vl);
        valuesContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return valuesContainer;
    }

    /** Replace the labels inside a multi-value container created by {@link #createMultiValueField}. */
    protected void setMultiValueField(Composite valuesContainer, List<String> values) {
        for (Control c : valuesContainer.getChildren()) c.dispose();
        List<String> items = (values != null && !values.isEmpty()) ? values : List.of("");
        for (String v : items) {
            Label lbl = new Label(valuesContainer, SWT.WRAP);
            lbl.setText(v);
            lbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }
    }
}
