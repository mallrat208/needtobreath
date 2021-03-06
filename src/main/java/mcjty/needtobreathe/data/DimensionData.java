package mcjty.needtobreathe.data;

import mcjty.needtobreathe.config.Config;
import mcjty.needtobreathe.config.PotionEffectConfig;
import mcjty.needtobreathe.items.InformationGlasses;
import mcjty.needtobreathe.items.ProtectiveHelmet;
import mcjty.needtobreathe.network.NTBMessages;
import mcjty.needtobreathe.network.PacketSendCleanAirToClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

import static mcjty.needtobreathe.data.ChunkData.CHUNK_DIM;

public class DimensionData {


    public static final int MAXEFFECTSTICKS = 5;

    private int counter = 1;
    private int subCounter = 0;
    private int effectCounter = MAXEFFECTSTICKS;
    private int globalCacheNr = 1;         // For the validity cache
    private int globalCacheUpdateTick = 1;

    private final Map<Long, ChunkData> cleanAir = new HashMap<>();       // 0 = no clean air, 255 = 100% clean


    private static int g_seed = 123456789;

    public static int fastrand128() {
        g_seed = (214013 * g_seed + 2531011);
        return (g_seed >> 16) & 0x7F;
    }

    /**
     * Get the minimum poison level for this position and adjacent positions
     */
    public int getPoison(BlockPos p) {
        int minPoison = 255;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int poison = getPoisonInternal(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (poison < minPoison) {
                        minPoison = poison;
                        if (minPoison == 0) {
                            return 0;
                        }
                    }
                }
            }
        }
        return Math.max(minPoison - Config.POISON_THRESSHOLD, 0);
    }

    private int getPoisonInternal(int x, int y, int z) {
        long chunkPos = SubChunkPosIndexed.fromPos(x, y, z);
        ChunkData data = cleanAir.get(chunkPos);
        if (data != null) {
            if (data.isStrong()) {
                return 0;
            }
            return data.getPoison(x, y, z);
        } else {
            return 255;
        }
    }

    public static boolean isValid(World world, BlockPos p) {
        IBlockState state = world.getBlockState(p);
        Block block = state.getBlock();

        if (block.isAir(state, world, p)) {
            return true;
        }

        if (Config.getBlocksBlocking().contains(block)) {
            // Special case for doors
            if (block instanceof BlockDoor) {
                return state.getValue(BlockDoor.OPEN);
            }
            if (block instanceof BlockTrapDoor) {
                return state.getValue(BlockTrapDoor.OPEN);
            }

            return false;
        }
        if (Config.getBlocksNonBlocking().contains(block)) {
            return true;
        }

        AxisAlignedBB box = state.getCollisionBoundingBox(world, p);
        if (box == null) {
            return true;
        }
        return !block.isOpaqueCube(state);
    }

    public void removeStrongAir(BlockPos p) {
        long chunkPos = SubChunkPosIndexed.fromPos(p);
        cleanAir.remove(chunkPos);
    }

    // Fill an entire chunk with strong clean air that doesn't do any ticking
    public void fillCleanAirStrong(BlockPos p) {
        long chunkPos = SubChunkPosIndexed.fromPos(p);
        cleanAir.remove(chunkPos);
        ChunkData data = new ChunkData(null);
        cleanAir.put(chunkPos, data);
    }

    // When a block is broken we clean the air there and also make sure the 'isValid' cache is
    // recalculated in this subchunk
    public void breakBlock(BlockPos p) {
        fillCleanAir(p);
        getChunkData(p).invalidateCache();
    }

    public void placeBlock(BlockPos p) {
        getChunkData(p).invalidateCache();
    }

    public int fillCleanAir(BlockPos p) {
        return fillCleanAir(p.getX(), p.getY(), p.getZ());
    }

    public int fillCleanAir(int x, int y, int z) {
        long chunkPos = SubChunkPosIndexed.fromPos(x, y, z);
        int air;
        ChunkData data = cleanAir.get(chunkPos);
        if (data != null) {
            if (data.isStrong()) {
                return 0;   // Nothing has happened
            }
            air = data.getAir(x, y, z);
        } else {
            data = new ChunkData();
            cleanAir.put(chunkPos, data);
            air = 0;
        }
        data.putAir(x, y, z, 255);
        return 255 - air;
    }


    public void worldTick(World world) {
        counter--;
        if (counter <= 0) {
            counter = Config.SUBCHUNK_TICKS;

            effectCounter--;
            if (effectCounter <= 0) {
                effectCounter = MAXEFFECTSTICKS;
                handleEffects(world);
            }

            tick(world);

            for (EntityPlayer player : world.playerEntities) {
                ItemStack helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
                if (!helmet.isEmpty() && helmet.getItem() instanceof InformationGlasses) {
                    PacketSendCleanAirToClient message = new PacketSendCleanAirToClient(getCleanAirPosition(player.getPosition()));
                    NTBMessages.INSTANCE.sendTo(message, (EntityPlayerMP) player);
                }
            }
        }
        CleanAirManager.getManager().save();
    }

    private Map<Long, ChunkData> getCleanAirPosition(BlockPos pos) {
        long center = SubChunkPosIndexed.fromPos(pos);
        Map<Long, ChunkData> map = new HashMap<>();
        int centerCx = SubChunkPosIndexed.getX(center);
        int centerCy = SubChunkPosIndexed.getY(center);
        int centerCz = SubChunkPosIndexed.getZ(center);
        int dist = 8;
        for (Map.Entry<Long, ChunkData> entry : cleanAir.entrySet()) {
            long chunkPos = entry.getKey();
            if (Math.abs(centerCx - SubChunkPosIndexed.getX(chunkPos)) <= dist
                    && Math.abs(centerCy - SubChunkPosIndexed.getY(chunkPos)) <= dist
                    && Math.abs(centerCz - SubChunkPosIndexed.getZ(chunkPos)) <= dist) {
                map.put(chunkPos, entry.getValue());
            }
        }
        return map;
    }

    private void handleEffects(World world) {
        List<Pair<Integer, Entity>> affectedEntities = new ArrayList<>();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLivingBase) {
                int poison = getPoison(entity.getPosition().up());
                if (poison > 20) {
                    affectedEntities.add(Pair.of(poison, entity));
                }
            }
        }

        for (Pair<Integer, Entity> pair : affectedEntities) {
            Entity entity = pair.getRight();
            Integer poison = pair.getLeft();

            PotionEffectConfig[] potionConfigs;

            if (entity instanceof EntityPlayer) {
                potionConfigs = Config.getPlayerEffects();
                EntityPlayer player = (EntityPlayer) entity;
                ItemStack helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
                if (!helmet.isEmpty() && helmet.getItem() instanceof ProtectiveHelmet) {
                    poison = (int) (poison * Config.PROTECTIVE_HELMET_FACTOR);
                }
            } else if (entity instanceof IMob) {
                potionConfigs = Config.getHostileEffects();
            } else {
                potionConfigs = Config.getPassiveEffects();
            }

            if (potionConfigs.length > 0) {
                for (PotionEffectConfig config : potionConfigs) {
                    if (poison >= config.getPoisonThresshold()) {
                        ((EntityLivingBase) entity).addPotionEffect(new PotionEffect(config.getPotion(),
                                Config.SUBCHUNK_TICKS * MAXEFFECTSTICKS * 2, config.getAmplitude()));
                    }
                }
            }
        }
    }

    private static int[] distList = new int[7];
    private static byte[][] distListData = new byte[7][];

    private boolean tickSubChunk(World world, long chunkPos, ChunkData data) {
        boolean empty = true;
        byte[] a = data.getData();
        for (int dx = 0; dx < CHUNK_DIM; dx++) {
            for (int dy = 0; dy < CHUNK_DIM; dy++) {
                for (int dz = 0; dz < CHUNK_DIM; dz++) {
                    int idx = ChunkData.index(dx, dy, dz);
                    int air = a[idx] & 0xff;
                    if (fastrand128() < Config.CLEANAIR_DECAY_CHANCE) {
                        air--;
                    }

                    if (air < 5 || !data.isValid(globalCacheNr, world, chunkPos, idx)) {
                        a[idx] = 0;
                    } else {
                        empty = false;
                        int totalAir = air;
                        int distListCnt = 0;

                        if (dx == 0 || dy == 0 || dz == 0 || dx == CHUNK_DIM - 1 || dy == CHUNK_DIM - 1 || dz == CHUNK_DIM - 1) {
                            // We are on a border

                            // Evenly distribute all air to the adjacent spots (and this one)
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                long adjacentChunkPos = ChunkData.adjacentChunkPosIndexed(idx, facing, chunkPos);
                                ChunkData dataAdjacent = getChunkData(adjacentChunkPos);
                                int idxAdjacent = ChunkData.offsetWrap(idx, facing);

                                if (dataAdjacent.isValid(globalCacheNr, world, adjacentChunkPos, idxAdjacent)) {
                                    byte[] d = a;
                                    if (dataAdjacent != data) { // @todo compare long
                                        if (dataAdjacent.isStrong()) {
                                            d = null;
                                            totalAir += 255;
                                        } else {
                                            d = dataAdjacent.getData();
                                            totalAir += d[idxAdjacent] & 0xff;
                                        }
                                    } else {
                                        totalAir += d[idxAdjacent] & 0xff;
                                    }
                                    distListData[distListCnt] = d;
                                    distList[distListCnt] = idxAdjacent;
                                    distListCnt++;
                                }
                            }

                            if (distListCnt > 0) {
                                // We distribute 'air' to all legal adjacent spaces (and this one)
                                air = totalAir / (distListCnt + 1);
                                for (int i = 0; i < distListCnt; i++) {
                                    totalAir -= air;
                                    byte[] b = distListData[i];
                                    if (b != null) {
                                        b[distList[i]] = (byte) air;
                                    }
                                }
                            }
                        } else {
                            // Evenly distribute all air to the adjacent spots (and this one)
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                int idxAdjacent = ChunkData.offset(idx, facing);
                                if (data.isValid(globalCacheNr, world, chunkPos, idxAdjacent)) {
                                    totalAir += a[idxAdjacent] & 0xff;
                                    distList[distListCnt] = idxAdjacent;
                                    distListCnt++;
                                }
                            }

                            if (distListCnt > 0) {
                                // We distribute 'air' to all legal adjacent spaces (and this one)
                                air = totalAir / (distListCnt + 1);
                                for (int i = 0; i < distListCnt; i++) {
                                    totalAir -= air;
                                    a[distList[i]] = (byte) air;
                                }
                            }
                        }
                        a[idx] = (byte) totalAir;
                    }
                }
            }
        }
        return empty;
    }

    private ChunkData getChunkData(BlockPos pos) {
        long chunkPos = SubChunkPosIndexed.fromPos(pos);
        return getChunkData(chunkPos);
    }

    private ChunkData getChunkData(long chunkPos) {
        if (!cleanAir.containsKey(chunkPos)) {
            ChunkData data = new ChunkData();
            cleanAir.put(chunkPos, data);
        }
        return cleanAir.get(chunkPos);
    }

    /**
     * This version is used to tick the borders of a strong chunk. i.e. the strong chunk
     * is not touched itself but influences the surroundings
     */
    private void tickSubChunkBordersStrong(long chunkPos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            long adjacentPos = SubChunkPosIndexed.offset(facing, chunkPos);
            if (adjacentPos == chunkPos) {
                continue;   // Same chunk, that means we hit a Y border
            }

            ChunkData adjacentData = cleanAir.get(adjacentPos);
            if (adjacentData == null || !adjacentData.isStrong()) {
                if (adjacentData == null) {
                    adjacentData = new ChunkData();
                    cleanAir.put(adjacentPos, adjacentData);
                }
                byte[] b = adjacentData.getData();

                // Not a 'strong' subchunk at this side so we have to propagate clean air
                switch (facing) {
                    case DOWN:
                        for (int x = 0; x < CHUNK_DIM; x++) {
                            for (int z = 0; z < CHUNK_DIM; z++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(x, CHUNK_DIM - 1, z);     // Up side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                    case UP:
                        for (int x = 0; x < CHUNK_DIM; x++) {
                            for (int z = 0; z < CHUNK_DIM; z++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(x, 0, z);     // Down side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                    case NORTH:
                        for (int x = 0; x < CHUNK_DIM; x++) {
                            for (int y = 0; y < CHUNK_DIM; y++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(x, y, CHUNK_DIM - 1);     // South side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                    case SOUTH:
                        for (int x = 0; x < CHUNK_DIM; x++) {
                            for (int y = 0; y < CHUNK_DIM; y++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(x, y, 0);     // North side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                    case WEST:
                        for (int y = 0; y < CHUNK_DIM; y++) {
                            for (int z = 0; z < CHUNK_DIM; z++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(CHUNK_DIM - 1, y, z);     // Right side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                    case EAST:
                        for (int y = 0; y < CHUNK_DIM; y++) {
                            for (int z = 0; z < CHUNK_DIM; z++) {
                                if (fastrand128() < Config.STRONGAIR_PROPAGATE_CHANCE) {
                                    int idx = ChunkData.index(0, y, z);     // Left side of adjacent chunk
                                    b[idx] = (byte) 255;
                                }
                            }
                        }
                        break;
                }
            }
        }
    }

    public void tick(World world) {
        globalCacheUpdateTick--;
        if (globalCacheUpdateTick <= 0) {
            // We don't update the globalCacheNr every time but keep the isValid caches active for a while longer. Worlds don't change
            // that often usually
            globalCacheUpdateTick = 20;
            globalCacheNr++;
        }
        subCounter++;

        // We do everything if the number of subchunks is low enough and every 4 subchunk tinks
        boolean doAll = cleanAir.size() < 3000 || (subCounter % 4 == 0);

        Set<Long> subChunksNearPlayers = new HashSet<>();
        for (EntityPlayer player : world.playerEntities) {
            long chunkPos = SubChunkPosIndexed.fromPos(player.getPosition());
            int cx = SubChunkPosIndexed.getX(chunkPos);
            int cy = SubChunkPosIndexed.getY(chunkPos);
            int cz = SubChunkPosIndexed.getZ(chunkPos);
            // The subchunk of a player we update more often with regards to validity checking (for doors mostly)
            ChunkData data = cleanAir.get(chunkPos);
            if (data != null) {
                data.invalidateCache();
            }

            if (!doAll) {
                int offs = 8;
                for (int dy = -offs; dy <= offs; dy++) {
                    if ((cy + dy) >= 0 && (cy + dy) < (256 / CHUNK_DIM)) {
                        for (int dx = -offs; dx <= offs; dx++) {
                            for (int dz = -offs; dz <= offs; dz++) {
                                subChunksNearPlayers.add(SubChunkPosIndexed.toLong(cx + dx, cy + dy, cz + dz));
                            }
                        }
                    }
                }
            }
        }

        Set<Map.Entry<Long, ChunkData>> copy = new HashSet<>(cleanAir.entrySet());
        for (Map.Entry<Long, ChunkData> entry : copy) {
            long chunkPos = entry.getKey();
            if (doAll || subChunksNearPlayers.contains(chunkPos)) {
                ChunkData data = entry.getValue();

                if (data.isStrong()) {
                    tickSubChunkBordersStrong(chunkPos);
                } else {
                    // First do the internals of each subchunk
                    if (tickSubChunk(world, chunkPos, data)) {
                        // No clean air in the subchunk. We can remove it
                        cleanAir.remove(chunkPos);
                    }
                }
            }
        }
    }


    public void readFromNBT(NBTTagCompound nbt) {
        cleanAir.clear();
        NBTTagList list = nbt.getTagList("list", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound subchunkNBT = list.getCompoundTagAt(i);
            long chunkPos = subchunkNBT.getLong("pos");
            if (subchunkNBT.hasKey("strong")) {
                cleanAir.put(chunkPos, new ChunkData(null));
            } else {
                byte[] data = subchunkNBT.getByteArray("data");
                cleanAir.put(chunkPos, new ChunkData(data));
            }
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<Long, ChunkData> entry : cleanAir.entrySet()) {
            NBTTagCompound subchunkNBT = new NBTTagCompound();
            subchunkNBT.setLong("pos", entry.getKey());
            if (entry.getValue().isStrong()) {
                subchunkNBT.setBoolean("strong", true);
            } else {
                subchunkNBT.setByteArray("data", entry.getValue().getData());
            }
            list.appendTag(subchunkNBT);
        }
        compound.setTag("list", list);

        return compound;
    }
}
