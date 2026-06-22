package com.dormitory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class DormitoryManagementGui extends JFrame {
    private static final Font BASE_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
    private static final Font TITLE_FONT = new Font("Microsoft YaHei UI", Font.BOLD, 22);
    private static final Color BACKGROUND = new Color(245, 247, 250);
    private static final Color PANEL_BACKGROUND = Color.WHITE;
    private static final Color PRIMARY = new Color(27, 94, 180);
    private static final Color TEXT = new Color(35, 40, 48);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final StudentDormService studentDormService;
    private final ChangeRequestService changeRequestService;
    private final DormAnalysisService dormAnalysisService;
    private final CardLayout cardLayout;
    private final JPanel rootPanel;

    private JTextField usernameField;
    private JPasswordField passwordField;

    public DormitoryManagementGui(Path dataDirectory) {
        MysqlConnectionFactory connectionFactory = MysqlSupport.initializedConnectionFactory();
        this.authService = new AuthService();
        this.studentDormService = new StudentDormService(new MysqlStudentRepository(connectionFactory));
        this.changeRequestService = new ChangeRequestService(new MysqlChangeRequestRepository(connectionFactory), studentDormService);
        this.dormAnalysisService = new DormAnalysisService();
        this.cardLayout = new CardLayout();
        this.rootPanel = new JPanel(cardLayout);

        setTitle("学生宿舍信息管理系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 720));
        setLocationByPlatform(true);
        rootPanel.add(buildLoginPanel(), "login");
        setContentPane(rootPanel);
    }

    public static void launch(Path dataDirectory) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            DormitoryManagementGui frame = new DormitoryManagementGui(dataDirectory);
            frame.setVisible(true);
            frame.setLocationRelativeTo(null);
        });
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignoredAgain) {
                // Keep Swing default if both look-and-feel options are unavailable.
            }
        }
        UIManager.put("Label.font", BASE_FONT);
        UIManager.put("Button.font", BASE_FONT);
        UIManager.put("TextField.font", BASE_FONT);
        UIManager.put("PasswordField.font", BASE_FONT);
        UIManager.put("TextArea.font", BASE_FONT);
        UIManager.put("TabbedPane.font", BASE_FONT);
        UIManager.put("Table.font", BASE_FONT);
        UIManager.put("TableHeader.font", BASE_FONT.deriveFont(Font.BOLD));
        UIManager.put("ComboBox.font", BASE_FONT);
    }

    private JPanel buildLoginPanel() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BACKGROUND);

        JPanel loginBox = new JPanel(new GridBagLayout());
        loginBox.setBackground(PANEL_BACKGROUND);
        loginBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 234)),
                new EmptyBorder(32, 38, 32, 38)));
        loginBox.setPreferredSize(new Dimension(460, 390));

        JLabel title = new JLabel("学生宿舍信息管理系统");
        title.setFont(TITLE_FONT);
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel("宿舍信息、调换申请与智能分析一体化管理");
        subtitle.setFont(BASE_FONT);
        subtitle.setForeground(new Color(92, 101, 116));

        usernameField = new JTextField("admin");
        passwordField = new JPasswordField("admin123");
        JButton loginButton = createButton("登录", true);
        loginButton.addActionListener(event -> login());
        getRootPane().setDefaultButton(loginButton);

        JLabel hint = new JLabel("演示账号：admin/admin123 或 student/student123");
        hint.setFont(BASE_FONT.deriveFont(12f));
        hint.setForeground(new Color(105, 116, 132));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 8, 0);
        loginBox.add(title, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 24, 0);
        loginBox.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 12);
        loginBox.add(label("用户名"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        loginBox.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 18, 12);
        loginBox.add(label("密码"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        loginBox.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(4, 0, 14, 0);
        loginBox.add(loginButton, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        loginBox.add(hint, gbc);

        page.add(loginBox);
        return page;
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        Optional<User> user = authService.login(username, password);
        if (user.isEmpty()) {
            showError("登录失败", "用户名或密码错误。");
            return;
        }
        showDashboard(user.get());
    }

    private void showDashboard(User user) {
        JPanel dashboard = new JPanel(new BorderLayout());
        dashboard.setBackground(BACKGROUND);
        dashboard.add(buildHeader(user), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(12, 14, 14, 14));
        if (user.getRole() == UserRole.ADMIN) {
            tabs.addTab("信息管理", buildAdminStudentPanel());
            tabs.addTab("调换审批", buildAdminRequestPanel());
            tabs.addTab("统计分析", buildAnalysisPanel());
        } else {
            tabs.addTab("信息查询", buildUserQueryPanel());
            tabs.addTab("调换申请", buildUserRequestPanel());
        }
        dashboard.add(tabs, BorderLayout.CENTER);

        rootPanel.add(dashboard, "dashboard");
        cardLayout.show(rootPanel, "dashboard");
        revalidate();
        repaint();
    }

    private JPanel buildHeader(User user) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(22, 54, 92));
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel("学生宿舍信息管理系统");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);

        String roleText = user.getRole() == UserRole.ADMIN ? "系统管理员" : "普通用户";
        JLabel userInfo = new JLabel(roleText + "：" + user.getUsername());
        userInfo.setForeground(new Color(218, 228, 242));
        userInfo.setFont(BASE_FONT);

        JButton logout = createButton("退出登录", false);
        logout.addActionListener(event -> {
            cardLayout.show(rootPanel, "login");
            setTitle("学生宿舍信息管理系统");
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(userInfo);
        right.add(logout);

        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildAdminStudentPanel() {
        JPanel root = createContentPanel(new BorderLayout(12, 12));
        DefaultTableModel model = createStudentTableModel();
        JTable table = createTable(model);
        refreshStudentRows(model, studentDormService.listAll());

        JPanel form = createStudentForm(model);
        root.add(form, BorderLayout.WEST);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(createStudentToolbar(model, table, true), BorderLayout.NORTH);
        return root;
    }

    private JPanel createStudentToolbar(DefaultTableModel model, JTable table, boolean adminMode) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(PANEL_BACKGROUND);
        toolbar.setBorder(createSectionBorder("查询与操作"));

        JTextField studentIdField = new JTextField(10);
        JTextField dormField = new JTextField(10);
        JButton queryStudent = createButton("按学号查询", false);
        JButton queryDorm = createButton("按宿舍查询", false);
        JButton all = createButton("显示全部", false);
        JButton sort = createButton("按系/班级排序", false);

        queryStudent.addActionListener(event -> refreshStudentRows(
                model,
                studentDormService.findByStudentId(studentIdField.getText().trim()).map(List::of).orElseGet(List::of)));
        queryDorm.addActionListener(event -> refreshStudentRows(model, studentDormService.findByDormNumber(dormField.getText().trim())));
        all.addActionListener(event -> refreshStudentRows(model, studentDormService.listAll()));
        sort.addActionListener(event -> refreshStudentRows(model, studentDormService.sortByDepartmentAndClass()));

        toolbar.add(label("学号"));
        toolbar.add(studentIdField);
        toolbar.add(queryStudent);
        toolbar.add(label("宿舍号"));
        toolbar.add(dormField);
        toolbar.add(queryDorm);
        toolbar.add(all);
        toolbar.add(sort);

        if (adminMode) {
            JButton delete = createButton("删除选中", false);
            delete.addActionListener(event -> deleteSelectedStudent(table, model));
            toolbar.add(delete);
        }

        return toolbar;
    }

    private JPanel createStudentForm(DefaultTableModel model) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BACKGROUND);
        form.setPreferredSize(new Dimension(330, 0));
        form.setBorder(createSectionBorder("添加学生宿舍信息"));

        JTextField studentId = new JTextField();
        JTextField name = new JTextField();
        JTextField department = new JTextField();
        JTextField className = new JTextField();
        JTextField dormNumber = new JTextField();
        JTextField dormPhone = new JTextField();
        JTextField bedNumber = new JTextField();

        addFormRow(form, 0, "学号", studentId);
        addFormRow(form, 1, "姓名", name);
        addFormRow(form, 2, "所在系", department);
        addFormRow(form, 3, "班级", className);
        addFormRow(form, 4, "宿舍号", dormNumber);
        addFormRow(form, 5, "宿舍电话", dormPhone);
        addFormRow(form, 6, "床位", bedNumber);

        JButton add = createButton("添加信息", true);
        JButton clear = createButton("清空", false);
        add.addActionListener(event -> {
            try {
                studentDormService.add(new StudentDormRecord(
                        studentId.getText().trim(),
                        name.getText().trim(),
                        department.getText().trim(),
                        className.getText().trim(),
                        dormNumber.getText().trim(),
                        dormPhone.getText().trim(),
                        bedNumber.getText().trim()));
                refreshStudentRows(model, studentDormService.listAll());
                clearTextFields(studentId, name, department, className, dormNumber, dormPhone, bedNumber);
                showInfo("添加成功", "学生宿舍信息已保存。");
            } catch (RuntimeException e) {
                showError("添加失败", e.getMessage());
            }
        });
        clear.addActionListener(event -> clearTextFields(studentId, name, department, className, dormNumber, dormPhone, bedNumber));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(clear);
        buttons.add(add);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(14, 0, 0, 0);
        form.add(buttons, gbc);

        return form;
    }

    private JPanel buildAdminRequestPanel() {
        JPanel root = createContentPanel(new BorderLayout(12, 12));
        DefaultTableModel model = createRequestTableModel();
        JTable table = createTable(model);
        refreshRequestRows(model, changeRequestService.listPending());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(PANEL_BACKGROUND);
        toolbar.setBorder(createSectionBorder("申请审核"));

        JButton pending = createButton("待审核", false);
        JButton all = createButton("全部申请", false);
        JButton approve = createButton("同意选中", true);
        JButton reject = createButton("拒绝选中", false);

        pending.addActionListener(event -> refreshRequestRows(model, changeRequestService.listPending()));
        all.addActionListener(event -> refreshRequestRows(model, changeRequestService.listAll()));
        approve.addActionListener(event -> handleRequest(table, model, true));
        reject.addActionListener(event -> handleRequest(table, model, false));

        toolbar.add(pending);
        toolbar.add(all);
        toolbar.add(approve);
        toolbar.add(reject);

        root.add(toolbar, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildAnalysisPanel() {
        JPanel root = createContentPanel(new BorderLayout(12, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        controls.setBackground(PANEL_BACKGROUND);
        controls.setBorder(createSectionBorder("统计范围"));
        JComboBox<String> scope = new JComboBox<>(new String[]{"楼栋", "宿舍"});
        JTextField value = new JTextField("3", 14);
        JButton analyze = createButton("生成统计与建议", true);
        controls.add(label("范围"));
        controls.add(scope);
        controls.add(label("编号"));
        controls.add(value);
        controls.add(analyze);

        JTextArea statisticsArea = createReadOnlyTextArea();
        JTextArea analysisArea = createReadOnlyTextArea();
        statisticsArea.setBorder(createSectionBorder("统计数据"));
        analysisArea.setBorder(createSectionBorder("智能宿舍运营评估与建议"));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(statisticsArea),
                new JScrollPane(analysisArea));
        splitPane.setResizeWeight(0.42);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        analyze.addActionListener(event -> {
            String scopeText = String.valueOf(scope.getSelectedItem());
            String input = value.getText().trim();
            if (input.isBlank()) {
                showError("输入不完整", "请输入楼栋号或宿舍号。");
                return;
            }
            DormStatistics statistics = "楼栋".equals(scopeText)
                    ? studentDormService.statisticsByBuilding(input)
                    : studentDormService.statisticsByDorm(input);
            statisticsArea.setText(statistics.toPromptText());
            analysisArea.setText("正在生成建议，请稍候...");
            analyze.setEnabled(false);
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return dormAnalysisService.analyze(statistics);
                }

                @Override
                protected void done() {
                    try {
                        analysisArea.setText(get());
                    } catch (Exception e) {
                        analysisArea.setText("生成失败：" + e.getMessage());
                    } finally {
                        analyze.setEnabled(true);
                    }
                }
            }.execute();
        });

        root.add(controls, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildUserQueryPanel() {
        JPanel root = createContentPanel(new BorderLayout(12, 12));
        DefaultTableModel model = createStudentTableModel();
        JTable table = createTable(model);
        refreshStudentRows(model, studentDormService.listAll());

        root.add(createStudentToolbar(model, table, false), BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildUserRequestPanel() {
        JPanel root = createContentPanel(new BorderLayout(12, 12));
        DefaultTableModel model = createRequestTableModel();
        JTable table = createTable(model);

        JPanel submitPanel = new JPanel(new GridBagLayout());
        submitPanel.setBackground(PANEL_BACKGROUND);
        submitPanel.setPreferredSize(new Dimension(340, 0));
        submitPanel.setBorder(createSectionBorder("提交调换申请"));

        JTextField studentId = new JTextField();
        JTextField targetDorm = new JTextField();
        JTextField targetPhone = new JTextField();
        JTextField targetBed = new JTextField();
        JTextArea reason = new JTextArea(5, 20);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);

        addFormRow(submitPanel, 0, "学号", studentId);
        addFormRow(submitPanel, 1, "目标宿舍", targetDorm);
        addFormRow(submitPanel, 2, "目标电话", targetPhone);
        addFormRow(submitPanel, 3, "目标床位", targetBed);

        GridBagConstraints reasonGbc = new GridBagConstraints();
        reasonGbc.gridx = 0;
        reasonGbc.gridy = 4;
        reasonGbc.anchor = GridBagConstraints.NORTHWEST;
        reasonGbc.insets = new Insets(8, 0, 8, 10);
        submitPanel.add(label("理由"), reasonGbc);
        reasonGbc.gridx = 1;
        reasonGbc.weightx = 1;
        reasonGbc.weighty = 1;
        reasonGbc.fill = GridBagConstraints.BOTH;
        submitPanel.add(new JScrollPane(reason), reasonGbc);

        JButton submit = createButton("提交申请", true);
        JButton history = createButton("查看我的申请", false);
        submit.addActionListener(event -> {
            try {
                DormChangeRequest request = changeRequestService.submit(
                        studentId.getText().trim(),
                        targetDorm.getText().trim(),
                        targetPhone.getText().trim(),
                        targetBed.getText().trim(),
                        reason.getText().trim());
                refreshRequestRows(model, changeRequestService.listByStudentId(studentId.getText().trim()));
                clearTextFields(targetDorm, targetPhone, targetBed);
                reason.setText("");
                showInfo("提交成功", "申请编号：" + request.getId());
            } catch (RuntimeException e) {
                showError("提交失败", e.getMessage());
            }
        });
        history.addActionListener(event -> refreshRequestRows(model, changeRequestService.listByStudentId(studentId.getText().trim())));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(history);
        buttons.add(submit);

        GridBagConstraints buttonGbc = new GridBagConstraints();
        buttonGbc.gridx = 0;
        buttonGbc.gridy = 5;
        buttonGbc.gridwidth = 2;
        buttonGbc.weightx = 1;
        buttonGbc.fill = GridBagConstraints.HORIZONTAL;
        buttonGbc.insets = new Insets(14, 0, 0, 0);
        submitPanel.add(buttons, buttonGbc);

        JPanel historyPanel = createContentPanel(new BorderLayout(8, 8));
        historyPanel.setBorder(createSectionBorder("申请记录"));
        historyPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        root.add(submitPanel, BorderLayout.WEST);
        root.add(historyPanel, BorderLayout.CENTER);
        return root;
    }

    private void deleteSelectedStudent(JTable table, DefaultTableModel model) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            showError("未选择记录", "请先在表格中选择要删除的学生记录。");
            return;
        }
        int row = table.convertRowIndexToModel(selectedRow);
        String studentId = String.valueOf(model.getValueAt(row, 0));
        String dormNumber = String.valueOf(model.getValueAt(row, 4));
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "确认删除学号 " + studentId + " 在宿舍 " + dormNumber + " 的记录吗？",
                "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        boolean removed = studentDormService.deleteByDormAndStudent(dormNumber, studentId);
        if (removed) {
            refreshStudentRows(model, studentDormService.listAll());
            showInfo("删除成功", "学生宿舍信息已删除。");
        } else {
            showError("删除失败", "未找到匹配记录。");
        }
    }

    private void handleRequest(JTable table, DefaultTableModel model, boolean approve) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            showError("未选择申请", "请先选择一条调换申请。");
            return;
        }
        int row = table.convertRowIndexToModel(selectedRow);
        String requestId = String.valueOf(model.getValueAt(row, 0));
        String comment = JOptionPane.showInputDialog(this, "请输入审核意见：", approve ? "同意申请" : "拒绝申请", JOptionPane.PLAIN_MESSAGE);
        if (comment == null) {
            return;
        }
        try {
            if (approve) {
                changeRequestService.approve(requestId, comment.trim());
                showInfo("审批完成", "已同意申请，学生宿舍信息已自动更新。");
            } else {
                changeRequestService.reject(requestId, comment.trim());
                showInfo("审批完成", "已拒绝申请。");
            }
            refreshRequestRows(model, changeRequestService.listPending());
        } catch (RuntimeException e) {
            showError("审批失败", e.getMessage());
        }
    }

    private DefaultTableModel createStudentTableModel() {
        return new DefaultTableModel(new Object[]{"学号", "姓名", "所在系", "班级", "宿舍号", "宿舍电话", "床位"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private DefaultTableModel createRequestTableModel() {
        return new DefaultTableModel(new Object[]{"申请编号", "学号", "原宿舍", "原床位", "目标宿舍", "目标床位", "状态", "申请时间", "理由/意见"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void refreshStudentRows(DefaultTableModel model, List<StudentDormRecord> records) {
        model.setRowCount(0);
        for (StudentDormRecord record : records) {
            model.addRow(new Object[]{
                    record.getStudentId(),
                    record.getName(),
                    record.getDepartment(),
                    record.getClassName(),
                    record.getDormNumber(),
                    record.getDormPhone(),
                    record.getBedNumber()
            });
        }
    }

    private void refreshRequestRows(DefaultTableModel model, List<DormChangeRequest> requests) {
        model.setRowCount(0);
        for (DormChangeRequest request : requests) {
            String handledText = request.getAdminComment() == null || request.getAdminComment().isBlank()
                    ? request.getReason()
                    : request.getReason() + " / " + request.getAdminComment();
            model.addRow(new Object[]{
                    request.getId(),
                    request.getStudentId(),
                    request.getCurrentDormNumber(),
                    request.getCurrentBedNumber(),
                    request.getTargetDormNumber(),
                    request.getTargetBedNumber(),
                    request.getStatus().getDisplayName(),
                    request.getCreatedAt().format(DATE_TIME_FORMATTER),
                    handledText
            });
        }
    }

    private JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(new Color(207, 226, 255));
        table.setSelectionForeground(TEXT);
        table.setGridColor(new Color(229, 233, 240));

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(235, 241, 248));
        header.setForeground(TEXT);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 34));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }

    private JPanel createContentPanel(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    private JPanel createContentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL_BACKGROUND);
        return panel;
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(BASE_FONT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? PRIMARY : new Color(198, 205, 216)),
                new EmptyBorder(7, 13, 7, 13)));
        if (primary) {
            button.setBackground(PRIMARY);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(Color.WHITE);
            button.setForeground(TEXT);
        }
        return button;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BASE_FONT);
        label.setForeground(TEXT);
        return label;
    }

    private TitledBorder createSectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 234)),
                title);
        border.setTitleFont(BASE_FONT.deriveFont(Font.BOLD));
        border.setTitleColor(TEXT);
        return border;
    }

    private JTextArea createReadOnlyTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(BASE_FONT);
        textArea.setForeground(TEXT);
        textArea.setBackground(Color.WHITE);
        textArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        return textArea;
    }

    private void addFormRow(JPanel panel, int row, String labelText, Component field) {
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(8, 0, 8, 10);
        panel.add(label(labelText), labelGbc);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = 1;
        fieldGbc.gridy = row;
        fieldGbc.weightx = 1;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.insets = new Insets(8, 0, 8, 0);
        panel.add(field, fieldGbc);
    }

    private void clearTextFields(JTextField... fields) {
        for (JTextField field : fields) {
            field.setText("");
        }
    }

    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
