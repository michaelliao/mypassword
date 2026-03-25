package org.puppylab.mypassword.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class MainWindow {
    private Shell       shell;
    private Table       itemTable;
    private Composite   rightContainer;
    private StackLayout rightStack;

    // 三个核心面板
    private Composite detailPanel;
    private Composite editPanel;
    private Composite emptyPanel;

    public void open() {
        Display display = new Display();
        shell = new Shell(display);
        shell.setText("MyPassword");
        shell.setSize(800, 600);
        shell.setLayout(new GridLayout(1, false));

        // 1. 顶部全局工具栏
        createGlobalToolbar();

        // 2. 主体容器
        Composite body = new Composite(shell, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));

        // 左侧固定列表
        createLeftList(body);

        // 右侧堆栈容器
        rightContainer = new Composite(body, SWT.NONE);
        rightContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        rightStack = new StackLayout();
        rightContainer.setLayout(rightStack);

        // 创建三个状态面板
        createEmptyPanel();
        createDetailPanel();
        createEditPanel();

        // 初始状态：显示空白页
        rightStack.topControl = emptyPanel;

        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        display.dispose();
    }

    private void createGlobalToolbar() {
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(2, false));

        Button btnAdd = new Button(toolbar, SWT.PUSH);
        btnAdd.setText(" + Add New ");
        btnAdd.addListener(SWT.Selection, e -> {
            itemTable.deselectAll(); // 新建时取消左侧选中
            switchToMode(editPanel);
        });

        Text search = new Text(toolbar, SWT.SEARCH | SWT.ICON_SEARCH);
        search.setMessage("Search logins...");
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createLeftList(Composite parent) {
        itemTable = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
        gd.widthHint = 260;
        itemTable.setLayoutData(gd);

        // 模拟数据
        new TableItem(itemTable, SWT.NONE).setText("Google\nexample@gmail.com");
        new TableItem(itemTable, SWT.NONE).setText("GitHub\nmichael-liao");

        // 核心监听：处理选中切换
        itemTable.addListener(SWT.Selection, e -> {
            if (itemTable.getSelectionCount() > 0) {
                switchToMode(detailPanel);
            } else {
                switchToMode(emptyPanel);
            }
        });
    }

    // --- 1. 空白面板 (帮助信息) ---
    private void createEmptyPanel() {
        emptyPanel = new Composite(rightContainer, SWT.NONE);
        // 使用内容居中的布局
        GridLayout layout = new GridLayout(1, false);
        layout.marginTop = 100;
        emptyPanel.setLayout(layout);

        Label iconLabel = new Label(emptyPanel, SWT.CENTER);
        iconLabel.setText("🔑"); // 这里可以用更大的字体或图标
        iconLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label hint = new Label(emptyPanel, SWT.CENTER);
        hint.setText("请从左侧列表选择一个项，或点击“Add New”创建一个新记录。");
        hint.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        hint.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
    }

    // --- 2. 详情面板 (含 Edit 按钮) ---
    private void createDetailPanel() {
        detailPanel = new Composite(rightContainer, SWT.NONE);
        detailPanel.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(detailPanel, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> switchToMode(editPanel));

        new Label(detailPanel, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // 模拟详情字段
        createDisplayField(detailPanel, "Title:", "GitHub");
        createDisplayField(detailPanel, "Username:", "michael-liao");
    }

    // --- 3. 编辑面板 (含 Save/Cancel 按钮) ---
    private void createEditPanel() {
        editPanel = new Composite(rightContainer, SWT.NONE);
        editPanel.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(editPanel, SWT.NONE);
        RowLayout rl = new RowLayout();
        rl.spacing = 10;
        actions.setLayout(rl);

        Button btnSave = new Button(actions, SWT.PUSH);
        btnSave.setText(" Save ");
        btnSave.addListener(SWT.Selection, e -> switchToMode(detailPanel));

        Button btnCancel = new Button(actions, SWT.PUSH);
        btnCancel.setText(" Cancel ");
        btnCancel.addListener(SWT.Selection, e -> {
            // 如果原本有选中项，回详情；否则回空白页
            if (itemTable.getSelectionCount() > 0)
                switchToMode(detailPanel);
            else
                switchToMode(emptyPanel);
        });

        new Label(editPanel, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // 模拟编辑字段
        createEditField(editPanel, "Title:");
        createEditField(editPanel, "Username:");
    }

    private void switchToMode(Composite panel) {
        rightStack.topControl = panel;
        rightContainer.layout();
    }

    private void createDisplayField(Composite parent, String label, String val) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        new Label(c, SWT.NONE).setText(label);
        Label v = new Label(c, SWT.BOLD);
        v.setText(val);
    }

    private void createEditField(Composite parent, String label) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        new Label(c, SWT.NONE).setText(label);
        new Text(c, SWT.BORDER).setLayoutData(new GridData(200, SWT.DEFAULT));
    }

    public static void main(String[] args) {
        new MainWindow().open();
    }

}
