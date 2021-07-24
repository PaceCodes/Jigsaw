package net.minecraft.src;

import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;

public class GuiOptionSliderOF extends GuiOptionSlider implements IOptionControl
{
    private GameSettings.Options option = null;

    public GuiOptionSliderOF(int p_i50_1_, int p_i50_2_, int p_i50_3_, GameSettings.Options p_i50_4_)
    {
        super(p_i50_1_, p_i50_2_, p_i50_3_, p_i50_4_);
        this.option = p_i50_4_;
    }

    public GameSettings.Options getOption()
    {
        return this.option;
    }
}
