package net.minecraft.world.gen;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;

public class ChunkProviderServer implements IChunkProvider
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final Set<Long> droppedChunksSet = Sets.<Long>newHashSet();
    private final IChunkGenerator chunkGenerator;
    private final IChunkLoader chunkLoader;
    private final Long2ObjectMap<Chunk> id2ChunkMap = new Long2ObjectOpenHashMap<Chunk>(8192);
    private final WorldServer worldObj;

    public ChunkProviderServer(WorldServer worldObjIn, IChunkLoader chunkLoaderIn, IChunkGenerator chunkGeneratorIn)
    {
        this.worldObj = worldObjIn;
        this.chunkLoader = chunkLoaderIn;
        this.chunkGenerator = chunkGeneratorIn;
    }

    public Collection<Chunk> getLoadedChunks()
    {
        return this.id2ChunkMap.values();
    }

    /**
     * Unloads a chunk
     */
    public void unload(Chunk chunkIn)
    {
        if (this.worldObj.provider.canDropChunk(chunkIn.x, chunkIn.z))
        {
            this.droppedChunksSet.add(Long.valueOf(ChunkPos.asLong(chunkIn.x, chunkIn.z)));
            chunkIn.unloaded = true;
        }
    }

    /**
     * marks all chunks for unload, ignoring those near the spawn
     */
    public void unloadAllChunks()
    {
        ObjectIterator objectiterator = this.id2ChunkMap.values().iterator();

        while (objectiterator.hasNext())
        {
            Chunk chunk = (Chunk)objectiterator.next();
            this.unload(chunk);
        }
    }

    @Nullable
    public Chunk getLoadedChunk(int x, int z)
    {
        long i = ChunkPos.asLong(x, z);
        Chunk chunk = (Chunk)this.id2ChunkMap.get(i);

        if (chunk != null)
        {
            chunk.unloaded = false;
        }

        return chunk;
    }

    @Nullable
    public Chunk loadChunk(int x, int z)
    {
        Chunk chunk = this.getLoadedChunk(x, z);

        if (chunk == null)
        {
            chunk = this.loadChunkFromFile(x, z);

            if (chunk != null)
            {
                this.id2ChunkMap.put(ChunkPos.asLong(x, z), chunk);
                chunk.onChunkLoad();
                chunk.populateChunk(this, this.chunkGenerator);
            }
        }

        return chunk;
    }

    public Chunk provideChunk(int x, int z)
    {
        Chunk chunk = this.loadChunk(x, z);

        if (chunk == null)
        {
            long i = ChunkPos.asLong(x, z);

            try
            {
                chunk = this.chunkGenerator.provideChunk(x, z);
            }
            catch (Throwable throwable)
            {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception generating new chunk");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Chunk to be generated");
                crashreportcategory.addCrashSection("Location", String.format("%d,%d", x, z));
                crashreportcategory.addCrashSection("Position hash", Long.valueOf(i));
                crashreportcategory.addCrashSection("Generator", this.chunkGenerator);
                throw new ReportedException(crashreport);
            }

            this.id2ChunkMap.put(i, chunk);
            chunk.onChunkLoad();
            chunk.populateChunk(this, this.chunkGenerator);
        }

        return chunk;
    }

    @Nullable
    private Chunk loadChunkFromFile(int x, int z)
    {
        try
        {
            Chunk chunk = this.chunkLoader.loadChunk(this.worldObj, x, z);

            if (chunk != null)
            {
                chunk.setLastSaveTime(this.worldObj.getTotalWorldTime());
                this.chunkGenerator.recreateStructures(chunk, x, z);
            }

            return chunk;
        }
        catch (Exception exception)
        {
            LOGGER.error("Couldn't load chunk", (Throwable)exception);
            return null;
        }
    }

    private void saveChunkExtraData(Chunk chunkIn)
    {
        try
        {
            this.chunkLoader.saveExtraChunkData(this.worldObj, chunkIn);
        }
        catch (Exception exception)
        {
            LOGGER.error("Couldn't save entities", (Throwable)exception);
        }
    }

    private void saveChunkData(Chunk chunkIn)
    {
        try
        {
            chunkIn.setLastSaveTime(this.worldObj.getTotalWorldTime());
            this.chunkLoader.saveChunk(this.worldObj, chunkIn);
        }
        catch (IOException ioexception)
        {
            LOGGER.error("Couldn't save chunk", (Throwable)ioexception);
        }
        catch (MinecraftException minecraftexception)
        {
            LOGGER.error("Couldn't save chunk; already in use by another instance of Minecraft?", (Throwable)minecraftexception);
        }
    }

    public boolean saveChunks(boolean p_186027_1_)
    {
        int i = 0;
        List<Chunk> list = Lists.newArrayList(this.id2ChunkMap.values());

        for (int j = 0; j < list.size(); ++j)
        {
            Chunk chunk = list.get(j);

            if (p_186027_1_)
            {
                this.saveChunkExtraData(chunk);
            }

            if (chunk.needsSaving(p_186027_1_))
            {
                this.saveChunkData(chunk);
                chunk.setModified(false);
                ++i;

                if (i == 24 && !p_186027_1_)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Save extra data not associated with any Chunk.  Not saved during autosave, only during world unload.  Currently
     * unimplemented.
     */
    public void saveExtraData()
    {
        this.chunkLoader.saveExtraData();
    }

    /**
     * Unloads chunks that are marked to be unloaded. This is not guaranteed to unload every such chunk.
     */
    public boolean unloadQueuedChunks()
    {
        if (!this.worldObj.disableLevelSaving)
        {
            if (!this.droppedChunksSet.isEmpty())
            {
                Iterator<Long> iterator = this.droppedChunksSet.iterator();

                for (int i = 0; i < 100 && iterator.hasNext(); iterator.remove())
                {
                    Long olong = iterator.next();
                    Chunk chunk = (Chunk)this.id2ChunkMap.get(olong);

                    if (chunk != null && chunk.unloaded)
                    {
                        chunk.onChunkUnload();
                        this.saveChunkData(chunk);
                        this.saveChunkExtraData(chunk);
                        this.id2ChunkMap.remove(olong);
                        ++i;
                    }
                }
            }

            this.chunkLoader.chunkTick();
        }

        return false;
    }

    /**
     * Returns if the IChunkProvider supports saving.
     */
    public boolean canSave()
    {
        return !this.worldObj.disableLevelSaving;
    }

    /**
     * Converts the instance data to a readable string.
     */
    public String makeString()
    {
        return "ServerChunkCache: " + this.id2ChunkMap.size() + " Drop: " + this.droppedChunksSet.size();
    }

    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos)
    {
        return this.chunkGenerator.getPossibleCreatures(creatureType, pos);
    }

    @Nullable
    public BlockPos getStrongholdGen(World worldIn, String structureName, BlockPos position, boolean p_180513_4_)
    {
        return this.chunkGenerator.getStrongholdGen(worldIn, structureName, position, p_180513_4_);
    }

    public boolean func_193413_a(World p_193413_1_, String p_193413_2_, BlockPos p_193413_3_)
    {
        return this.chunkGenerator.func_193414_a(p_193413_1_, p_193413_2_, p_193413_3_);
    }

    public int getLoadedChunkCount()
    {
        return this.id2ChunkMap.size();
    }

    /**
     * Checks to see if a chunk exists at x, z
     */
    public boolean chunkExists(int x, int z)
    {
        return this.id2ChunkMap.containsKey(ChunkPos.asLong(x, z));
    }

    public boolean func_191062_e(int p_191062_1_, int p_191062_2_)
    {
        return this.id2ChunkMap.containsKey(ChunkPos.asLong(p_191062_1_, p_191062_2_)) || this.chunkLoader.func_191063_a(p_191062_1_, p_191062_2_);
    }
}
