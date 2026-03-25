package org.puppylab.mypassword.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.ui.controller.MainController;
import org.puppylab.mypassword.ui.view.DetailView;
import org.puppylab.mypassword.ui.view.EditView;
import org.puppylab.mypassword.ui.view.EmptyView;
import org.puppylab.mypassword.ui.view.ItemListView;
import org.puppylab.mypassword.ui.view.ToolbarView;
import org.puppylab.mypassword.ui.view.UnlockView;

public class MainWindow {

    public void open() {
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("MyPassword");
        shell.setSize(800, 600);
        shell.setLayout(new GridLayout(1, false));

        // ── top-level stack: unlock vs. main content ──────────────────
        Composite   topContainer = new Composite(shell, SWT.NONE);
        topContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        StackLayout topStack     = new StackLayout();
        topContainer.setLayout(topStack);

        UnlockView unlockView = new UnlockView(topContainer);

        // ── main content (shown after unlock) ─────────────────────────
        Composite mainContent = new Composite(topContainer, SWT.NONE);
        mainContent.setLayout(new GridLayout(1, false));

        ToolbarView toolbar = new ToolbarView(mainContent);

        Composite body = new Composite(mainContent, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));

        ItemListView listView = new ItemListView(body);

        Composite rightContainer = new Composite(body, SWT.NONE);
        rightContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        StackLayout rightStack = new StackLayout();
        rightContainer.setLayout(rightStack);

        EmptyView  emptyView  = new EmptyView(rightContainer);
        DetailView detailView = new DetailView(rightContainer);
        EditView   editView   = new EditView(rightContainer);

        MainController controller = new MainController(
                unlockView, topContainer, topStack, mainContent,
                toolbar, listView, emptyView, detailView, editView,
                rightContainer, rightStack);
        controller.init();

        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        display.dispose();
    }

    public static void main(String[] args) {
        new MainWindow().open();
    }
}
