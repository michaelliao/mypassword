package org.puppylab.mypassword.ui.view;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public class ToolbarView {

    private Runnable         onAddNew;
    private Consumer<String> onSearch;

    public ToolbarView(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        composite.setLayout(new GridLayout(2, false));

        Button btnAdd = new Button(composite, SWT.PUSH);
        btnAdd.setText(" + Add New ");
        btnAdd.addListener(SWT.Selection, e -> { if (onAddNew != null) onAddNew.run(); });

        Text search = new Text(composite, SWT.SEARCH | SWT.ICON_SEARCH);
        search.setMessage("Search logins...");
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addModifyListener(e -> { if (onSearch != null) onSearch.accept(search.getText()); });
    }

    public void setOnAddNew(Runnable listener) {
        this.onAddNew = listener;
    }

    public void setOnSearch(Consumer<String> listener) {
        this.onSearch = listener;
    }
}
