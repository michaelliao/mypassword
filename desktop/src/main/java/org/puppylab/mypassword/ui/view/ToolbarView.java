package org.puppylab.mypassword.ui.view;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.ItemType;

public class ToolbarView {

    private Consumer<Integer> onAddNew;
    private Consumer<String>  onSearch;
    private Runnable          onLock;

    public ToolbarView(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout gl = new GridLayout(3, false);
        gl.marginWidth = 4;
        gl.marginHeight = 4;
        composite.setLayout(gl);

        // ── Add New button with drop-down menu ────────────────────────
        Button btnAdd = new Button(composite, SWT.PUSH);
        btnAdd.setText(" + Add New ");

        Menu addMenu = new Menu(btnAdd);
        addMenuItem(addMenu, "Login", ItemType.LOGIN);
        addMenuItem(addMenu, "Note", ItemType.NOTE);
        addMenuItem(addMenu, "Identity", ItemType.IDENTITY);

        btnAdd.addListener(SWT.Selection, e -> {
            Rectangle bounds = btnAdd.getBounds();
            Point loc = btnAdd.getParent().toDisplay(bounds.x, bounds.y + bounds.height);
            addMenu.setLocation(loc);
            addMenu.setVisible(true);
        });

        // ── Search field (fills remaining space) ──────────────────────
        Text search = new Text(composite, SWT.SEARCH | SWT.ICON_SEARCH);
        search.setMessage("Search...");
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addModifyListener(e -> {
            if (onSearch != null)
                onSearch.accept(search.getText());
        });

        // ── Lock button (pinned to the right) ─────────────────────────
        Button btnLock = new Button(composite, SWT.PUSH);
        btnLock.setText("Lock");
        btnLock.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        btnLock.addListener(SWT.Selection, e -> {
            if (onLock != null)
                onLock.run();
        });
    }

    // ── public API ────────────────────────────────────────────────────

    public void setOnAddNew(Consumer<Integer> listener) {
        this.onAddNew = listener;
    }

    public void setOnSearch(Consumer<String> listener) {
        this.onSearch = listener;
    }

    public void setOnLock(Runnable listener) {
        this.onLock = listener;
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void addMenuItem(Menu menu, String label, Integer type) {
        MenuItem item = new MenuItem(menu, SWT.PUSH);
        item.setText(label);
        item.addListener(SWT.Selection, e -> {
            if (onAddNew != null)
                onAddNew.accept(type);
        });
    }
}
