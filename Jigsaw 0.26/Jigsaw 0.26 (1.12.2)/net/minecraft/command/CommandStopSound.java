package net.minecraft.command;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

public class CommandStopSound extends CommandBase
{
    /**
     * Gets the name of the command
     */
    public String getCommandName()
    {
        return "stopsound";
    }

    /**
     * Return the required permission level for this command.
     */
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    /**
     * Gets the usage string for the command.
     */
    public String getCommandUsage(ICommandSender sender)
    {
        return "commands.stopsound.usage";
    }

    /**
     * Callback for when the command is executed
     */
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length >= 1 && args.length <= 3)
        {
            int i = 0;
            EntityPlayerMP entityplayermp = getPlayer(server, sender, args[i++]);
            String s = "";
            String s1 = "";

            if (args.length >= 2)
            {
                String s2 = args[i++];
                SoundCategory soundcategory = SoundCategory.getByName(s2);

                if (soundcategory == null)
                {
                    throw new CommandException("commands.stopsound.unknownSoundSource", new Object[] {s2});
                }

                s = soundcategory.getName();
            }

            if (args.length == 3)
            {
                s1 = args[i++];
            }

            PacketBuffer packetbuffer = new PacketBuffer(Unpooled.buffer());
            packetbuffer.writeString(s);
            packetbuffer.writeString(s1);
            entityplayermp.connection.sendPacket(new SPacketCustomPayload("MC|StopSound", packetbuffer));

            if (s.isEmpty() && s1.isEmpty())
            {
                notifyCommandListener(sender, this, "commands.stopsound.success.all", new Object[] {entityplayermp.getName()});
            }
            else if (s1.isEmpty())
            {
                notifyCommandListener(sender, this, "commands.stopsound.success.soundSource", new Object[] {s, entityplayermp.getName()});
            }
            else
            {
                notifyCommandListener(sender, this, "commands.stopsound.success.individualSound", new Object[] {s1, s, entityplayermp.getName()});
            }
        }
        else
        {
            throw new WrongUsageException(this.getCommandUsage(sender), new Object[0]);
        }
    }

    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, server.getAllUsernames());
        }
        else if (args.length == 2)
        {
            return getListOfStringsMatchingLastWord(args, SoundCategory.getSoundCategoryNames());
        }
        else
        {
            return args.length == 3 ? getListOfStringsMatchingLastWord(args, SoundEvent.REGISTRY.getKeys()) : Collections.emptyList();
        }
    }

    /**
     * Return whether the specified command parameter index is a username parameter.
     */
    public boolean isUsernameIndex(String[] args, int index)
    {
        return index == 0;
    }
}
