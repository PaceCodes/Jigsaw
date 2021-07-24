package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

public class GuiLockIconButton extends GuiButton
{
    private boolean locked;

    public GuiLockIconButton(int p_i45538_1_, int p_i45538_2_, int p_i45538_3_)
    {
        super(p_i45538_1_, p_i45538_2_, p_i45538_3_, 20, 20, "");
    }

    public boolean isLocked()
    {
        return this.locked;
    }

    public void setLocked(boolean lockedIn)
    {
        this.locked = lockedIn;
    }

    public void drawButton(Minecraft p_191745_1_, int p_191745_2_, int p_191745_3_, float p_191745_4_)
    {
        if (this.visible)
        {
            p_191745_1_.getTextureManager().bindTexture(GuiButton.BUTTON_TEXTURES);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            boolean flag = p_191745_2_ >= this.x && p_191745_3_ >= this.y && p_191745_2_ < this.x + this.width && p_191745_3_ < this.y + this.height;
            GuiLockIconButton.Icon guilockiconbutton$icon;

            if (this.locked)
            {
                if (!this.enabled)
                {
                    guilockiconbutton$icon = GuiLockIconButton.Icon.LOCKED_DISABLED;
                }
                else if (flag)
                {
                    guilockiconbutton$icon = GuiLockIconButton.Icon.LOCKED_HOVER;
                }
                else
                {
                    guilockiconbutton$icon = GuiLockIconButton.Icon.LOCKED;
                }
            }
            else if (!this.enabled)
            {
                guilockiconbutton$icon = GuiLockIconButton.Icon.UNLOCKED_DISABLED;
            }
            else if (flag)
            {
                guilockiconbutton$icon = GuiLockIconButton.Icon.UNLOCKED_HOVER;
            }
            else
            {
                guilockiconbutton$icon = GuiLockIconButton.Icon.UNLOCKED;
            }

            this.drawTexturedModalRect(this.x, this.y, guilockiconbutton$icon.getX(), guilockiconbutton$icon.getY(), this.width, this.height);
        }
    }

    static enum Icon
    {
        LOCKED(0, 146),
        LOCKED_HOVER(0, 166),
        LOCKED_DISABLED(0, 186),
        UNLOCKED(20, 146),
        UNLOCKED_HOVER(20, 166),
        UNLOCKED_DISABLED(20, 186);

        private final int x;
        private final int y;

        private Icon(int p_i45537_3_, int p_i45537_4_)
        {
            this.x = p_i45537_3_;
            this.y = p_i45537_4_;
        }

        public int getX()
        {
            return this.x;
        }

        public int getY()
        {
            return this.y;
        }
    }
}
