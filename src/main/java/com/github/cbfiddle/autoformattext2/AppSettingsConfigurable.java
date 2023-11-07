package com.github.cbfiddle.autoformattext2;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AppSettingsConfigurable implements Configurable
{
    private AppSettingsComponent mySettingsComponent;

    @Override
    public String getDisplayName()
    {
        return "Auto-Format Text 2 Settings";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent()
    {
        return mySettingsComponent.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent()
    {
        mySettingsComponent = new AppSettingsComponent();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified()
    {
        AppSettingsState settings = AppSettingsState.getInstance();
        boolean modified = mySettingsComponent.getLineWidth() != settings.lineWidth;
        return modified;
    }

    @Override
    public void apply()
    {
        AppSettingsState settings = AppSettingsState.getInstance();
        settings.lineWidth = mySettingsComponent.getLineWidth();
    }

    @Override
    public void reset()
    {
        AppSettingsState settings = AppSettingsState.getInstance();
        mySettingsComponent.setLineWidth(settings.lineWidth);
    }

    @Override
    public void disposeUIResources()
    {
        mySettingsComponent = null;
    }
}
