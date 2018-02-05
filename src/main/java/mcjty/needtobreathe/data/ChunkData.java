package mcjty.needtobreathe.data;

import net.minecraft.util.EnumFacing;

/**
 * All clean air in a subchunk (16x16x16)
 */
public class ChunkData {

    private byte data[];        // 0 = no clean air, 255 = 100% clean

//    public static final int CHUNK_DIM = 16;
//    public static final int CHUNK_SHIFT = 4;
//    public static final int CHUNK_2SHIFT = 8;
//    public static final int CHUNK_MASK = 0xf;
    public static final int CHUNK_DIM = 8;
    public static final int CHUNK_SHIFT = 3;
    public static final int CHUNK_2SHIFT = 6;
    public static final int CHUNK_MASK = 0x7;
    public static final int CHUNK_SIZE = CHUNK_DIM*CHUNK_DIM*CHUNK_DIM;

    public ChunkData() {
        data = new byte[CHUNK_SIZE];
    }

    public ChunkData(byte[] data) {
        this.data = data;
    }

    public static int index(int dx, int dy, int dz) {
        return (dx << CHUNK_2SHIFT) + (dy << CHUNK_SHIFT) + dz;
    }

    // Given an index in the inner part of the data (not including the outer boundary of the 16x16x16 subchunk) calculate a new index with the given offset
    public static int offset(int idx, EnumFacing offset) {
        switch (offset) {
            case DOWN:
                return idx - CHUNK_DIM;
            case UP:
                return idx + CHUNK_DIM;
            case NORTH:
                return idx - 1;
            case SOUTH:
                return idx + 1;
            case WEST:
                return idx - (CHUNK_DIM*CHUNK_DIM);
            case EAST:
                return idx + (CHUNK_DIM*CHUNK_DIM);
        }
        return idx;
    }

    public static int offsetWrap(int idx, EnumFacing offset) {
        switch (offset) {
            case DOWN:
                if ((idx & (CHUNK_MASK<<CHUNK_SHIFT)) == 0) {
                    return (idx | (CHUNK_MASK<<CHUNK_SHIFT));
                }
                return idx - CHUNK_DIM;
            case UP:
                if ((idx & (CHUNK_MASK<<CHUNK_SHIFT)) == (CHUNK_MASK<<CHUNK_SHIFT)) {
                    return (idx & ~(CHUNK_MASK<<CHUNK_SHIFT));
                }
                return idx + CHUNK_DIM;
            case NORTH:
                if ((idx & CHUNK_MASK) == 0) {
                    return (idx | CHUNK_MASK);
                }
                return idx - 1;
            case SOUTH:
                if ((idx & CHUNK_MASK) == CHUNK_MASK) {
                    return (idx & ~CHUNK_MASK);
                }
                return idx + 1;
            case WEST:
                if ((idx & (CHUNK_MASK<<CHUNK_2SHIFT)) == 0) {
                    return (idx | (CHUNK_MASK<<CHUNK_2SHIFT));
                }
                return idx - (CHUNK_DIM*CHUNK_DIM);
            case EAST:
                if ((idx & (CHUNK_MASK<<CHUNK_2SHIFT)) == (CHUNK_MASK<<CHUNK_2SHIFT)) {
                    return (idx & ~(CHUNK_MASK<<CHUNK_2SHIFT));
                }
                return idx + (CHUNK_DIM*CHUNK_DIM);
        }
        return idx;
    }

    public int getPoison(int x, int y, int z) {
        int idx = ((x & CHUNK_MASK) << CHUNK_2SHIFT) + ((y & CHUNK_MASK) << CHUNK_SHIFT) + (z & CHUNK_MASK);
        return 255-(data[idx] & 0xff);
    }

    public int getAir(int x, int y, int z) {
        int idx = ((x & CHUNK_MASK) << CHUNK_2SHIFT) + ((y & CHUNK_MASK) << CHUNK_SHIFT) + (z & CHUNK_MASK);
        return data[idx] & 0xff;
    }

    public void putAir(int x, int y, int z, int air) {
        int idx = ((x & CHUNK_MASK) << CHUNK_2SHIFT) + ((y & CHUNK_MASK) << CHUNK_SHIFT) + (z & CHUNK_MASK);
        data[idx] = (byte) air;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isStrong() {
        return data == null;
    }
}
