package com.wtm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Shared North Star lockup.
 *
 * Vertical treatments use the exact approved primary-logo raster. Horizontal
 * treatments use the exact approved symbol beside live text so small headers
 * stay legible while preserving the same identity geometry.
 */
public final class NorthStarBrandLockup extends JPanel {
    public enum Layout { HORIZONTAL, VERTICAL }

    public NorthStarBrandLockup(
            Layout layout,
            int symbolSize,
            int wordmarkSize,
            boolean tagline
    ){
        setOpaque(false);

        if(layout==Layout.VERTICAL){
            setLayout(new GridBagLayout());
            int width=Math.max(210,(int)Math.round(symbolSize*1.65));
            JLabel approved=new JLabel(NorthStarBrand.primaryLockup(width));
            add(approved);
            return;
        }

        setLayout(new BorderLayout(11,0));
        JLabel symbol=new JLabel(BrandIdentity.symbol(symbolSize));
        add(symbol,BorderLayout.WEST);

        JLabel wordmark=new JLabel(BrandIdentity.product());
        wordmark.setForeground(Theme.text());
        wordmark.setFont(new Font(Font.SANS_SERIF,Font.BOLD,wordmarkSize));

        JLabel sub=new JLabel(BrandIdentity.tagline());
        sub.setForeground(Theme.accent());
        sub.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(8,Math.round(wordmarkSize*.28f))));

        JPanel stack=new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
        wordmark.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(wordmark);
        if(tagline){
            stack.add(Box.createVerticalStrut(2));
            stack.add(sub);
        }

        JPanel words=new JPanel(new GridBagLayout());
        words.setOpaque(false);
        GridBagConstraints center=new GridBagConstraints();
        center.gridx=0;
        center.gridy=0;
        center.anchor=GridBagConstraints.WEST;
        center.weightx=1;
        words.add(stack,center);
        add(words,BorderLayout.CENTER);
    }
}
