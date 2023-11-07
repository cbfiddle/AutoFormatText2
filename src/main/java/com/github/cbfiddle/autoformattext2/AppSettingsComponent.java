package com.github.cbfiddle.autoformattext2;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;

public class AppSettingsComponent
{
    private final JPanel myMainPanel;
    private final JBTextField lineWidthText = new JBTextField();

    public AppSettingsComponent()
    {
       myMainPanel = FormBuilder.createFormBuilder()
           .addLabeledComponent(new JBLabel("Line width: "), lineWidthText, 1, false)
           .addComponent(new JBLabel("If 0, the right margin determines the line width"), 1)
           .addComponentFillVertically(new JPanel(), 0)
           .getPanel();
     }

     public JPanel getPanel()
     {
       return myMainPanel;
     }

     public JComponent getPreferredFocusedComponent()
     {
       return lineWidthText;
     }

    public void setLineWidth(int lineWidth)
    {
        lineWidthText.setText(Integer.toString(lineWidth));
    }

    public int getLineWidth()
    {
        try {
            return Integer.parseInt(lineWidthText.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
