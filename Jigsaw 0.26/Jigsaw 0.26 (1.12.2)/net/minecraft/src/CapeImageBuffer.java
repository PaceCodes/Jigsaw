package net.minecraft.src;

import java.awt.image.BufferedImage;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.util.ResourceLocation;

public class CapeImageBuffer extends ImageBufferDownload
{
    private AbstractClientPlayer player;
    private ResourceLocation resourceLocation;

    public CapeImageBuffer(AbstractClientPlayer p_i21_1_, ResourceLocation p_i21_2_)
    {
        this.player = p_i21_1_;
        this.resourceLocation = p_i21_2_;
    }

    public BufferedImage parseUserSkin(BufferedImage image)
    {
        return CapeUtils.parseCape(image);
    }

    public void skinAvailable()
    {
        if (this.player != null)
        {
            this.player.setLocationOfCape(this.resourceLocation);
        }
    }

    public void cleanup()
    {
        this.player = null;
    }
}
