package com.github.cbfiddle.autoformattext2;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "com.github.cbfiddle.autoformattext2.AppSettingsState",
  storages = @Storage("AutoFormatText2Plugin.xml")
)

final class AppSettingsState implements PersistentStateComponent<AppSettingsState>
{
    public int lineWidth = 0;

    static AppSettingsState getInstance()
    {
        return ApplicationManager.getApplication().getService(AppSettingsState.class);
    }

    @Override
    public @NotNull AppSettingsState getState()
    {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettingsState state)
    {
        XmlSerializerUtil.copyBean(state, this);
    }
}
