package com.yohannzhang.aigit.ui;

import javax.swing.border.AbstractBorder;
import java.awt.*;

public class RoundedCornerBorder extends AbstractBorder {
    private final int radius;

    public RoundedCornerBorder(int radius) {
        this.radius = radius;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(c.getBackground());
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(4, 8, 4, 8); // 可根据需要调整内边距
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}

