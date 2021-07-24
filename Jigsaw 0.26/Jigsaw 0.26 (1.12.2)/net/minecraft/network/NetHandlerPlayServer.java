package net.minecraft.network;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.util.concurrent.Futures;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IJumpingMount;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartCommandBlock;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerBeacon;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemWritableBook;
import net.minecraft.item.ItemWrittenBook;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.client.CPacketClientSettings;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.network.play.client.CPacketCloseWindow;
import net.minecraft.network.play.client.CPacketConfirmTeleport;
import net.minecraft.network.play.client.CPacketConfirmTransaction;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.client.CPacketEnchantItem;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketInput;
import net.minecraft.network.play.client.CPacketKeepAlive;
import net.minecraft.network.play.client.CPacketPlaceRecipe;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerAbilities;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketRecipeInfo;
import net.minecraft.network.play.client.CPacketResourcePackStatus;
import net.minecraft.network.play.client.CPacketSeenAdvancements;
import net.minecraft.network.play.client.CPacketSpectate;
import net.minecraft.network.play.client.CPacketSteerBoat;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.network.play.client.CPacketUpdateSign;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraft.network.play.server.SPacketMoveVehicle;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketTabComplete;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.ServerRecipeBookHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

public class NetHandlerPlayServer implements INetHandlerPlayServer, ITickable
{
    private static final Logger LOGGER = LogManager.getLogger();
    public final NetworkManager netManager;
    private final MinecraftServer serverController;
    public EntityPlayerMP playerEntity;
    private int networkTickCount;
    private long field_194402_f;
    private boolean field_194403_g;
    private long field_194404_h;

    /**
     * Incremented by 20 each time a user sends a chat message, decreased by one every tick. Non-ops kicked when over
     * 200
     */
    private int chatSpamThresholdCount;
    private int itemDropThreshold;
    private final IntHashMap<Short> pendingTransactions = new IntHashMap<Short>();
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    private Entity lowestRiddenEnt;
    private double lowestRiddenX;
    private double lowestRiddenY;
    private double lowestRiddenZ;
    private double lowestRiddenX1;
    private double lowestRiddenY1;
    private double lowestRiddenZ1;
    private Vec3d targetPos;
    private int teleportId;
    private int lastPositionUpdate;
    private boolean floating;

    /**
     * Used to keep track of how the player is floating while gamerules should prevent that. Surpassing 80 ticks means
     * kick
     */
    private int floatingTickCount;
    private boolean vehicleFloating;

    /**
     * Used to keep track of how long the player is floating in a vehicle. Surpassing 80 means a kick
     */
    private int vehicleFloatingTickCount;
    private int movePacketCounter;
    private int lastMovePacketCounter;
    private ServerRecipeBookHelper field_194309_H = new ServerRecipeBookHelper();

    public NetHandlerPlayServer(MinecraftServer server, NetworkManager networkManagerIn, EntityPlayerMP playerIn)
    {
        this.serverController = server;
        this.netManager = networkManagerIn;
        networkManagerIn.setNetHandler(this);
        this.playerEntity = playerIn;
        playerIn.connection = this;
    }

    /**
     * Like the old updateEntity(), except more generic.
     */
    public void update()
    {
        this.captureCurrentPosition();
        this.playerEntity.onUpdateEntity();
        this.playerEntity.setPositionAndRotation(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.playerEntity.rotationYaw, this.playerEntity.rotationPitch);
        ++this.networkTickCount;
        this.lastMovePacketCounter = this.movePacketCounter;

        if (this.floating)
        {
            if (++this.floatingTickCount > 80)
            {
                LOGGER.warn("{} was kicked for floating too long!", (Object)this.playerEntity.getName());
                this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.flying", new Object[0]));
                return;
            }
        }
        else
        {
            this.floating = false;
            this.floatingTickCount = 0;
        }

        this.lowestRiddenEnt = this.playerEntity.getLowestRidingEntity();

        if (this.lowestRiddenEnt != this.playerEntity && this.lowestRiddenEnt.getControllingPassenger() == this.playerEntity)
        {
            this.lowestRiddenX = this.lowestRiddenEnt.posX;
            this.lowestRiddenY = this.lowestRiddenEnt.posY;
            this.lowestRiddenZ = this.lowestRiddenEnt.posZ;
            this.lowestRiddenX1 = this.lowestRiddenEnt.posX;
            this.lowestRiddenY1 = this.lowestRiddenEnt.posY;
            this.lowestRiddenZ1 = this.lowestRiddenEnt.posZ;

            if (this.vehicleFloating && this.playerEntity.getLowestRidingEntity().getControllingPassenger() == this.playerEntity)
            {
                if (++this.vehicleFloatingTickCount > 80)
                {
                    LOGGER.warn("{} was kicked for floating a vehicle too long!", (Object)this.playerEntity.getName());
                    this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.flying", new Object[0]));
                    return;
                }
            }
            else
            {
                this.vehicleFloating = false;
                this.vehicleFloatingTickCount = 0;
            }
        }
        else
        {
            this.lowestRiddenEnt = null;
            this.vehicleFloating = false;
            this.vehicleFloatingTickCount = 0;
        }

        this.serverController.theProfiler.startSection("keepAlive");
        long i = this.currentTimeMillis();

        if (i - this.field_194402_f >= 15000L)
        {
            if (this.field_194403_g)
            {
                this.func_194028_b(new TextComponentTranslation("disconnect.timeout", new Object[0]));
            }
            else
            {
                this.field_194403_g = true;
                this.field_194402_f = i;
                this.field_194404_h = i;
                this.sendPacket(new SPacketKeepAlive(this.field_194404_h));
            }
        }

        this.serverController.theProfiler.endSection();

        if (this.chatSpamThresholdCount > 0)
        {
            --this.chatSpamThresholdCount;
        }

        if (this.itemDropThreshold > 0)
        {
            --this.itemDropThreshold;
        }

        if (this.playerEntity.getLastActiveTime() > 0L && this.serverController.getMaxPlayerIdleMinutes() > 0 && MinecraftServer.getCurrentTimeMillis() - this.playerEntity.getLastActiveTime() > (long)(this.serverController.getMaxPlayerIdleMinutes() * 1000 * 60))
        {
            this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.idling", new Object[0]));
        }
    }

    private void captureCurrentPosition()
    {
        this.firstGoodX = this.playerEntity.posX;
        this.firstGoodY = this.playerEntity.posY;
        this.firstGoodZ = this.playerEntity.posZ;
        this.lastGoodX = this.playerEntity.posX;
        this.lastGoodY = this.playerEntity.posY;
        this.lastGoodZ = this.playerEntity.posZ;
    }

    public NetworkManager getNetworkManager()
    {
        return this.netManager;
    }

    public void func_194028_b(final ITextComponent p_194028_1_)
    {
        this.netManager.sendPacket(new SPacketDisconnect(p_194028_1_), new GenericFutureListener < Future <? super Void >> ()
        {
            public void operationComplete(Future <? super Void > p_operationComplete_1_) throws Exception
            {
                NetHandlerPlayServer.this.netManager.closeChannel(p_194028_1_);
            }
        });
        this.netManager.disableAutoRead();
        Futures.getUnchecked(this.serverController.addScheduledTask(new Runnable()
        {
            public void run()
            {
                NetHandlerPlayServer.this.netManager.checkDisconnected();
            }
        }));
    }

    /**
     * Processes player movement input. Includes walking, strafing, jumping, sneaking; excludes riding and toggling
     * flying/sprinting
     */
    public void processInput(CPacketInput packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.setEntityActionState(packetIn.getStrafeSpeed(), packetIn.func_192620_b(), packetIn.isJumping(), packetIn.isSneaking());
    }

    private static boolean isMovePlayerPacketInvalid(CPacketPlayer packetIn)
    {
        if (Doubles.isFinite(packetIn.getX(0.0D)) && Doubles.isFinite(packetIn.getY(0.0D)) && Doubles.isFinite(packetIn.getZ(0.0D)) && Floats.isFinite(packetIn.getPitch(0.0F)) && Floats.isFinite(packetIn.getYaw(0.0F)))
        {
            return Math.abs(packetIn.getX(0.0D)) > 3.0E7D || Math.abs(packetIn.getY(0.0D)) > 3.0E7D || Math.abs(packetIn.getZ(0.0D)) > 3.0E7D;
        }
        else
        {
            return true;
        }
    }

    private static boolean isMoveVehiclePacketInvalid(CPacketVehicleMove packetIn)
    {
        return !Doubles.isFinite(packetIn.getX()) || !Doubles.isFinite(packetIn.getY()) || !Doubles.isFinite(packetIn.getZ()) || !Floats.isFinite(packetIn.getPitch()) || !Floats.isFinite(packetIn.getYaw());
    }

    public void processVehicleMove(CPacketVehicleMove packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (isMoveVehiclePacketInvalid(packetIn))
        {
            this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.invalid_vehicle_movement", new Object[0]));
        }
        else
        {
            Entity entity = this.playerEntity.getLowestRidingEntity();

            if (entity != this.playerEntity && entity.getControllingPassenger() == this.playerEntity && entity == this.lowestRiddenEnt)
            {
                WorldServer worldserver = this.playerEntity.getServerWorld();
                double d0 = entity.posX;
                double d1 = entity.posY;
                double d2 = entity.posZ;
                double d3 = packetIn.getX();
                double d4 = packetIn.getY();
                double d5 = packetIn.getZ();
                float f = packetIn.getYaw();
                float f1 = packetIn.getPitch();
                double d6 = d3 - this.lowestRiddenX;
                double d7 = d4 - this.lowestRiddenY;
                double d8 = d5 - this.lowestRiddenZ;
                double d9 = entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ;
                double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                if (d10 - d9 > 100.0D && (!this.serverController.isSinglePlayer() || !this.serverController.getServerOwner().equals(entity.getName())))
                {
                    LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName(), this.playerEntity.getName(), Double.valueOf(d6), Double.valueOf(d7), Double.valueOf(d8));
                    this.netManager.sendPacket(new SPacketMoveVehicle(entity));
                    return;
                }

                boolean flag = worldserver.getCollisionBoxes(entity, entity.getEntityBoundingBox().contract(0.0625D)).isEmpty();
                d6 = d3 - this.lowestRiddenX1;
                d7 = d4 - this.lowestRiddenY1 - 1.0E-6D;
                d8 = d5 - this.lowestRiddenZ1;
                entity.moveEntity(MoverType.PLAYER, d6, d7, d8);
                double d11 = d7;
                d6 = d3 - entity.posX;
                d7 = d4 - entity.posY;

                if (d7 > -0.5D || d7 < 0.5D)
                {
                    d7 = 0.0D;
                }

                d8 = d5 - entity.posZ;
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag1 = false;

                if (d10 > 0.0625D)
                {
                    flag1 = true;
                    LOGGER.warn("{} moved wrongly!", (Object)entity.getName());
                }

                entity.setPositionAndRotation(d3, d4, d5, f, f1);
                boolean flag2 = worldserver.getCollisionBoxes(entity, entity.getEntityBoundingBox().contract(0.0625D)).isEmpty();

                if (flag && (flag1 || !flag2))
                {
                    entity.setPositionAndRotation(d0, d1, d2, f, f1);
                    this.netManager.sendPacket(new SPacketMoveVehicle(entity));
                    return;
                }

                this.serverController.getPlayerList().serverUpdateMovingPlayer(this.playerEntity);
                this.playerEntity.addMovementStat(this.playerEntity.posX - d0, this.playerEntity.posY - d1, this.playerEntity.posZ - d2);
                this.vehicleFloating = d11 >= -0.03125D && !this.serverController.isFlightAllowed() && !worldserver.checkBlockCollision(entity.getEntityBoundingBox().expandXyz(0.0625D).addCoord(0.0D, -0.55D, 0.0D));
                this.lowestRiddenX1 = entity.posX;
                this.lowestRiddenY1 = entity.posY;
                this.lowestRiddenZ1 = entity.posZ;
            }
        }
    }

    public void processConfirmTeleport(CPacketConfirmTeleport packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (packetIn.getTeleportId() == this.teleportId)
        {
            this.playerEntity.setPositionAndRotation(this.targetPos.x, this.targetPos.y, this.targetPos.z, this.playerEntity.rotationYaw, this.playerEntity.rotationPitch);

            if (this.playerEntity.isInvulnerableDimensionChange())
            {
                this.lastGoodX = this.targetPos.x;
                this.lastGoodY = this.targetPos.y;
                this.lastGoodZ = this.targetPos.z;
                this.playerEntity.clearInvulnerableDimensionChange();
            }

            this.targetPos = null;
        }
    }

    public void func_191984_a(CPacketRecipeInfo p_191984_1_)
    {
        PacketThreadUtil.checkThreadAndEnqueue(p_191984_1_, this, this.playerEntity.getServerWorld());

        if (p_191984_1_.func_194156_a() == CPacketRecipeInfo.Purpose.SHOWN)
        {
            this.playerEntity.func_192037_E().func_194074_f(p_191984_1_.func_193648_b());
        }
        else if (p_191984_1_.func_194156_a() == CPacketRecipeInfo.Purpose.SETTINGS)
        {
            this.playerEntity.func_192037_E().func_192813_a(p_191984_1_.func_192624_c());
            this.playerEntity.func_192037_E().func_192810_b(p_191984_1_.func_192625_d());
        }
    }

    public void func_194027_a(CPacketSeenAdvancements p_194027_1_)
    {
        PacketThreadUtil.checkThreadAndEnqueue(p_194027_1_, this, this.playerEntity.getServerWorld());

        if (p_194027_1_.func_194162_b() == CPacketSeenAdvancements.Action.OPENED_TAB)
        {
            ResourceLocation resourcelocation = p_194027_1_.func_194165_c();
            Advancement advancement = this.serverController.func_191949_aK().func_192778_a(resourcelocation);

            if (advancement != null)
            {
                this.playerEntity.func_192039_O().func_194220_a(advancement);
            }
        }
    }

    /**
     * Processes clients perspective on player positioning and/or orientation
     */
    public void processPlayer(CPacketPlayer packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (isMovePlayerPacketInvalid(packetIn))
        {
            this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.invalid_player_movement", new Object[0]));
        }
        else
        {
            WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);

            if (!this.playerEntity.playerConqueredTheEnd)
            {
                if (this.networkTickCount == 0)
                {
                    this.captureCurrentPosition();
                }

                if (this.targetPos != null)
                {
                    if (this.networkTickCount - this.lastPositionUpdate > 20)
                    {
                        this.lastPositionUpdate = this.networkTickCount;
                        this.setPlayerLocation(this.targetPos.x, this.targetPos.y, this.targetPos.z, this.playerEntity.rotationYaw, this.playerEntity.rotationPitch);
                    }
                }
                else
                {
                    this.lastPositionUpdate = this.networkTickCount;

                    if (this.playerEntity.isRiding())
                    {
                        this.playerEntity.setPositionAndRotation(this.playerEntity.posX, this.playerEntity.posY, this.playerEntity.posZ, packetIn.getYaw(this.playerEntity.rotationYaw), packetIn.getPitch(this.playerEntity.rotationPitch));
                        this.serverController.getPlayerList().serverUpdateMovingPlayer(this.playerEntity);
                    }
                    else
                    {
                        double d0 = this.playerEntity.posX;
                        double d1 = this.playerEntity.posY;
                        double d2 = this.playerEntity.posZ;
                        double d3 = this.playerEntity.posY;
                        double d4 = packetIn.getX(this.playerEntity.posX);
                        double d5 = packetIn.getY(this.playerEntity.posY);
                        double d6 = packetIn.getZ(this.playerEntity.posZ);
                        float f = packetIn.getYaw(this.playerEntity.rotationYaw);
                        float f1 = packetIn.getPitch(this.playerEntity.rotationPitch);
                        double d7 = d4 - this.firstGoodX;
                        double d8 = d5 - this.firstGoodY;
                        double d9 = d6 - this.firstGoodZ;
                        double d10 = this.playerEntity.motionX * this.playerEntity.motionX + this.playerEntity.motionY * this.playerEntity.motionY + this.playerEntity.motionZ * this.playerEntity.motionZ;
                        double d11 = d7 * d7 + d8 * d8 + d9 * d9;

                        if (this.playerEntity.isPlayerSleeping())
                        {
                            if (d11 > 1.0D)
                            {
                                this.setPlayerLocation(this.playerEntity.posX, this.playerEntity.posY, this.playerEntity.posZ, packetIn.getYaw(this.playerEntity.rotationYaw), packetIn.getPitch(this.playerEntity.rotationPitch));
                            }
                        }
                        else
                        {
                            ++this.movePacketCounter;
                            int i = this.movePacketCounter - this.lastMovePacketCounter;

                            if (i > 5)
                            {
                                LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.playerEntity.getName(), Integer.valueOf(i));
                                i = 1;
                            }

                            if (!this.playerEntity.isInvulnerableDimensionChange() && (!this.playerEntity.getServerWorld().getGameRules().getBoolean("disableElytraMovementCheck") || !this.playerEntity.isElytraFlying()))
                            {
                                float f2 = this.playerEntity.isElytraFlying() ? 300.0F : 100.0F;

                                if (d11 - d10 > (double)(f2 * (float)i) && (!this.serverController.isSinglePlayer() || !this.serverController.getServerOwner().equals(this.playerEntity.getName())))
                                {
                                    LOGGER.warn("{} moved too quickly! {},{},{}", this.playerEntity.getName(), Double.valueOf(d7), Double.valueOf(d8), Double.valueOf(d9));
                                    this.setPlayerLocation(this.playerEntity.posX, this.playerEntity.posY, this.playerEntity.posZ, this.playerEntity.rotationYaw, this.playerEntity.rotationPitch);
                                    return;
                                }
                            }

                            boolean flag2 = worldserver.getCollisionBoxes(this.playerEntity, this.playerEntity.getEntityBoundingBox().contract(0.0625D)).isEmpty();
                            d7 = d4 - this.lastGoodX;
                            d8 = d5 - this.lastGoodY;
                            d9 = d6 - this.lastGoodZ;

                            if (this.playerEntity.onGround && !packetIn.isOnGround() && d8 > 0.0D)
                            {
                                this.playerEntity.jump();
                            }

                            this.playerEntity.moveEntity(MoverType.PLAYER, d7, d8, d9);
                            this.playerEntity.onGround = packetIn.isOnGround();
                            double d12 = d8;
                            d7 = d4 - this.playerEntity.posX;
                            d8 = d5 - this.playerEntity.posY;

                            if (d8 > -0.5D || d8 < 0.5D)
                            {
                                d8 = 0.0D;
                            }

                            d9 = d6 - this.playerEntity.posZ;
                            d11 = d7 * d7 + d8 * d8 + d9 * d9;
                            boolean flag = false;

                            if (!this.playerEntity.isInvulnerableDimensionChange() && d11 > 0.0625D && !this.playerEntity.isPlayerSleeping() && !this.playerEntity.interactionManager.isCreative() && this.playerEntity.interactionManager.getGameType() != GameType.SPECTATOR)
                            {
                                flag = true;
                                LOGGER.warn("{} moved wrongly!", (Object)this.playerEntity.getName());
                            }

                            this.playerEntity.setPositionAndRotation(d4, d5, d6, f, f1);
                            this.playerEntity.addMovementStat(this.playerEntity.posX - d0, this.playerEntity.posY - d1, this.playerEntity.posZ - d2);

                            if (!this.playerEntity.noClip && !this.playerEntity.isPlayerSleeping())
                            {
                                boolean flag1 = worldserver.getCollisionBoxes(this.playerEntity, this.playerEntity.getEntityBoundingBox().contract(0.0625D)).isEmpty();

                                if (flag2 && (flag || !flag1))
                                {
                                    this.setPlayerLocation(d0, d1, d2, f, f1);
                                    return;
                                }
                            }

                            this.floating = d12 >= -0.03125D;
                            this.floating &= !this.serverController.isFlightAllowed() && !this.playerEntity.capabilities.allowFlying;
                            this.floating &= !this.playerEntity.isPotionActive(MobEffects.LEVITATION) && !this.playerEntity.isElytraFlying() && !worldserver.checkBlockCollision(this.playerEntity.getEntityBoundingBox().expandXyz(0.0625D).addCoord(0.0D, -0.55D, 0.0D));
                            this.playerEntity.onGround = packetIn.isOnGround();
                            this.serverController.getPlayerList().serverUpdateMovingPlayer(this.playerEntity);
                            this.playerEntity.handleFalling(this.playerEntity.posY - d3, packetIn.isOnGround());
                            this.lastGoodX = this.playerEntity.posX;
                            this.lastGoodY = this.playerEntity.posY;
                            this.lastGoodZ = this.playerEntity.posZ;
                        }
                    }
                }
            }
        }
    }

    public void setPlayerLocation(double x, double y, double z, float yaw, float pitch)
    {
        this.setPlayerLocation(x, y, z, yaw, pitch, Collections.emptySet());
    }

    public void setPlayerLocation(double x, double y, double z, float yaw, float pitch, Set<SPacketPlayerPosLook.EnumFlags> relativeSet)
    {
        double d0 = relativeSet.contains(SPacketPlayerPosLook.EnumFlags.X) ? this.playerEntity.posX : 0.0D;
        double d1 = relativeSet.contains(SPacketPlayerPosLook.EnumFlags.Y) ? this.playerEntity.posY : 0.0D;
        double d2 = relativeSet.contains(SPacketPlayerPosLook.EnumFlags.Z) ? this.playerEntity.posZ : 0.0D;
        this.targetPos = new Vec3d(x + d0, y + d1, z + d2);
        float f = yaw;
        float f1 = pitch;

        if (relativeSet.contains(SPacketPlayerPosLook.EnumFlags.Y_ROT))
        {
            f = yaw + this.playerEntity.rotationYaw;
        }

        if (relativeSet.contains(SPacketPlayerPosLook.EnumFlags.X_ROT))
        {
            f1 = pitch + this.playerEntity.rotationPitch;
        }

        if (++this.teleportId == Integer.MAX_VALUE)
        {
            this.teleportId = 0;
        }

        this.lastPositionUpdate = this.networkTickCount;
        this.playerEntity.setPositionAndRotation(this.targetPos.x, this.targetPos.y, this.targetPos.z, f, f1);
        this.playerEntity.connection.sendPacket(new SPacketPlayerPosLook(x, y, z, yaw, pitch, relativeSet, this.teleportId));
    }

    /**
     * Processes the player initiating/stopping digging on a particular spot, as well as a player dropping items?. (0:
     * initiated, 1: reinitiated, 2? , 3-4 drop item (respectively without or with player control), 5: stopped; x,y,z,
     * side clicked on;)
     */
    public void processPlayerDigging(CPacketPlayerDigging packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);
        BlockPos blockpos = packetIn.getPosition();
        this.playerEntity.markPlayerActive();

        switch (packetIn.getAction())
        {
            case SWAP_HELD_ITEMS:
                if (!this.playerEntity.isSpectator())
                {
                    ItemStack itemstack = this.playerEntity.getHeldItem(EnumHand.OFF_HAND);
                    this.playerEntity.setHeldItem(EnumHand.OFF_HAND, this.playerEntity.getHeldItem(EnumHand.MAIN_HAND));
                    this.playerEntity.setHeldItem(EnumHand.MAIN_HAND, itemstack);
                }

                return;

            case DROP_ITEM:
                if (!this.playerEntity.isSpectator())
                {
                    this.playerEntity.dropItem(false);
                }

                return;

            case DROP_ALL_ITEMS:
                if (!this.playerEntity.isSpectator())
                {
                    this.playerEntity.dropItem(true);
                }

                return;

            case RELEASE_USE_ITEM:
                this.playerEntity.stopActiveHand();
                return;

            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                double d0 = this.playerEntity.posX - ((double)blockpos.getX() + 0.5D);
                double d1 = this.playerEntity.posY - ((double)blockpos.getY() + 0.5D) + 1.5D;
                double d2 = this.playerEntity.posZ - ((double)blockpos.getZ() + 0.5D);
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                if (d3 > 36.0D)
                {
                    return;
                }
                else if (blockpos.getY() >= this.serverController.getBuildLimit())
                {
                    return;
                }
                else
                {
                    if (packetIn.getAction() == CPacketPlayerDigging.Action.START_DESTROY_BLOCK)
                    {
                        if (!this.serverController.isBlockProtected(worldserver, blockpos, this.playerEntity) && worldserver.getWorldBorder().contains(blockpos))
                        {
                            this.playerEntity.interactionManager.onBlockClicked(blockpos, packetIn.getFacing());
                        }
                        else
                        {
                            this.playerEntity.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
                        }
                    }
                    else
                    {
                        if (packetIn.getAction() == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK)
                        {
                            this.playerEntity.interactionManager.blockRemoving(blockpos);
                        }
                        else if (packetIn.getAction() == CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK)
                        {
                            this.playerEntity.interactionManager.cancelDestroyingBlock();
                        }

                        if (worldserver.getBlockState(blockpos).getMaterial() != Material.AIR)
                        {
                            this.playerEntity.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
                        }
                    }

                    return;
                }

            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    public void processRightClickBlock(CPacketPlayerTryUseItemOnBlock packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);
        EnumHand enumhand = packetIn.getHand();
        ItemStack itemstack = this.playerEntity.getHeldItem(enumhand);
        BlockPos blockpos = packetIn.getPos();
        EnumFacing enumfacing = packetIn.getDirection();
        this.playerEntity.markPlayerActive();

        if (blockpos.getY() < this.serverController.getBuildLimit() - 1 || enumfacing != EnumFacing.UP && blockpos.getY() < this.serverController.getBuildLimit())
        {
            if (this.targetPos == null && this.playerEntity.getDistanceSq((double)blockpos.getX() + 0.5D, (double)blockpos.getY() + 0.5D, (double)blockpos.getZ() + 0.5D) < 64.0D && !this.serverController.isBlockProtected(worldserver, blockpos, this.playerEntity) && worldserver.getWorldBorder().contains(blockpos))
            {
                this.playerEntity.interactionManager.processRightClickBlock(this.playerEntity, worldserver, itemstack, enumhand, blockpos, enumfacing, packetIn.getFacingX(), packetIn.getFacingY(), packetIn.getFacingZ());
            }
        }
        else
        {
            TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("build.tooHigh", new Object[] {this.serverController.getBuildLimit()});
            textcomponenttranslation.getStyle().setColor(TextFormatting.RED);
            this.playerEntity.connection.sendPacket(new SPacketChat(textcomponenttranslation, ChatType.GAME_INFO));
        }

        this.playerEntity.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos));
        this.playerEntity.connection.sendPacket(new SPacketBlockChange(worldserver, blockpos.offset(enumfacing)));
    }

    /**
     * Processes block placement and block activation (anvil, furnace, etc.)
     */
    public void processPlayerBlockPlacement(CPacketPlayerTryUseItem packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);
        EnumHand enumhand = packetIn.getHand();
        ItemStack itemstack = this.playerEntity.getHeldItem(enumhand);
        this.playerEntity.markPlayerActive();

        if (!itemstack.func_190926_b())
        {
            this.playerEntity.interactionManager.processRightClick(this.playerEntity, worldserver, itemstack, enumhand);
        }
    }

    public void handleSpectate(CPacketSpectate packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (this.playerEntity.isSpectator())
        {
            Entity entity = null;

            for (WorldServer worldserver : this.serverController.worldServers)
            {
                if (worldserver != null)
                {
                    entity = packetIn.getEntity(worldserver);

                    if (entity != null)
                    {
                        break;
                    }
                }
            }

            if (entity != null)
            {
                this.playerEntity.setSpectatingEntity(this.playerEntity);
                this.playerEntity.dismountRidingEntity();

                if (entity.world == this.playerEntity.world)
                {
                    this.playerEntity.setPositionAndUpdate(entity.posX, entity.posY, entity.posZ);
                }
                else
                {
                    WorldServer worldserver1 = this.playerEntity.getServerWorld();
                    WorldServer worldserver2 = (WorldServer)entity.world;
                    this.playerEntity.dimension = entity.dimension;
                    this.sendPacket(new SPacketRespawn(this.playerEntity.dimension, worldserver1.getDifficulty(), worldserver1.getWorldInfo().getTerrainType(), this.playerEntity.interactionManager.getGameType()));
                    this.serverController.getPlayerList().updatePermissionLevel(this.playerEntity);
                    worldserver1.removeEntityDangerously(this.playerEntity);
                    this.playerEntity.isDead = false;
                    this.playerEntity.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);

                    if (this.playerEntity.isEntityAlive())
                    {
                        worldserver1.updateEntityWithOptionalForce(this.playerEntity, false);
                        worldserver2.spawnEntityInWorld(this.playerEntity);
                        worldserver2.updateEntityWithOptionalForce(this.playerEntity, false);
                    }

                    this.playerEntity.setWorld(worldserver2);
                    this.serverController.getPlayerList().preparePlayer(this.playerEntity, worldserver1);
                    this.playerEntity.setPositionAndUpdate(entity.posX, entity.posY, entity.posZ);
                    this.playerEntity.interactionManager.setWorld(worldserver2);
                    this.serverController.getPlayerList().updateTimeAndWeatherForPlayer(this.playerEntity, worldserver2);
                    this.serverController.getPlayerList().syncPlayerInventory(this.playerEntity);
                }
            }
        }
    }

    public void handleResourcePackStatus(CPacketResourcePackStatus packetIn)
    {
    }

    public void processSteerBoat(CPacketSteerBoat packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        Entity entity = this.playerEntity.getRidingEntity();

        if (entity instanceof EntityBoat)
        {
            ((EntityBoat)entity).setPaddleState(packetIn.getLeft(), packetIn.getRight());
        }
    }

    /**
     * Invoked when disconnecting, the parameter is a ChatComponent describing the reason for termination
     */
    public void onDisconnect(ITextComponent reason)
    {
        LOGGER.info("{} lost connection: {}", this.playerEntity.getName(), reason.getUnformattedText());
        this.serverController.refreshStatusNextTick();
        TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("multiplayer.player.left", new Object[] {this.playerEntity.getDisplayName()});
        textcomponenttranslation.getStyle().setColor(TextFormatting.YELLOW);
        this.serverController.getPlayerList().sendChatMsg(textcomponenttranslation);
        this.playerEntity.mountEntityAndWakeUp();
        this.serverController.getPlayerList().playerLoggedOut(this.playerEntity);

        if (this.serverController.isSinglePlayer() && this.playerEntity.getName().equals(this.serverController.getServerOwner()))
        {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.serverController.initiateShutdown();
        }
    }

    public void sendPacket(final Packet<?> packetIn)
    {
        if (packetIn instanceof SPacketChat)
        {
            SPacketChat spacketchat = (SPacketChat)packetIn;
            EntityPlayer.EnumChatVisibility entityplayer$enumchatvisibility = this.playerEntity.getChatVisibility();

            if (entityplayer$enumchatvisibility == EntityPlayer.EnumChatVisibility.HIDDEN && spacketchat.func_192590_c() != ChatType.GAME_INFO)
            {
                return;
            }

            if (entityplayer$enumchatvisibility == EntityPlayer.EnumChatVisibility.SYSTEM && !spacketchat.isSystem())
            {
                return;
            }
        }

        try
        {
            this.netManager.sendPacket(packetIn);
        }
        catch (Throwable throwable)
        {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Packet being sent");
            crashreportcategory.setDetail("Packet class", new ICrashReportDetail<String>()
            {
                public String call() throws Exception
                {
                    return packetIn.getClass().getCanonicalName();
                }
            });
            throw new ReportedException(crashreport);
        }
    }

    /**
     * Updates which quickbar slot is selected
     */
    public void processHeldItemChange(CPacketHeldItemChange packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (packetIn.getSlotId() >= 0 && packetIn.getSlotId() < InventoryPlayer.getHotbarSize())
        {
            this.playerEntity.inventory.currentItem = packetIn.getSlotId();
            this.playerEntity.markPlayerActive();
        }
        else
        {
            LOGGER.warn("{} tried to set an invalid carried item", (Object)this.playerEntity.getName());
        }
    }

    /**
     * Process chat messages (broadcast back to clients) and commands (executes)
     */
    public void processChatMessage(CPacketChatMessage packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (this.playerEntity.getChatVisibility() == EntityPlayer.EnumChatVisibility.HIDDEN)
        {
            TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("chat.cannotSend", new Object[0]);
            textcomponenttranslation.getStyle().setColor(TextFormatting.RED);
            this.sendPacket(new SPacketChat(textcomponenttranslation));
        }
        else
        {
            this.playerEntity.markPlayerActive();
            String s = packetIn.getMessage();
            s = StringUtils.normalizeSpace(s);

            for (int i = 0; i < s.length(); ++i)
            {
                if (!ChatAllowedCharacters.isAllowedCharacter(s.charAt(i)))
                {
                    this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.illegal_characters", new Object[0]));
                    return;
                }
            }

            if (s.startsWith("/"))
            {
                this.handleSlashCommand(s);
            }
            else
            {
                ITextComponent itextcomponent = new TextComponentTranslation("chat.type.text", new Object[] {this.playerEntity.getDisplayName(), s});
                this.serverController.getPlayerList().sendChatMsgImpl(itextcomponent, false);
            }

            this.chatSpamThresholdCount += 20;

            if (this.chatSpamThresholdCount > 200 && !this.serverController.getPlayerList().canSendCommands(this.playerEntity.getGameProfile()))
            {
                this.func_194028_b(new TextComponentTranslation("disconnect.spam", new Object[0]));
            }
        }
    }

    /**
     * Handle commands that start with a /
     */
    private void handleSlashCommand(String command)
    {
        this.serverController.getCommandManager().executeCommand(this.playerEntity, command);
    }

    public void handleAnimation(CPacketAnimation packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();
        this.playerEntity.swingArm(packetIn.getHand());
    }

    /**
     * Processes a range of action-types: sneaking, sprinting, waking from sleep, opening the inventory or setting jump
     * height of the horse the player is riding
     */
    public void processEntityAction(CPacketEntityAction packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();

        switch (packetIn.getAction())
        {
            case START_SNEAKING:
                this.playerEntity.setSneaking(true);
                break;

            case STOP_SNEAKING:
                this.playerEntity.setSneaking(false);
                break;

            case START_SPRINTING:
                this.playerEntity.setSprinting(true);
                break;

            case STOP_SPRINTING:
                this.playerEntity.setSprinting(false);
                break;

            case STOP_SLEEPING:
                if (this.playerEntity.isPlayerSleeping())
                {
                    this.playerEntity.wakeUpPlayer(false, true, true);
                    this.targetPos = new Vec3d(this.playerEntity.posX, this.playerEntity.posY, this.playerEntity.posZ);
                }

                break;

            case START_RIDING_JUMP:
                if (this.playerEntity.getRidingEntity() instanceof IJumpingMount)
                {
                    IJumpingMount ijumpingmount1 = (IJumpingMount)this.playerEntity.getRidingEntity();
                    int i = packetIn.getAuxData();

                    if (ijumpingmount1.canJump() && i > 0)
                    {
                        ijumpingmount1.handleStartJump(i);
                    }
                }

                break;

            case STOP_RIDING_JUMP:
                if (this.playerEntity.getRidingEntity() instanceof IJumpingMount)
                {
                    IJumpingMount ijumpingmount = (IJumpingMount)this.playerEntity.getRidingEntity();
                    ijumpingmount.handleStopJump();
                }

                break;

            case OPEN_INVENTORY:
                if (this.playerEntity.getRidingEntity() instanceof AbstractHorse)
                {
                    ((AbstractHorse)this.playerEntity.getRidingEntity()).openGUI(this.playerEntity);
                }

                break;

            case START_FALL_FLYING:
                if (!this.playerEntity.onGround && this.playerEntity.motionY < 0.0D && !this.playerEntity.isElytraFlying() && !this.playerEntity.isInWater())
                {
                    ItemStack itemstack = this.playerEntity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);

                    if (itemstack.getItem() == Items.ELYTRA && ItemElytra.isBroken(itemstack))
                    {
                        this.playerEntity.setElytraFlying();
                    }
                }
                else
                {
                    this.playerEntity.clearElytraFlying();
                }

                break;

            default:
                throw new IllegalArgumentException("Invalid client command!");
        }
    }

    /**
     * Processes interactions ((un)leashing, opening command block GUI) and attacks on an entity with players currently
     * equipped item
     */
    public void processUseEntity(CPacketUseEntity packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);
        Entity entity = packetIn.getEntityFromWorld(worldserver);
        this.playerEntity.markPlayerActive();

        if (entity != null)
        {
            boolean flag = this.playerEntity.canEntityBeSeen(entity);
            double d0 = 36.0D;

            if (!flag)
            {
                d0 = 9.0D;
            }

            if (this.playerEntity.getDistanceSqToEntity(entity) < d0)
            {
                if (packetIn.getAction() == CPacketUseEntity.Action.INTERACT)
                {
                    EnumHand enumhand = packetIn.getHand();
                    this.playerEntity.func_190775_a(entity, enumhand);
                }
                else if (packetIn.getAction() == CPacketUseEntity.Action.INTERACT_AT)
                {
                    EnumHand enumhand1 = packetIn.getHand();
                    entity.applyPlayerInteraction(this.playerEntity, packetIn.getHitVec(), enumhand1);
                }
                else if (packetIn.getAction() == CPacketUseEntity.Action.ATTACK)
                {
                    if (entity instanceof EntityItem || entity instanceof EntityXPOrb || entity instanceof EntityArrow || entity == this.playerEntity)
                    {
                        this.func_194028_b(new TextComponentTranslation("multiplayer.disconnect.invalid_entity_attacked", new Object[0]));
                        this.serverController.logWarning("Player " + this.playerEntity.getName() + " tried to attack an invalid entity");
                        return;
                    }

                    this.playerEntity.attackTargetEntityWithCurrentItem(entity);
                }
            }
        }
    }

    /**
     * Processes the client status updates: respawn attempt from player, opening statistics or achievements, or
     * acquiring 'open inventory' achievement
     */
    public void processClientStatus(CPacketClientStatus packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();
        CPacketClientStatus.State cpacketclientstatus$state = packetIn.getStatus();

        switch (cpacketclientstatus$state)
        {
            case PERFORM_RESPAWN:
                if (this.playerEntity.playerConqueredTheEnd)
                {
                    this.playerEntity.playerConqueredTheEnd = false;
                    this.playerEntity = this.serverController.getPlayerList().recreatePlayerEntity(this.playerEntity, 0, true);
                    CriteriaTriggers.field_193134_u.func_193143_a(this.playerEntity, DimensionType.THE_END, DimensionType.OVERWORLD);
                }
                else
                {
                    if (this.playerEntity.getHealth() > 0.0F)
                    {
                        return;
                    }

                    this.playerEntity = this.serverController.getPlayerList().recreatePlayerEntity(this.playerEntity, 0, false);

                    if (this.serverController.isHardcore())
                    {
                        this.playerEntity.setGameType(GameType.SPECTATOR);
                        this.playerEntity.getServerWorld().getGameRules().setOrCreateGameRule("spectatorsGenerateChunks", "false");
                    }
                }

                break;

            case REQUEST_STATS:
                this.playerEntity.getStatFile().sendStats(this.playerEntity);
        }
    }

    /**
     * Processes the client closing windows (container)
     */
    public void processCloseWindow(CPacketCloseWindow packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.closeContainer();
    }

    /**
     * Executes a container/inventory slot manipulation as indicated by the packet. Sends the serverside result if they
     * didn't match the indicated result and prevents further manipulation by the player until he confirms that it has
     * the same open container/inventory
     */
    public void processClickWindow(CPacketClickWindow packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();

        if (this.playerEntity.openContainer.windowId == packetIn.getWindowId() && this.playerEntity.openContainer.getCanCraft(this.playerEntity))
        {
            if (this.playerEntity.isSpectator())
            {
                NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>func_191196_a();

                for (int i = 0; i < this.playerEntity.openContainer.inventorySlots.size(); ++i)
                {
                    nonnulllist.add(((Slot)this.playerEntity.openContainer.inventorySlots.get(i)).getStack());
                }

                this.playerEntity.updateCraftingInventory(this.playerEntity.openContainer, nonnulllist);
            }
            else
            {
                ItemStack itemstack2 = this.playerEntity.openContainer.slotClick(packetIn.getSlotId(), packetIn.getUsedButton(), packetIn.getClickType(), this.playerEntity);

                if (ItemStack.areItemStacksEqual(packetIn.getClickedItem(), itemstack2))
                {
                    this.playerEntity.connection.sendPacket(new SPacketConfirmTransaction(packetIn.getWindowId(), packetIn.getActionNumber(), true));
                    this.playerEntity.isChangingQuantityOnly = true;
                    this.playerEntity.openContainer.detectAndSendChanges();
                    this.playerEntity.updateHeldItem();
                    this.playerEntity.isChangingQuantityOnly = false;
                }
                else
                {
                    this.pendingTransactions.addKey(this.playerEntity.openContainer.windowId, Short.valueOf(packetIn.getActionNumber()));
                    this.playerEntity.connection.sendPacket(new SPacketConfirmTransaction(packetIn.getWindowId(), packetIn.getActionNumber(), false));
                    this.playerEntity.openContainer.setCanCraft(this.playerEntity, false);
                    NonNullList<ItemStack> nonnulllist1 = NonNullList.<ItemStack>func_191196_a();

                    for (int j = 0; j < this.playerEntity.openContainer.inventorySlots.size(); ++j)
                    {
                        ItemStack itemstack = ((Slot)this.playerEntity.openContainer.inventorySlots.get(j)).getStack();
                        ItemStack itemstack1 = itemstack.func_190926_b() ? ItemStack.field_190927_a : itemstack;
                        nonnulllist1.add(itemstack1);
                    }

                    this.playerEntity.updateCraftingInventory(this.playerEntity.openContainer, nonnulllist1);
                }
            }
        }
    }

    public void func_194308_a(CPacketPlaceRecipe p_194308_1_)
    {
        PacketThreadUtil.checkThreadAndEnqueue(p_194308_1_, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();

        if (!this.playerEntity.isSpectator() && this.playerEntity.openContainer.windowId == p_194308_1_.func_194318_a() && this.playerEntity.openContainer.getCanCraft(this.playerEntity))
        {
            this.field_194309_H.func_194327_a(this.playerEntity, p_194308_1_.func_194317_b(), p_194308_1_.func_194319_c());
        }
    }

    /**
     * Enchants the item identified by the packet given some convoluted conditions (matching window, which
     * should/shouldn't be in use?)
     */
    public void processEnchantItem(CPacketEnchantItem packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();

        if (this.playerEntity.openContainer.windowId == packetIn.getWindowId() && this.playerEntity.openContainer.getCanCraft(this.playerEntity) && !this.playerEntity.isSpectator())
        {
            this.playerEntity.openContainer.enchantItem(this.playerEntity, packetIn.getButton());
            this.playerEntity.openContainer.detectAndSendChanges();
        }
    }

    /**
     * Update the server with an ItemStack in a slot.
     */
    public void processCreativeInventoryAction(CPacketCreativeInventoryAction packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());

        if (this.playerEntity.interactionManager.isCreative())
        {
            boolean flag = packetIn.getSlotId() < 0;
            ItemStack itemstack = packetIn.getStack();

            if (!itemstack.func_190926_b() && itemstack.hasTagCompound() && itemstack.getTagCompound().hasKey("BlockEntityTag", 10))
            {
                NBTTagCompound nbttagcompound = itemstack.getTagCompound().getCompoundTag("BlockEntityTag");

                if (nbttagcompound.hasKey("x") && nbttagcompound.hasKey("y") && nbttagcompound.hasKey("z"))
                {
                    BlockPos blockpos = new BlockPos(nbttagcompound.getInteger("x"), nbttagcompound.getInteger("y"), nbttagcompound.getInteger("z"));
                    TileEntity tileentity = this.playerEntity.world.getTileEntity(blockpos);

                    if (tileentity != null)
                    {
                        NBTTagCompound nbttagcompound1 = tileentity.writeToNBT(new NBTTagCompound());
                        nbttagcompound1.removeTag("x");
                        nbttagcompound1.removeTag("y");
                        nbttagcompound1.removeTag("z");
                        itemstack.setTagInfo("BlockEntityTag", nbttagcompound1);
                    }
                }
            }

            boolean flag1 = packetIn.getSlotId() >= 1 && packetIn.getSlotId() <= 45;
            boolean flag2 = itemstack.func_190926_b() || itemstack.getMetadata() >= 0 && itemstack.func_190916_E() <= 64 && !itemstack.func_190926_b();

            if (flag1 && flag2)
            {
                if (itemstack.func_190926_b())
                {
                    this.playerEntity.inventoryContainer.putStackInSlot(packetIn.getSlotId(), ItemStack.field_190927_a);
                }
                else
                {
                    this.playerEntity.inventoryContainer.putStackInSlot(packetIn.getSlotId(), itemstack);
                }

                this.playerEntity.inventoryContainer.setCanCraft(this.playerEntity, true);
            }
            else if (flag && flag2 && this.itemDropThreshold < 200)
            {
                this.itemDropThreshold += 20;
                EntityItem entityitem = this.playerEntity.dropItem(itemstack, true);

                if (entityitem != null)
                {
                    entityitem.setAgeToCreativeDespawnTime();
                }
            }
        }
    }

    /**
     * Received in response to the server requesting to confirm that the client-side open container matches the servers'
     * after a mismatched container-slot manipulation. It will unlock the player's ability to manipulate the container
     * contents
     */
    public void processConfirmTransaction(CPacketConfirmTransaction packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        Short oshort = this.pendingTransactions.lookup(this.playerEntity.openContainer.windowId);

        if (oshort != null && packetIn.getUid() == oshort.shortValue() && this.playerEntity.openContainer.windowId == packetIn.getWindowId() && !this.playerEntity.openContainer.getCanCraft(this.playerEntity) && !this.playerEntity.isSpectator())
        {
            this.playerEntity.openContainer.setCanCraft(this.playerEntity, true);
        }
    }

    public void processUpdateSign(CPacketUpdateSign packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.markPlayerActive();
        WorldServer worldserver = this.serverController.worldServerForDimension(this.playerEntity.dimension);
        BlockPos blockpos = packetIn.getPosition();

        if (worldserver.isBlockLoaded(blockpos))
        {
            IBlockState iblockstate = worldserver.getBlockState(blockpos);
            TileEntity tileentity = worldserver.getTileEntity(blockpos);

            if (!(tileentity instanceof TileEntitySign))
            {
                return;
            }

            TileEntitySign tileentitysign = (TileEntitySign)tileentity;

            if (!tileentitysign.getIsEditable() || tileentitysign.getPlayer() != this.playerEntity)
            {
                this.serverController.logWarning("Player " + this.playerEntity.getName() + " just tried to change non-editable sign");
                return;
            }

            String[] astring = packetIn.getLines();

            for (int i = 0; i < astring.length; ++i)
            {
                tileentitysign.signText[i] = new TextComponentString(TextFormatting.getTextWithoutFormattingCodes(astring[i]));
            }

            tileentitysign.markDirty();
            worldserver.notifyBlockUpdate(blockpos, iblockstate, iblockstate, 3);
        }
    }

    /**
     * Updates a players' ping statistics
     */
    public void processKeepAlive(CPacketKeepAlive packetIn)
    {
        if (this.field_194403_g && packetIn.getKey() == this.field_194404_h)
        {
            int i = (int)(this.currentTimeMillis() - this.field_194402_f);
            this.playerEntity.ping = (this.playerEntity.ping * 3 + i) / 4;
            this.field_194403_g = false;
        }
        else if (!this.playerEntity.getName().equals(this.serverController.getServerOwner()))
        {
            this.func_194028_b(new TextComponentTranslation("disconnect.timeout", new Object[0]));
        }
    }

    private long currentTimeMillis()
    {
        return System.nanoTime() / 1000000L;
    }

    /**
     * Processes a player starting/stopping flying
     */
    public void processPlayerAbilities(CPacketPlayerAbilities packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.capabilities.isFlying = packetIn.isFlying() && this.playerEntity.capabilities.allowFlying;
    }

    /**
     * Retrieves possible tab completions for the requested command string and sends them to the client
     */
    public void processTabComplete(CPacketTabComplete packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        List<String> list = Lists.<String>newArrayList();

        for (String s : this.serverController.getTabCompletions(this.playerEntity, packetIn.getMessage(), packetIn.getTargetBlock(), packetIn.hasTargetBlock()))
        {
            list.add(s);
        }

        this.playerEntity.connection.sendPacket(new SPacketTabComplete((String[])list.toArray(new String[list.size()])));
    }

    /**
     * Updates serverside copy of client settings: language, render distance, chat visibility, chat colours, difficulty,
     * and whether to show the cape
     */
    public void processClientSettings(CPacketClientSettings packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        this.playerEntity.handleClientSettings(packetIn);
    }

    /**
     * Synchronizes serverside and clientside book contents and signing
     */
    public void processCustomPayload(CPacketCustomPayload packetIn)
    {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.playerEntity.getServerWorld());
        String s = packetIn.getChannelName();

        if ("MC|BEdit".equals(s))
        {
            PacketBuffer packetbuffer = packetIn.getBufferData();

            try
            {
                ItemStack itemstack = packetbuffer.readItemStackFromBuffer();

                if (itemstack.func_190926_b())
                {
                    return;
                }

                if (!ItemWritableBook.isNBTValid(itemstack.getTagCompound()))
                {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack1 = this.playerEntity.getHeldItemMainhand();

                if (itemstack1.func_190926_b())
                {
                    return;
                }

                if (itemstack.getItem() == Items.WRITABLE_BOOK && itemstack.getItem() == itemstack1.getItem())
                {
                    itemstack1.setTagInfo("pages", itemstack.getTagCompound().getTagList("pages", 8));
                }
            }
            catch (Exception exception6)
            {
                LOGGER.error("Couldn't handle book info", (Throwable)exception6);
            }
        }
        else if ("MC|BSign".equals(s))
        {
            PacketBuffer packetbuffer1 = packetIn.getBufferData();

            try
            {
                ItemStack itemstack3 = packetbuffer1.readItemStackFromBuffer();

                if (itemstack3.func_190926_b())
                {
                    return;
                }

                if (!ItemWrittenBook.validBookTagContents(itemstack3.getTagCompound()))
                {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack4 = this.playerEntity.getHeldItemMainhand();

                if (itemstack4.func_190926_b())
                {
                    return;
                }

                if (itemstack3.getItem() == Items.WRITABLE_BOOK && itemstack4.getItem() == Items.WRITABLE_BOOK)
                {
                    ItemStack itemstack2 = new ItemStack(Items.WRITTEN_BOOK);
                    itemstack2.setTagInfo("author", new NBTTagString(this.playerEntity.getName()));
                    itemstack2.setTagInfo("title", new NBTTagString(itemstack3.getTagCompound().getString("title")));
                    NBTTagList nbttaglist = itemstack3.getTagCompound().getTagList("pages", 8);

                    for (int i = 0; i < nbttaglist.tagCount(); ++i)
                    {
                        String s1 = nbttaglist.getStringTagAt(i);
                        ITextComponent itextcomponent = new TextComponentString(s1);
                        s1 = ITextComponent.Serializer.componentToJson(itextcomponent);
                        nbttaglist.set(i, new NBTTagString(s1));
                    }

                    itemstack2.setTagInfo("pages", nbttaglist);
                    this.playerEntity.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, itemstack2);
                }
            }
            catch (Exception exception7)
            {
                LOGGER.error("Couldn't sign book", (Throwable)exception7);
            }
        }
        else if ("MC|TrSel".equals(s))
        {
            try
            {
                int k = packetIn.getBufferData().readInt();
                Container container = this.playerEntity.openContainer;

                if (container instanceof ContainerMerchant)
                {
                    ((ContainerMerchant)container).setCurrentRecipeIndex(k);
                }
            }
            catch (Exception exception5)
            {
                LOGGER.error("Couldn't select trade", (Throwable)exception5);
            }
        }
        else if ("MC|AdvCmd".equals(s))
        {
            if (!this.serverController.isCommandBlockEnabled())
            {
                this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.notEnabled", new Object[0]));
                return;
            }

            if (!this.playerEntity.canUseCommandBlock())
            {
                this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.notAllowed", new Object[0]));
                return;
            }

            PacketBuffer packetbuffer2 = packetIn.getBufferData();

            try
            {
                int l = packetbuffer2.readByte();
                CommandBlockBaseLogic commandblockbaselogic1 = null;

                if (l == 0)
                {
                    TileEntity tileentity = this.playerEntity.world.getTileEntity(new BlockPos(packetbuffer2.readInt(), packetbuffer2.readInt(), packetbuffer2.readInt()));

                    if (tileentity instanceof TileEntityCommandBlock)
                    {
                        commandblockbaselogic1 = ((TileEntityCommandBlock)tileentity).getCommandBlockLogic();
                    }
                }
                else if (l == 1)
                {
                    Entity entity = this.playerEntity.world.getEntityByID(packetbuffer2.readInt());

                    if (entity instanceof EntityMinecartCommandBlock)
                    {
                        commandblockbaselogic1 = ((EntityMinecartCommandBlock)entity).getCommandBlockLogic();
                    }
                }

                String s6 = packetbuffer2.readStringFromBuffer(packetbuffer2.readableBytes());
                boolean flag2 = packetbuffer2.readBoolean();

                if (commandblockbaselogic1 != null)
                {
                    commandblockbaselogic1.setCommand(s6);
                    commandblockbaselogic1.setTrackOutput(flag2);

                    if (!flag2)
                    {
                        commandblockbaselogic1.setLastOutput((ITextComponent)null);
                    }

                    commandblockbaselogic1.updateCommand();
                    this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.setCommand.success", new Object[] {s6}));
                }
            }
            catch (Exception exception4)
            {
                LOGGER.error("Couldn't set command block", (Throwable)exception4);
            }
        }
        else if ("MC|AutoCmd".equals(s))
        {
            if (!this.serverController.isCommandBlockEnabled())
            {
                this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.notEnabled", new Object[0]));
                return;
            }

            if (!this.playerEntity.canUseCommandBlock())
            {
                this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.notAllowed", new Object[0]));
                return;
            }

            PacketBuffer packetbuffer3 = packetIn.getBufferData();

            try
            {
                CommandBlockBaseLogic commandblockbaselogic = null;
                TileEntityCommandBlock tileentitycommandblock = null;
                BlockPos blockpos1 = new BlockPos(packetbuffer3.readInt(), packetbuffer3.readInt(), packetbuffer3.readInt());
                TileEntity tileentity2 = this.playerEntity.world.getTileEntity(blockpos1);

                if (tileentity2 instanceof TileEntityCommandBlock)
                {
                    tileentitycommandblock = (TileEntityCommandBlock)tileentity2;
                    commandblockbaselogic = tileentitycommandblock.getCommandBlockLogic();
                }

                String s7 = packetbuffer3.readStringFromBuffer(packetbuffer3.readableBytes());
                boolean flag3 = packetbuffer3.readBoolean();
                TileEntityCommandBlock.Mode tileentitycommandblock$mode = TileEntityCommandBlock.Mode.valueOf(packetbuffer3.readStringFromBuffer(16));
                boolean flag = packetbuffer3.readBoolean();
                boolean flag1 = packetbuffer3.readBoolean();

                if (commandblockbaselogic != null)
                {
                    EnumFacing enumfacing = (EnumFacing)this.playerEntity.world.getBlockState(blockpos1).getValue(BlockCommandBlock.FACING);

                    switch (tileentitycommandblock$mode)
                    {
                        case SEQUENCE:
                            IBlockState iblockstate3 = Blocks.CHAIN_COMMAND_BLOCK.getDefaultState();
                            this.playerEntity.world.setBlockState(blockpos1, iblockstate3.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, Boolean.valueOf(flag)), 2);
                            break;

                        case AUTO:
                            IBlockState iblockstate2 = Blocks.REPEATING_COMMAND_BLOCK.getDefaultState();
                            this.playerEntity.world.setBlockState(blockpos1, iblockstate2.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, Boolean.valueOf(flag)), 2);
                            break;

                        case REDSTONE:
                            IBlockState iblockstate = Blocks.COMMAND_BLOCK.getDefaultState();
                            this.playerEntity.world.setBlockState(blockpos1, iblockstate.withProperty(BlockCommandBlock.FACING, enumfacing).withProperty(BlockCommandBlock.CONDITIONAL, Boolean.valueOf(flag)), 2);
                    }

                    tileentity2.validate();
                    this.playerEntity.world.setTileEntity(blockpos1, tileentity2);
                    commandblockbaselogic.setCommand(s7);
                    commandblockbaselogic.setTrackOutput(flag3);

                    if (!flag3)
                    {
                        commandblockbaselogic.setLastOutput((ITextComponent)null);
                    }

                    tileentitycommandblock.setAuto(flag1);
                    commandblockbaselogic.updateCommand();

                    if (!net.minecraft.util.StringUtils.isNullOrEmpty(s7))
                    {
                        this.playerEntity.addChatMessage(new TextComponentTranslation("advMode.setCommand.success", new Object[] {s7}));
                    }
                }
            }
            catch (Exception exception3)
            {
                LOGGER.error("Couldn't set command block", (Throwable)exception3);
            }
        }
        else if ("MC|Beacon".equals(s))
        {
            if (this.playerEntity.openContainer instanceof ContainerBeacon)
            {
                try
                {
                    PacketBuffer packetbuffer4 = packetIn.getBufferData();
                    int i1 = packetbuffer4.readInt();
                    int k1 = packetbuffer4.readInt();
                    ContainerBeacon containerbeacon = (ContainerBeacon)this.playerEntity.openContainer;
                    Slot slot = containerbeacon.getSlot(0);

                    if (slot.getHasStack())
                    {
                        slot.decrStackSize(1);
                        IInventory iinventory = containerbeacon.getTileEntity();
                        iinventory.setField(1, i1);
                        iinventory.setField(2, k1);
                        iinventory.markDirty();
                    }
                }
                catch (Exception exception2)
                {
                    LOGGER.error("Couldn't set beacon", (Throwable)exception2);
                }
            }
        }
        else if ("MC|ItemName".equals(s))
        {
            if (this.playerEntity.openContainer instanceof ContainerRepair)
            {
                ContainerRepair containerrepair = (ContainerRepair)this.playerEntity.openContainer;

                if (packetIn.getBufferData() != null && packetIn.getBufferData().readableBytes() >= 1)
                {
                    String s5 = ChatAllowedCharacters.filterAllowedCharacters(packetIn.getBufferData().readStringFromBuffer(32767));

                    if (s5.length() <= 35)
                    {
                        containerrepair.updateItemName(s5);
                    }
                }
                else
                {
                    containerrepair.updateItemName("");
                }
            }
        }
        else if ("MC|Struct".equals(s))
        {
            if (!this.playerEntity.canUseCommandBlock())
            {
                return;
            }

            PacketBuffer packetbuffer5 = packetIn.getBufferData();

            try
            {
                BlockPos blockpos = new BlockPos(packetbuffer5.readInt(), packetbuffer5.readInt(), packetbuffer5.readInt());
                IBlockState iblockstate1 = this.playerEntity.world.getBlockState(blockpos);
                TileEntity tileentity1 = this.playerEntity.world.getTileEntity(blockpos);

                if (tileentity1 instanceof TileEntityStructure)
                {
                    TileEntityStructure tileentitystructure = (TileEntityStructure)tileentity1;
                    int l1 = packetbuffer5.readByte();
                    String s8 = packetbuffer5.readStringFromBuffer(32);
                    tileentitystructure.setMode(TileEntityStructure.Mode.valueOf(s8));
                    tileentitystructure.setName(packetbuffer5.readStringFromBuffer(64));
                    int i2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
                    int j2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
                    int k2 = MathHelper.clamp(packetbuffer5.readInt(), -32, 32);
                    tileentitystructure.setPosition(new BlockPos(i2, j2, k2));
                    int l2 = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
                    int i3 = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
                    int j = MathHelper.clamp(packetbuffer5.readInt(), 0, 32);
                    tileentitystructure.setSize(new BlockPos(l2, i3, j));
                    String s2 = packetbuffer5.readStringFromBuffer(32);
                    tileentitystructure.setMirror(Mirror.valueOf(s2));
                    String s3 = packetbuffer5.readStringFromBuffer(32);
                    tileentitystructure.setRotation(Rotation.valueOf(s3));
                    tileentitystructure.setMetadata(packetbuffer5.readStringFromBuffer(128));
                    tileentitystructure.setIgnoresEntities(packetbuffer5.readBoolean());
                    tileentitystructure.setShowAir(packetbuffer5.readBoolean());
                    tileentitystructure.setShowBoundingBox(packetbuffer5.readBoolean());
                    tileentitystructure.setIntegrity(MathHelper.clamp(packetbuffer5.readFloat(), 0.0F, 1.0F));
                    tileentitystructure.setSeed(packetbuffer5.readVarLong());
                    String s4 = tileentitystructure.getName();

                    if (l1 == 2)
                    {
                        if (tileentitystructure.save())
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.save_success", new Object[] {s4}), false);
                        }
                        else
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.save_failure", new Object[] {s4}), false);
                        }
                    }
                    else if (l1 == 3)
                    {
                        if (!tileentitystructure.isStructureLoadable())
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.load_not_found", new Object[] {s4}), false);
                        }
                        else if (tileentitystructure.load())
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.load_success", new Object[] {s4}), false);
                        }
                        else
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.load_prepare", new Object[] {s4}), false);
                        }
                    }
                    else if (l1 == 4)
                    {
                        if (tileentitystructure.detectSize())
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.size_success", new Object[] {s4}), false);
                        }
                        else
                        {
                            this.playerEntity.addChatComponentMessage(new TextComponentTranslation("structure_block.size_failure", new Object[0]), false);
                        }
                    }

                    tileentitystructure.markDirty();
                    this.playerEntity.world.notifyBlockUpdate(blockpos, iblockstate1, iblockstate1, 3);
                }
            }
            catch (Exception exception1)
            {
                LOGGER.error("Couldn't set structure block", (Throwable)exception1);
            }
        }
        else if ("MC|PickItem".equals(s))
        {
            PacketBuffer packetbuffer6 = packetIn.getBufferData();

            try
            {
                int j1 = packetbuffer6.readVarIntFromBuffer();
                this.playerEntity.inventory.pickItem(j1);
                this.playerEntity.connection.sendPacket(new SPacketSetSlot(-2, this.playerEntity.inventory.currentItem, this.playerEntity.inventory.getStackInSlot(this.playerEntity.inventory.currentItem)));
                this.playerEntity.connection.sendPacket(new SPacketSetSlot(-2, j1, this.playerEntity.inventory.getStackInSlot(j1)));
                this.playerEntity.connection.sendPacket(new SPacketHeldItemChange(this.playerEntity.inventory.currentItem));
            }
            catch (Exception exception)
            {
                LOGGER.error("Couldn't pick item", (Throwable)exception);
            }
        }
    }
}
