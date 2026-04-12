package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.util.ArrayList;
import java.util.List;
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
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.ui.Icons;

public abstract class AbstractEditView<T extends AbstractItemData> {

    static final int LABEL_WIDTH = 80;

    public final Composite composite;

    final ScrolledComposite sc;
    final Composite         content;

    private Consumer<T> onSave;
    private Runnable    onCancel;

    public AbstractEditView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        RowLayout rl = new RowLayout();
        rl.spacing = 10;
        actions.setLayout(rl);

        Button btnSave = new Button(actions, SWT.PUSH);
        btnSave.setText(i18n("edit.btn.save"));
        btnSave.setImage(Icons.get("save"));
        btnSave.addListener(SWT.Selection, _ -> {
            if (onSave != null) {
                onSave.accept(collectData());
            }
        });

        Button btnCancel = new Button(actions, SWT.PUSH);
        btnCancel.setText(i18n("edit.btn.cancel"));
        btnCancel.setImage(Icons.get("cancel"));
        btnCancel.addListener(SWT.Selection, _ -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setLayout(new FillLayout(SWT.VERTICAL));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new GridLayout(1, false));

        createFields();

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(_ -> {
            int w = sc.getClientArea().width;
            content.layout(true, true);
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    protected abstract void createFields();

    /**
     * Populate form for editing an existing identity, or pass null for a new one.
     */
    public void edit(T item) {
        setData(item);
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    protected abstract void setData(T item);

    public void triggerSave() {
        if (onSave != null)
            onSave.accept(collectData());
    }

    public void setOnSave(Consumer<T> listener) {
        this.onSave = listener;
    }

    public void setOnCancel(Runnable listener) {
        this.onCancel = listener;
    }

    protected abstract T collectData();

    protected Text createField(String labelText, int textStyle) {
        Composite parent = this.content;
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = LABEL_WIDTH;
        lbl.setLayoutData(ld);

        Text t = new Text(row, textStyle);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return t;
    }

    protected Text createAreaField(String labelText) {
        Composite parent = this.content;
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = LABEL_WIDTH;
        lbl.setLayoutData(ld);

        Text t = new Text(row, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData td = new GridData(SWT.FILL, SWT.TOP, true, false);
        td.heightHint = 80;
        t.setLayoutData(td);
        return t;
    }

    protected MultiText createMultiTextFields(String labelText) {
        // outer row matches the visual style of createField() rows
        Composite row = new Composite(content, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Composite right = new Composite(row, SWT.NONE);
        GridLayout rl = new GridLayout(1, false);
        rl.marginHeight = 0;
        rl.marginWidth = 0;
        rl.verticalSpacing = 4;
        right.setLayout(rl);
        right.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        List<Text> websiteFields = new ArrayList<>();
        Composite websitesContainer = new Composite(right, SWT.NONE);
        GridLayout wl = new GridLayout(1, false);
        wl.marginHeight = 0;
        wl.marginWidth = 0;
        wl.verticalSpacing = 3;
        websitesContainer.setLayout(wl);
        websitesContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Button addWebsiteBtn = new Button(right, SWT.PUSH);
        MultiText multiText = new MultiText(websitesContainer, websiteFields, addWebsiteBtn);

        addWebsiteBtn.setText(i18n("edit.btn.add_more"));
        addWebsiteBtn.setImage(Icons.get("plus"));
        addWebsiteBtn.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        addWebsiteBtn.addListener(SWT.Selection, _ -> addMultiTextRow(multiText, "", true));
        return multiText;
    }

    /** Add one text row. Pass doRelayout=false when bulk-populating. */
    protected void addMultiTextRow(MultiText multiText, String value, boolean doRelayout) {
        Composite row = new Composite(multiText.container(), SWT.NONE);
        GridLayout rl = new GridLayout(2, false);
        rl.marginHeight = 0;
        rl.marginWidth = 0;
        row.setLayout(rl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Text t = new Text(row, SWT.BORDER);
        t.setText(value);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        multiText.fields().add(t);

        Button removeBtn = new Button(row, SWT.PUSH);
        removeBtn.setImage(Icons.get("delete"));
        GridData removeBtnGd = new GridData();
        removeBtnGd.widthHint = 32;
        removeBtn.setLayoutData(removeBtnGd);
        removeBtn.addListener(SWT.Selection, _ -> {
            multiText.fields().remove(t);
            row.dispose();
            updateMultiTextAddButton(multiText);
            relayoutMultiText(multiText);
        });

        updateMultiTextAddButton(multiText);
        if (doRelayout) {
            relayoutMultiText(multiText);
        }
    }

    protected void updateMultiTextAddButton(MultiText multiText) {
        if (multiText.addBtn() != null && !multiText.addBtn().isDisposed()) {
            multiText.addBtn().setEnabled(multiText.fields().size() < 10);
        }
    }

    protected void relayoutMultiText(MultiText multiText) {
        multiText.container().layout(true, true);
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    protected void setMultiTextValues(MultiText multiText, List<String> values) {
        if (values != null && !values.isEmpty()) {
            for (String value : values)
                addMultiTextRow(multiText, value, false);
        } else {
            addMultiTextRow(multiText, "", false);
        }
    }
}
