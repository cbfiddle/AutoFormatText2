package com.github.cbfiddle.autoformattext2;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class AutoFormatTextAction
  extends AnAction
{
    public void actionPerformed(@NotNull AnActionEvent event)
    {
        // Get optional maximum line width from settings
        AppSettingsState settings = AppSettingsState.getInstance().getState();
        int specifiedLineWidth = settings.lineWidth;
        AutoFormatTextActionProcessor p = new AutoFormatTextActionProcessor(event, specifiedLineWidth);
    }
}
