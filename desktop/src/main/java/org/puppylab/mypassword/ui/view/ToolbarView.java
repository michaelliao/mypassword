package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

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
import org.puppylab.mypassword.core.data.ItemType;

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
        btnAdd.setText(i18n("toolbar.btn.add_new"));

        Menu addMenu = new Menu(btnAdd);
        addMenuItem(addMenu, i18n("toolbar.menu.login"), ItemType.LOGIN);
        addMenuItem(addMenu, i18n("toolbar.menu.note"), ItemType.NOTE);
        addMenuItem(addMenu, i18n("toolbar.menu.identity"), ItemType.IDENTITY);

        btnAdd.addListener(SWT.Selection, _ -> {
            Rectangle bounds = btnAdd.getBounds();
            Point loc = btnAdd.getParent().toDisplay(bounds.x, bounds.y + bounds.height);
            addMenu.setLocation(loc);
            addMenu.setVisible(true);
        });

        // ── Search field (fills remaining space) ──────────────────────
        Text search = new Text(composite, SWT.SEARCH | SWT.ICON_SEARCH);
        search.setMessage(i18n("toolbar.search.placeholder"));
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addModifyListener(_ -> {
            if (onSearch != null)
                onSearch.accept(search.getText());
        });

        // ── Lock button (pinned to the right) ─────────────────────────
        Button btnLock = new Button(composite, SWT.PUSH);
        btnLock.setText(i18n("toolbar.btn.lock"));
        btnLock.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        btnLock.addListener(SWT.Selection, _ -> {
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
        item.addListener(SWT.Selection, _ -> {
            if (onAddNew != null)
                onAddNew.accept(type);
        });
    }
}
