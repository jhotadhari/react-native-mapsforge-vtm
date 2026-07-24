/*
 * Copyright 2012-2014 Hannes Janetzek
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.renderer.bucket;

import org.oscim.backend.GL;
import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile.TileData;
import org.oscim.renderer.BufferObject;
import org.oscim.renderer.MapRenderer;
import org.oscim.theme.styles.AreaStyle;
import org.oscim.theme.styles.CircleStyle;
import org.oscim.theme.styles.LineStyle;

import java.nio.ShortBuffer;
import java.util.logging.Logger;

import static org.oscim.renderer.MapRenderer.COORD_SCALE;
import static org.oscim.renderer.bucket.RenderBucket.*;

/**
 * This class is primarily intended for rendering the vector elements of a
 * MapTile. It can be used for other purposes as well but some optimizations
 * (and limitations) probably wont make sense in different contexts.
 *
 * RESTORED from vtm 0.29.0 source.  The vtm 0.29.0 JAR contains these
 * .class files but AGP/D8 drops them during DEX merge for unknown reasons.
 * Compiling from source ensures the classes survive into the APK.
 */
public class RenderBuckets extends TileData {

    private static final Logger log = Logger.getLogger(RenderBuckets.class.getName());

    public static final int[] VERTEX_CNT = {
            4, // LINE_VERTEX
            6, // TEXLINE_VERTEX
            2, // POLY_VERTEX
            2, // MESH_VERTEX
            4, // EXTRUSION_VERTEX
            2, // HAIRLINE_VERTEX
            6, // SYMBOL
            6, // BITMAP
            2, // CIRCLE
    };

    public static final int SHORT_BYTES = 2;

    public static final int TILE_FILL_VERTICES = 4;

    private RenderBucket buckets;

    public BufferObject vbo;
    public BufferObject ibo;

    public int[] offset = {0, 0};

    private RenderBucket mCurBucket;

    public RenderBuckets() {
    }

    public LineBucket addLineBucket(int level, LineStyle style) {
        LineBucket l = (LineBucket) getBucket(level, LINE);
        if (l == null)
            return null;
        l.scale = 1;
        l.line = style;
        return l;
    }

    public PolygonBucket addPolygonBucket(int level, AreaStyle style) {
        PolygonBucket l = (PolygonBucket) getBucket(level, POLYGON);
        if (l == null)
            return null;
        l.area = style;
        return l;
    }

    public MeshBucket addMeshBucket(int level, AreaStyle style) {
        MeshBucket l = (MeshBucket) getBucket(level, MESH);
        if (l == null)
            return null;
        l.area = style;
        return l;
    }

    public HairLineBucket addHairLineBucket(int level, LineStyle style) {
        HairLineBucket ll = getHairLineBucket(level);
        if (ll == null)
            return null;
        ll.line = style;

        return ll;
    }

    public CircleBucket addCircleBucket(int level, CircleStyle style) {
        CircleBucket l = (CircleBucket) getBucket(level, CIRCLE);
        if (l == null)
            return null;
        l.circle = style;
        return l;
    }

    public LineBucket getLineBucket(int level) {
        return (LineBucket) getBucket(level, LINE);
    }

    public MeshBucket getMeshBucket(int level) {
        return (MeshBucket) getBucket(level, MESH);
    }

    public PolygonBucket getPolygonBucket(int level) {
        return (PolygonBucket) getBucket(level, POLYGON);
    }

    public LineTexBucket getLineTexBucket(int level) {
        return (LineTexBucket) getBucket(level, TEXLINE);
    }

    public HairLineBucket getHairLineBucket(int level) {
        return (HairLineBucket) getBucket(level, HAIRLINE);
    }

    public CircleBucket getCircleBucket(int level) {
        return (CircleBucket) getBucket(level, CIRCLE);
    }

    public void set(RenderBucket buckets) {
        for (RenderBucket l = this.buckets; l != null; l = l.next)
            l.clear();

        this.buckets = buckets;
    }

    public RenderBucket get() {
        return buckets;
    }

    private RenderBucket getBucket(int level, int type) {
        RenderBucket bucket = null;

        if (mCurBucket != null && mCurBucket.level == level) {
            bucket = mCurBucket;
            if (bucket.type != type) {
                log.severe("BUG wrong bucket " + bucket.type + " " + type + " on level " + level);
                throw new IllegalArgumentException();
            }
            return bucket;
        }

        RenderBucket b = buckets;
        if (b == null || b.level > level) {
            b = null;
        } else {
            if (mCurBucket != null && level > mCurBucket.level)
                b = mCurBucket;

            while (true) {
                if (b.level == level) {
                    bucket = b;
                    break;
                }
                if (b.next == null || b.next.level > level)
                    break;

                b = b.next;
            }
        }

        if (bucket == null) {
            if (type == LINE)
                bucket = new LineBucket(level);
            else if (type == POLYGON)
                bucket = new PolygonBucket(level);
            else if (type == TEXLINE)
                bucket = new LineTexBucket(level);
            else if (type == MESH)
                bucket = new MeshBucket(level);
            else if (type == HAIRLINE)
                bucket = new HairLineBucket(level);
            else if (type == CIRCLE)
                bucket = new CircleBucket(level);

            if (bucket == null)
                throw new IllegalArgumentException();

            if (b == null) {
                bucket.next = buckets;
                buckets = bucket;
            } else {
                bucket.next = b.next;
                b.next = bucket;
            }
        }

        if (bucket.type != type) {
            log.severe("BUG wrong bucket " + bucket.type + " " + type + " on level " + level);
            throw new IllegalArgumentException();
        }

        mCurBucket = bucket;

        return bucket;
    }

    private int countVboSize() {
        int vboSize = 0;

        for (RenderBucket l = buckets; l != null; l = l.next)
            vboSize += l.numVertices * VERTEX_CNT[l.type];

        return vboSize;
    }

    private int countIboSize() {
        int numIndices = 0;

        for (RenderBucket l = buckets; l != null; l = l.next)
            numIndices += l.numIndices;

        return numIndices;
    }

    public void setFrom(RenderBuckets buckets) {
        if (buckets == this)
            throw new IllegalArgumentException("Cannot set from oneself!");

        set(buckets.buckets);

        mCurBucket = null;
        buckets.buckets = null;
        buckets.mCurBucket = null;
    }

    public void clear() {
        set(null);
        mCurBucket = null;

        vbo = BufferObject.release(vbo);
        ibo = BufferObject.release(ibo);
    }

    public void clearBuckets() {
        for (RenderBucket l = buckets; l != null; l = l.next)
            l.clear();

        mCurBucket = null;
    }

    @Override
    protected void dispose() {
        clear();
    }

    public void prepare() {
        for (RenderBucket l = buckets; l != null; l = l.next)
            l.prepare();
    }

    public void bind() {
        if (vbo != null)
            vbo.bind();

        if (ibo != null)
            ibo.bind();
    }

    public boolean compile(boolean addFill) {

        int vboSize = countVboSize();

        if (vboSize <= 0) {
            vbo = BufferObject.release(vbo);
            ibo = BufferObject.release(ibo);
            return false;
        }

        if (addFill)
            vboSize += TILE_FILL_VERTICES * 2;

        ShortBuffer vboData = MapRenderer.getShortBuffer(vboSize);

        if (addFill)
            vboData.put(fillShortCoords, 0, TILE_FILL_VERTICES * 2);

        ShortBuffer iboData = null;

        int iboSize = countIboSize();
        if (iboSize > 0) {
            iboData = MapRenderer.getShortBuffer(iboSize);
        }

        int pos = addFill ? TILE_FILL_VERTICES : 0;

        for (RenderBucket l = buckets; l != null; l = l.next) {
            if (l.type == POLYGON) {
                l.compile(vboData, iboData);
                l.vertexOffset = pos;
                pos += l.numVertices;
            }
        }

        offset[LINE] = vboData.position() * SHORT_BYTES;
        pos = 0;
        for (RenderBucket l = buckets; l != null; l = l.next) {
            if (l.type == LINE) {
                l.compile(vboData, iboData);

                l.vertexOffset = pos;
                pos += l.numVertices;
            }
        }

        for (RenderBucket l = buckets; l != null; l = l.next) {
            if (l.type != LINE && l.type != POLYGON) {
                l.compile(vboData, iboData);
            }
        }

        if (vboSize != vboData.position()) {
            log.fine("wrong vertex buffer size: "
                    + " new size: " + vboSize
                    + " buffer pos: " + vboData.position()
                    + " buffer limit: " + vboData.limit()
                    + " buffer fill: " + vboData.remaining());
            return false;
        }

        if (iboSize > 0 && iboSize != iboData.position()) {
            log.fine("wrong indice buffer size: "
                    + " new size: " + iboSize
                    + " buffer pos: " + iboData.position()
                    + " buffer limit: " + iboData.limit()
                    + " buffer fill: " + iboData.remaining());
            return false;
        }

        if (vbo == null)
            vbo = BufferObject.get(GL.ARRAY_BUFFER, vboSize);

        vbo.loadBufferData(vboData.flip(), vboSize * SHORT_BYTES);

        if (iboSize > 0) {
            if (ibo == null)
                ibo = BufferObject.get(GL.ELEMENT_ARRAY_BUFFER, iboSize);

            ibo.loadBufferData(iboData.flip(), iboSize * SHORT_BYTES);
        }

        return true;
    }

    private static short[] fillShortCoords;

    static {
        short s = (short) (Tile.SIZE * COORD_SCALE);
        fillShortCoords = new short[]{0, s, s, s, 0, 0, s, 0};
    }

    public static void initRenderer() {
        LineBucket.Renderer.init();
        LineTexBucket.Renderer.init();
        PolygonBucket.Renderer.init();
        TextureBucket.Renderer.init();
        BitmapBucket.Renderer.init();
        MeshBucket.Renderer.init();
        HairLineBucket.Renderer.init();
        CircleBucket.Renderer.init();
    }
}
