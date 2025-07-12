import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class CustomSkillSwapUI {

    static java.util.List<User> users = new ArrayList<>();
    static Admin admin = new Admin();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CustomSkillSwapUI::createUI);
    }

    static void createUI() {
        JFrame frame = new JFrame("🎯 Skill Swap Platform");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // Use custom font and colors
        UIManager.put("Panel.background", new Color(245, 245, 245));
        UIManager.put("TabbedPane.selected", new Color(240, 240, 240));
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 13));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 13));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));

        tabs.addTab("👤 Register User", createRegisterPanel());
        tabs.addTab("👥 All Users", createUserListPanel());
        tabs.addTab("🔁 Swap Skills", createSwapPanel());
        tabs.addTab("🛠 Admin Panel", createAdminPanel());

        frame.add(tabs);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridLayout(10, 2, 10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        panel.setBackground(Color.WHITE);

        JTextField name = new JTextField();
        JTextField location = new JTextField();
        JTextField photo = new JTextField();
        JTextField availability = new JTextField();
        JCheckBox isPublic = new JCheckBox("Make Profile Public", true);
        JTextField offered = new JTextField();
        JTextField wanted = new JTextField();
        JButton register = createButton("Register", new Color(76, 175, 80));

        JTextArea result = new JTextArea(3, 40);
        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);

        panel.add(new JLabel("Name:")); panel.add(name);
        panel.add(new JLabel("Location:")); panel.add(location);
        panel.add(new JLabel("Photo URL:")); panel.add(photo);
        panel.add(new JLabel("Availability:")); panel.add(availability);
        panel.add(new JLabel("Skills Offered (comma):")); panel.add(offered);
        panel.add(new JLabel("Skills Wanted (comma):")); panel.add(wanted);
        panel.add(new JLabel("")); panel.add(isPublic);
        panel.add(new JLabel("")); panel.add(register);
        panel.add(new JLabel("")); panel.add(new JScrollPane(result));

        register.addActionListener(e -> {
            User user = new User(name.getText());
            user.location = location.getText();
            user.profilePhoto = photo.getText();
            user.availability = availability.getText();
            user.isPublic = isPublic.isSelected();
            for (String s : offered.getText().split(",")) user.skillsOffered.add(s.trim());
            for (String s : wanted.getText().split(",")) user.skillsWanted.add(s.trim());
            users.add(user);
            result.setText("✅ User '" + user.name + "' Registered Successfully!");
            name.setText(""); location.setText(""); photo.setText("");
            availability.setText(""); offered.setText(""); wanted.setText("");
        });

        return panel;
    }

    static JPanel createUserListPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JButton refresh = createButton("🔄 Refresh", new Color(33, 150, 243));

        refresh.addActionListener(e -> {
            area.setText("");
            for (User u : users) {
                area.append(u + "\n\n");
            }
        });

        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(refresh, BorderLayout.SOUTH);
        return panel;
    }

    static JPanel createSwapPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        panel.setBackground(Color.WHITE);

        JComboBox<String> userFrom = new JComboBox<>();
        JComboBox<String> userTo = new JComboBox<>();
        JButton send = createButton("📨 Send Request", new Color(33, 150, 243));
        JButton accept = createButton("✅ Accept First", new Color(76, 175, 80));
        JTextArea result = new JTextArea(3, 30);
        result.setEditable(false);

        updateComboBoxes(userFrom, userTo);

        panel.add(new JLabel("From User:")); panel.add(userFrom);
        panel.add(new JLabel("To User:")); panel.add(userTo);
        panel.add(send); panel.add(accept);
        panel.add(new JLabel("")); panel.add(new JScrollPane(result));

        send.addActionListener(e -> {
            int i = userFrom.getSelectedIndex();
            int j = userTo.getSelectedIndex();
            if (i >= 0 && j >= 0 && i != j) {
                SkillSwapPlatform.sendSwapRequest(users.get(i), users.get(j));
                result.append("📨 Request Sent from " + users.get(i).name + " to " + users.get(j).name + "\n");
            }
        });

        accept.addActionListener(e -> {
            int j = userTo.getSelectedIndex();
            if (j >= 0 && !users.get(j).swapRequests.isEmpty()) {
                SkillSwapPlatform.acceptSwapRequest(users.get(j), 0);
                result.append("✅ Request Accepted by " + users.get(j).name + "\n");
            }
        });

        return panel;
    }

    static void updateComboBoxes(JComboBox<String> from, JComboBox<String> to) {
        from.removeAllItems(); to.removeAllItems();
        for (User u : users) {
            from.addItem(u.name); to.addItem(u.name);
        }
    }

    static JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        panel.setBackground(Color.WHITE);

        JTextArea output = new JTextArea(10, 40);
        output.setEditable(false);
        output.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JTextField message = new JTextField();
        JButton broadcast = createButton("📢 Broadcast", new Color(255, 152, 0));
        JButton report = createButton("📄 Download Feedback", new Color(63, 81, 181));

        broadcast.addActionListener(e -> {
            admin.sendPlatformMessage(message.getText());
            output.append("📣 Sent: " + message.getText() + "\n");
            message.setText("");
        });

        report.addActionListener(e -> {
            output.append("\n--- 📋 Feedback Report ---\n");
            for (User user : users) {
                output.append(user.name + ": " + user.feedbackList + "\n");
            }
        });

        JPanel top = new JPanel(new GridLayout(2, 1, 5, 5));
        top.setBackground(Color.WHITE);
        top.add(message); top.add(broadcast);

        panel.add(top, BorderLayout.NORTH);
        panel.add(report, BorderLayout.CENTER);
        panel.add(new JScrollPane(output), BorderLayout.SOUTH);
        return panel;
    }

    static JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return btn;
    }
}
