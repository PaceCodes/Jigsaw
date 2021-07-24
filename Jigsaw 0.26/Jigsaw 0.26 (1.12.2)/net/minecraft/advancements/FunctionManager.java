package net.minecraft.advancements;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;
import com.google.common.io.Files;

import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class FunctionManager implements ITickable
{
    private static final Logger field_193067_a = LogManager.getLogger();
    private final File field_193068_b;
    private final MinecraftServer field_193069_c;
    private final Map<ResourceLocation, FunctionObject> field_193070_d = Maps.<ResourceLocation, FunctionObject>newHashMap();
    private String field_193071_e = "-";
    private FunctionObject field_193072_f;
    private final ArrayDeque<FunctionManager.QueuedCommand> field_194020_g = new ArrayDeque<FunctionManager.QueuedCommand>();
    private boolean field_194021_h = false;
    private final ICommandSender field_193073_g = new ICommandSender()
    {
        public String getName()
        {
            return FunctionManager.this.field_193071_e;
        }
        public boolean canCommandSenderUseCommand(int permLevel, String commandName)
        {
            return permLevel <= 2;
        }
        public World getEntityWorld()
        {
            return FunctionManager.this.field_193069_c.worldServers[0];
        }
        public MinecraftServer getServer()
        {
            return FunctionManager.this.field_193069_c;
        }
    };

    public FunctionManager(@Nullable File p_i47517_1_, MinecraftServer p_i47517_2_)
    {
        this.field_193068_b = p_i47517_1_;
        this.field_193069_c = p_i47517_2_;
        this.func_193059_f();
    }

    @Nullable
    public FunctionObject func_193058_a(ResourceLocation p_193058_1_)
    {
        return this.field_193070_d.get(p_193058_1_);
    }

    public ICommandManager func_193062_a()
    {
        return this.field_193069_c.getCommandManager();
    }

    public int func_193065_c()
    {
        return this.field_193069_c.worldServers[0].getGameRules().getInt("maxCommandChainLength");
    }

    public Map<ResourceLocation, FunctionObject> func_193066_d()
    {
        return this.field_193070_d;
    }

    /**
     * Like the old updateEntity(), except more generic.
     */
    public void update()
    {
        String s = this.field_193069_c.worldServers[0].getGameRules().getString("gameLoopFunction");

        if (!s.equals(this.field_193071_e))
        {
            this.field_193071_e = s;
            this.field_193072_f = this.func_193058_a(new ResourceLocation(s));
        }

        if (this.field_193072_f != null)
        {
            this.func_194019_a(this.field_193072_f, this.field_193073_g);
        }
    }

    public int func_194019_a(FunctionObject p_194019_1_, ICommandSender p_194019_2_)
    {
        int i = this.func_193065_c();

        if (this.field_194021_h)
        {
            if (this.field_194020_g.size() < i)
            {
                this.field_194020_g.addFirst(new FunctionManager.QueuedCommand(this, p_194019_2_, new FunctionObject.FunctionEntry(p_194019_1_)));
            }

            return 0;
        }
        else
        {
            int l;

            try
            {
                this.field_194021_h = true;
                int j = 0;
                FunctionObject.Entry[] afunctionobject$entry = p_194019_1_.func_193528_a();

                for (int k = afunctionobject$entry.length - 1; k >= 0; --k)
                {
                    this.field_194020_g.push(new FunctionManager.QueuedCommand(this, p_194019_2_, afunctionobject$entry[k]));
                }

                while (true)
                {
                    if (this.field_194020_g.isEmpty())
                    {
                        l = j;
                        return l;
                    }

                    (this.field_194020_g.removeFirst()).func_194222_a(this.field_194020_g, i);
                    ++j;

                    if (j >= i)
                    {
                        break;
                    }
                }

                l = j;
            }
            finally
            {
                this.field_194020_g.clear();
                this.field_194021_h = false;
            }

            return l;
        }
    }

    public void func_193059_f()
    {
        this.field_193070_d.clear();
        this.field_193072_f = null;
        this.field_193071_e = "-";
        this.func_193061_h();
    }

    private void func_193061_h()
    {
        if (this.field_193068_b != null)
        {
            this.field_193068_b.mkdirs();

            for (File file1 : FileUtils.listFiles(this.field_193068_b, new String[] {"mcfunction"}, true))
            {
                String s = FilenameUtils.removeExtension(this.field_193068_b.toURI().relativize(file1.toURI()).toString());
                String[] astring = s.split("/", 2);

                if (astring.length == 2)
                {
                    ResourceLocation resourcelocation = new ResourceLocation(astring[0], astring[1]);

                    try
                    {
                        this.field_193070_d.put(resourcelocation, FunctionObject.func_193527_a(this, Files.readLines(file1, StandardCharsets.UTF_8)));
                    }
                    catch (Throwable throwable)
                    {
                        field_193067_a.error("Couldn't read custom function " + resourcelocation + " from " + file1, throwable);
                    }
                }
            }

            if (!this.field_193070_d.isEmpty())
            {
                field_193067_a.info("Loaded " + this.field_193070_d.size() + " custom command functions");
            }
        }
    }

    public static class QueuedCommand
    {
        private final FunctionManager field_194223_a;
        private final ICommandSender field_194224_b;
        private final FunctionObject.Entry field_194225_c;

        public QueuedCommand(FunctionManager p_i47603_1_, ICommandSender p_i47603_2_, FunctionObject.Entry p_i47603_3_)
        {
            this.field_194223_a = p_i47603_1_;
            this.field_194224_b = p_i47603_2_;
            this.field_194225_c = p_i47603_3_;
        }

        public void func_194222_a(ArrayDeque<FunctionManager.QueuedCommand> p_194222_1_, int p_194222_2_)
        {
            this.field_194225_c.func_194145_a(this.field_194223_a, this.field_194224_b, p_194222_1_, p_194222_2_);
        }

        public String toString()
        {
            return this.field_194225_c.toString();
        }
    }
}
