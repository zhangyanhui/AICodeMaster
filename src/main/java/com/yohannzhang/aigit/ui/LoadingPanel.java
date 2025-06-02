package com.yohannzhang.aigit.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;

public class LoadingPanel extends JPanel {
    private static final int SPINNER_SIZE = 40;
    private static final int SPINNER_STROKE = 3;
    private static final Color SPINNER_COLOR = new JBColor(
        new Color(97, 175, 239),  // Light theme color
        new Color(65, 134, 204)   // Dark theme color
    );
    private static final Color BACKGROUND_COLOR = new JBColor(
        new Color(250, 250, 250), // Light theme color
        new Color(43, 43, 43)     // Dark theme color
    );
    private static final Color TEXT_COLOR = new JBColor(
        new Color(60, 60, 60),    // Light theme color
        new Color(180, 180, 180)  // Dark theme color
    );

    private double angle = 0;
    private Timer timer;
    private String text = "正在生成文档...";
    private final JLabel textLabel;

    public LoadingPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BACKGROUND_COLOR);
        setBorder(JBUI.Borders.empty(20));

        // 创建文本标签
        textLabel = new JLabel(text);
        textLabel.setFont(JBUI.Fonts.label(14));
        textLabel.setForeground(TEXT_COLOR);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加组件
        add(Box.createVerticalGlue());
        add(Box.createVerticalStrut(20));
        add(textLabel);
        add(Box.createVerticalStrut(30));

        // 设置定时器
        timer = new Timer(16, e -> {
            angle = (angle + 5) % 360;
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        
        // 启用抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 保存原始变换
        AffineTransform originalTransform = g2d.getTransform();

        // 计算中心点
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // 绘制加载动画
        g2d.setColor(SPINNER_COLOR);
        g2d.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // 创建弧形
        Arc2D arc = new Arc2D.Double(
            centerX - SPINNER_SIZE/2,
            centerY - SPINNER_SIZE/2,
            SPINNER_SIZE,
            SPINNER_SIZE,
            angle,
            270,
            Arc2D.OPEN
        );
        
        g2d.draw(arc);

        // 恢复原始变换
        g2d.setTransform(originalTransform);
        g2d.dispose();
    }

    public void startLoading() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    public void stopLoading() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }

    public void setText(String text) {
        this.text = text;
        textLabel.setText(text);
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        if (textLabel != null) {
            textLabel.setBackground(bg);
        }
    }

    @Override
    public void setForeground(Color fg) {
        super.setForeground(fg);
        if (textLabel != null) {
            textLabel.setForeground(fg);
        }
    }
} 