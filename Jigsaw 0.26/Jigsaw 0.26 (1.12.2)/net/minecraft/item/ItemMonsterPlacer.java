package net.minecraft.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

public class ItemMonsterPlacer extends Item
{
    public ItemMonsterPlacer()
    {
        this.setCreativeTab(CreativeTabs.MISC);
    }

    public String getItemStackDisplayName(ItemStack stack)
    {
        String s = ("" + I18n.translateToLocal(this.getUnlocalizedName() + ".name")).trim();
        String s1 = EntityList.func_191302_a(func_190908_h(stack));

        if (s1 != null)
        {
            s = s + " " + I18n.translateToLocal("entity." + s1 + ".name");
        }

        return s;
    }

    /**
     * Called when a Block is right-clicked with this Item
     */
    public EnumActionResult onItemUse(EntityPlayer stack, World playerIn, BlockPos worldIn, EnumHand pos, EnumFacing hand, float facing, float hitX, float hitY)
    {
        ItemStack itemstack = stack.getHeldItem(pos);

        if (playerIn.isRemote)
        {
            return EnumActionResult.SUCCESS;
        }
        else if (!stack.canPlayerEdit(worldIn.offset(hand), hand, itemstack))
        {
            return EnumActionResult.FAIL;
        }
        else
        {
            IBlockState iblockstate = playerIn.getBlockState(worldIn);
            Block block = iblockstate.getBlock();

            if (block == Blocks.MOB_SPAWNER)
            {
                TileEntity tileentity = playerIn.getTileEntity(worldIn);

                if (tileentity instanceof TileEntityMobSpawner)
                {
                    MobSpawnerBaseLogic mobspawnerbaselogic = ((TileEntityMobSpawner)tileentity).getSpawnerBaseLogic();
                    mobspawnerbaselogic.func_190894_a(func_190908_h(itemstack));
                    tileentity.markDirty();
                    playerIn.notifyBlockUpdate(worldIn, iblockstate, iblockstate, 3);

                    if (!stack.capabilities.isCreativeMode)
                    {
                        itemstack.func_190918_g(1);
                    }

                    return EnumActionResult.SUCCESS;
                }
            }

            BlockPos blockpos = worldIn.offset(hand);
            double d0 = this.func_190909_a(playerIn, blockpos);
            Entity entity = spawnCreature(playerIn, func_190908_h(itemstack), (double)blockpos.getX() + 0.5D, (double)blockpos.getY() + d0, (double)blockpos.getZ() + 0.5D);

            if (entity != null)
            {
                if (entity instanceof EntityLivingBase && itemstack.hasDisplayName())
                {
                    entity.setCustomNameTag(itemstack.getDisplayName());
                }

                applyItemEntityDataToEntity(playerIn, stack, itemstack, entity);

                if (!stack.capabilities.isCreativeMode)
                {
                    itemstack.func_190918_g(1);
                }
            }

            return EnumActionResult.SUCCESS;
        }
    }

    protected double func_190909_a(World p_190909_1_, BlockPos p_190909_2_)
    {
        AxisAlignedBB axisalignedbb = (new AxisAlignedBB(p_190909_2_)).addCoord(0.0D, -1.0D, 0.0D);
        List<AxisAlignedBB> list = p_190909_1_.getCollisionBoxes((Entity)null, axisalignedbb);

        if (list.isEmpty())
        {
            return 0.0D;
        }
        else
        {
            double d0 = axisalignedbb.minY;

            for (AxisAlignedBB axisalignedbb1 : list)
            {
                d0 = Math.max(axisalignedbb1.maxY, d0);
            }

            return d0 - (double)p_190909_2_.getY();
        }
    }

    /**
     * Applies the data in the EntityTag tag of the given ItemStack to the given Entity.
     */
    public static void applyItemEntityDataToEntity(World entityWorld, @Nullable EntityPlayer player, ItemStack stack, @Nullable Entity targetEntity)
    {
        MinecraftServer minecraftserver = entityWorld.getMinecraftServer();

        if (minecraftserver != null && targetEntity != null)
        {
            NBTTagCompound nbttagcompound = stack.getTagCompound();

            if (nbttagcompound != null && nbttagcompound.hasKey("EntityTag", 10))
            {
                if (!entityWorld.isRemote && targetEntity.ignoreItemEntityData() && (player == null || !minecraftserver.getPlayerList().canSendCommands(player.getGameProfile())))
                {
                    return;
                }

                NBTTagCompound nbttagcompound1 = targetEntity.writeToNBT(new NBTTagCompound());
                UUID uuid = targetEntity.getUniqueID();
                nbttagcompound1.merge(nbttagcompound.getCompoundTag("EntityTag"));
                targetEntity.setUniqueId(uuid);
                targetEntity.readFromNBT(nbttagcompound1);
            }
        }
    }

    public ActionResult<ItemStack> onItemRightClick(World itemStackIn, EntityPlayer worldIn, EnumHand playerIn)
    {
        ItemStack itemstack = worldIn.getHeldItem(playerIn);

        if (itemStackIn.isRemote)
        {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, itemstack);
        }
        else
        {
            RayTraceResult raytraceresult = this.rayTrace(itemStackIn, worldIn, true);

            if (raytraceresult != null && raytraceresult.typeOfHit == RayTraceResult.Type.BLOCK)
            {
                BlockPos blockpos = raytraceresult.getBlockPos();

                if (!(itemStackIn.getBlockState(blockpos).getBlock() instanceof BlockLiquid))
                {
                    return new ActionResult<ItemStack>(EnumActionResult.PASS, itemstack);
                }
                else if (itemStackIn.isBlockModifiable(worldIn, blockpos) && worldIn.canPlayerEdit(blockpos, raytraceresult.sideHit, itemstack))
                {
                    Entity entity = spawnCreature(itemStackIn, func_190908_h(itemstack), (double)blockpos.getX() + 0.5D, (double)blockpos.getY() + 0.5D, (double)blockpos.getZ() + 0.5D);

                    if (entity == null)
                    {
                        return new ActionResult<ItemStack>(EnumActionResult.PASS, itemstack);
                    }
                    else
                    {
                        if (entity instanceof EntityLivingBase && itemstack.hasDisplayName())
                        {
                            entity.setCustomNameTag(itemstack.getDisplayName());
                        }

                        applyItemEntityDataToEntity(itemStackIn, worldIn, itemstack, entity);

                        if (!worldIn.capabilities.isCreativeMode)
                        {
                            itemstack.func_190918_g(1);
                        }

                        worldIn.addStat(StatList.getObjectUseStats(this));
                        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
                    }
                }
                else
                {
                    return new ActionResult<ItemStack>(EnumActionResult.FAIL, itemstack);
                }
            }
            else
            {
                return new ActionResult<ItemStack>(EnumActionResult.PASS, itemstack);
            }
        }
    }

    @Nullable

    /**
     * Spawns the creature specified by the egg's type in the location specified by the last three parameters.
     * Parameters: world, entityID, x, y, z.
     */
    public static Entity spawnCreature(World worldIn, @Nullable ResourceLocation entityID, double x, double y, double z)
    {
        if (entityID != null && EntityList.ENTITY_EGGS.containsKey(entityID))
        {
            Entity entity = null;

            for (int i = 0; i < 1; ++i)
            {
                entity = EntityList.createEntityByIDFromName(entityID, worldIn);

                if (entity instanceof EntityLiving)
                {
                    EntityLiving entityliving = (EntityLiving)entity;
                    entity.setLocationAndAngles(x, y, z, MathHelper.wrapDegrees(worldIn.rand.nextFloat() * 360.0F), 0.0F);
                    entityliving.rotationYawHead = entityliving.rotationYaw;
                    entityliving.renderYawOffset = entityliving.rotationYaw;
                    entityliving.onInitialSpawn(worldIn.getDifficultyForLocation(new BlockPos(entityliving)), (IEntityLivingData)null);
                    worldIn.spawnEntityInWorld(entity);
                    entityliving.playLivingSound();
                }
            }

            return entity;
        }
        else
        {
            return null;
        }
    }

    /**
     * returns a list of items with the same ID, but different meta (eg: dye returns 16 items)
     */
    public void getSubItems(CreativeTabs itemIn, NonNullList<ItemStack> tab)
    {
        if (this.func_194125_a(itemIn))
        {
            for (EntityList.EntityEggInfo entitylist$entityegginfo : EntityList.ENTITY_EGGS.values())
            {
                ItemStack itemstack = new ItemStack(this, 1);
                applyEntityIdToItemStack(itemstack, entitylist$entityegginfo.spawnedID);
                tab.add(itemstack);
            }
        }
    }

    /**
     * APplies the given entity ID to the given ItemStack's NBT data.
     */
    public static void applyEntityIdToItemStack(ItemStack stack, ResourceLocation entityId)
    {
        NBTTagCompound nbttagcompound = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();
        nbttagcompound1.setString("id", entityId.toString());
        nbttagcompound.setTag("EntityTag", nbttagcompound1);
        stack.setTagCompound(nbttagcompound);
    }

    @Nullable
    public static ResourceLocation func_190908_h(ItemStack p_190908_0_)
    {
        NBTTagCompound nbttagcompound = p_190908_0_.getTagCompound();

        if (nbttagcompound == null)
        {
            return null;
        }
        else if (!nbttagcompound.hasKey("EntityTag", 10))
        {
            return null;
        }
        else
        {
            NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundTag("EntityTag");

            if (!nbttagcompound1.hasKey("id", 8))
            {
                return null;
            }
            else
            {
                String s = nbttagcompound1.getString("id");
                ResourceLocation resourcelocation = new ResourceLocation(s);

                if (!s.contains(":"))
                {
                    nbttagcompound1.setString("id", resourcelocation.toString());
                }

                return resourcelocation;
            }
        }
    }
}
