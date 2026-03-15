// FILE: src/main/java/com/igcalc/gui/widget/CalculatorButtonWidget.java
package com.igcalc.gui.widget;

/**
 * Data record describing a calculator button.
 * CalculatorScreen renders these manually using DrawContext.fill() instead of using
 * Minecraft's ButtonWidget (which is now abstract with a final renderWidget).
 */
public record CalculatorButtonWidget(
        int x, int y, int w, int h,
        String label,
        int bgColor,
        int hoverColor,
        int textColor,
        Runnable action
) {}
