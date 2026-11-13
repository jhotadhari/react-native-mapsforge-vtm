/*
 * Derived from org.oscim.android.cache.TileCache (vtm, LGPL-3.0+,
 * Copyright 2013 Hannes Janetzek, 2016 Stephan Leuschner, 2016 devemux86),
 * Copyright 2026 jhotadhari.
 *
 * Differences: enforces a real byte budget. vtm's TileCache.setCacheSize
 * only clears the table when passed 0 and otherwise does nothing (see
 * https://github.com/mapsforge/vtm/discussions/1212), so the on-disk cache
 * grows without bound. Here setCacheSize keeps that clear-on-<=0 semantics
 * but a positive value sets a byte budget that is enforced after every save
 * by evicting the least-recently-accessed tiles.
 *
 * Local, opt-in replacement: wired in Utils.buildTileCache. Delete this
 * class once an upstream vtm release implements the same limit.
 */
package com.jhotadhari.reactnative.mapsforge.vtm;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;

import org.oscim.core.Tile;
import org.oscim.tiling.ITileCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SizeLimitedTileCache implements ITileCache {

	private static final Logger log = Logger.getLogger(SizeLimitedTileCache.class.getName());
	static final boolean dbg = false;

	/** How many least-recently-accessed tiles to evict per save while over budget. */
	private static final int EVICT_BATCH = 32;

	class CacheTileReader implements TileReader {
		final InputStream inputStream;
		final Tile tile;

		public CacheTileReader(Tile tile, InputStream is) {
			this.tile = tile;
			this.inputStream = is;
		}

		@Override
		public Tile getTile() {
			return tile;
		}

		@Override
		public InputStream getInputStream() {
			return inputStream;
		}
	}

	class CacheTileWriter implements TileWriter {
		final ByteArrayOutputStream outputStream;
		final Tile tile;

		CacheTileWriter(Tile tile, ByteArrayOutputStream os) {
			this.tile = tile;
			this.outputStream = os;
		}

		@Override
		public Tile getTile() {
			return tile;
		}

		@Override
		public OutputStream getOutputStream() {
			return outputStream;
		}

		@Override
		public void complete(boolean success) {
			saveTile(tile, outputStream, success);
		}
	}

	private final ArrayList<ByteArrayOutputStream> cacheBuffers;
	private final SQLiteHelper dbHelper;
	private final SQLiteDatabase database;
	private final SQLiteStatement stmtGetTile;
	private final SQLiteStatement stmtPutTile;
	private final SQLiteStatement stmtUpdateAccess;

	/** Sum of the size column of all cached rows; guarded by this. */
	private long totalBytes;

	/** 0 = unlimited (no eviction). */
	private long maxCacheBytes;

	public void dispose() {
		if (database.isOpen())
			database.close();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public SizeLimitedTileCache(Context context, String cacheDirectory, String dbName) {
		if (dbg)
			log.fine("open cache " + cacheDirectory + ", " + dbName);

		if (cacheDirectory != null)
			dbName = new File(cacheDirectory, dbName).getAbsolutePath();
		dbHelper = new SQLiteHelper(context, dbName);

		if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN)
			dbHelper.setWriteAheadLoggingEnabled(true);

		database = dbHelper.getWritableDatabase();

		stmtGetTile = database.compileStatement("" +
			"SELECT " + COLUMN_DATA +
			" FROM " + TABLE_NAME +
			" WHERE x=? AND y=? AND z = ?");

		stmtPutTile = database.compileStatement("" +
			"INSERT OR REPLACE INTO " + TABLE_NAME +
			" (x, y, z, time, last_access, size, data)" +
			" VALUES(?,?,?,?,?,?,?)");

		stmtUpdateAccess = database.compileStatement("" +
			"UPDATE " + TABLE_NAME +
			"  SET " + COLUMN_ACCESS + "=?" +
			"  WHERE x=? AND y=? AND z=?");

		cacheBuffers = new ArrayList<ByteArrayOutputStream>();

		// Seed the running total. Cheap: reads the size column, never the blobs.
		totalBytes = querySingleLong("SELECT COALESCE(SUM(" + COLUMN_SIZE + "),0) FROM " + TABLE_NAME, null);
	}

	@Override
	public TileWriter writeTile(Tile tile) {
		ByteArrayOutputStream os;

		synchronized (cacheBuffers) {
			if (cacheBuffers.size() == 0)
				os = new ByteArrayOutputStream(32 * 1024);
			else
				os = cacheBuffers.remove(cacheBuffers.size() - 1);
		}
		return new CacheTileWriter(tile, os);
	}

	static final String TABLE_NAME = "tiles";
	static final String COLUMN_TIME = "time";
	static final String COLUMN_ACCESS = "last_access";
	static final String COLUMN_SIZE = "size";
	static final String COLUMN_DATA = "data";

	class SQLiteHelper extends SQLiteOpenHelper {

		private static final int DATABASE_VERSION = 2;

		private static final String TILE_SCHEMA =
			"CREATE TABLE "
				+ TABLE_NAME + "("
				+ "x INTEGER NOT NULL,"
				+ "y INTEGER NOT NULL,"
				+ "z INTEGER NOT NULL,"
				+ COLUMN_TIME + " LONG NOT NULL,"
				+ COLUMN_ACCESS + " LONG NOT NULL,"
				+ COLUMN_SIZE + " LONG NOT NULL,"
				+ COLUMN_DATA + " BLOB,"
				+ "PRIMARY KEY(x,y,z));";

		public SQLiteHelper(Context context, String dbName) {
			super(context, dbName, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			log.fine("create table");
			db.execSQL(TILE_SCHEMA);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion < 2) {
				// v1 -> v2: add the size column and backfill it from the blob
				// length so existing caches keep their tiles (vtm's TileCache
				// drops the table on any upgrade).
				db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_SIZE + " LONG NOT NULL DEFAULT 0");
				db.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_SIZE + " = length(" + COLUMN_DATA + ")");
				return;
			}
			log.fine("drop table");
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}
	}

	public synchronized void saveTile(Tile tile, ByteArrayOutputStream data, boolean success) {
		byte[] bytes = null;

		if (success)
			bytes = data.toByteArray();

		synchronized (cacheBuffers) {
			data.reset();
			cacheBuffers.add(data);
		}

		if (dbg)
			log.fine("store tile " + tile + " " + success);

		if (!success)
			return;

		// Accounting for INSERT OR REPLACE: subtract a replaced row's old size.
		final long oldSize = querySingleLong(
			"SELECT " + COLUMN_SIZE + " FROM " + TABLE_NAME + " WHERE z=? AND x=? AND y=?",
			new String[]{String.valueOf(tile.zoomLevel), String.valueOf(tile.tileX), String.valueOf(tile.tileY)});

		synchronized (stmtPutTile) {
			stmtPutTile.bindLong(1, tile.tileX);
			stmtPutTile.bindLong(2, tile.tileY);
			stmtPutTile.bindLong(3, tile.zoomLevel);
			stmtPutTile.bindLong(4, System.currentTimeMillis());
			stmtPutTile.bindLong(5, System.currentTimeMillis());
			stmtPutTile.bindLong(6, bytes.length);
			stmtPutTile.bindBlob(7, bytes);

			stmtPutTile.execute();
			stmtPutTile.clearBindings();
		}

		totalBytes = totalBytes - oldSize + bytes.length;

		enforceMaxSizeLocked();
	}

	private final String[] queryVals = new String[3];

	@Override
	public synchronized TileReader getTile(Tile tile) {

		queryVals[0] = String.valueOf(tile.zoomLevel);
		queryVals[1] = String.valueOf(tile.tileX);
		queryVals[2] = String.valueOf(tile.tileY);

		Cursor cursor = database.rawQuery("SELECT " + COLUMN_DATA +
			" FROM " + TABLE_NAME +
			" WHERE z=? AND x=? AND y=?", queryVals);

		if (!cursor.moveToFirst()) {
			if (dbg)
				log.fine("not in cache " + tile);

			cursor.close();
			return null;
		}

		InputStream in = new ByteArrayInputStream(cursor.getBlob(0));
		cursor.close();

		// Refresh recency so LRU eviction keeps whatever region is being panned.
		synchronized (stmtUpdateAccess) {
			stmtUpdateAccess.bindLong(1, System.currentTimeMillis());
			stmtUpdateAccess.bindLong(2, tile.tileX);
			stmtUpdateAccess.bindLong(3, tile.tileY);
			stmtUpdateAccess.bindLong(4, tile.zoomLevel);
			stmtUpdateAccess.execute();
			stmtUpdateAccess.clearBindings();
		}

		if (dbg)
			log.fine("load tile " + tile);

		return new CacheTileReader(tile, in);
	}

	/**
	 * 0 or less clears the cache, matching vtm's TileCache.setCacheSize.
	 * A positive value sets the byte budget, prunes immediately and enforces
	 * the budget after every save.
	 */
	@Override
	public synchronized void setCacheSize(long size) {
		if (size <= 0) {
			database.execSQL("DELETE FROM " + TABLE_NAME);
			totalBytes = 0;
		} else {
			maxCacheBytes = size;
			enforceMaxSizeLocked();
		}
	}

	private void enforceMaxSizeLocked() {
		if (maxCacheBytes <= 0)
			return;
		while (totalBytes > maxCacheBytes) {
			if (!evictOldestLocked(EVICT_BATCH))
				return;
		}
	}

	/**
	 * Evicts up to {@code batch} least-recently-accessed rows, always keeping
	 * at least one row (a single tile larger than the budget stays cached).
	 *
	 * @return true if anything was evicted.
	 */
	private boolean evictOldestLocked(int batch) {
		final long rowCount = querySingleLong("SELECT COUNT(*) FROM " + TABLE_NAME, null);
		if (rowCount <= 1)
			return false;

		final List<long[]> doomed = new ArrayList<>();
		Cursor c = database.rawQuery("SELECT x,y,z," + COLUMN_SIZE +
			" FROM " + TABLE_NAME +
			" ORDER BY " + COLUMN_ACCESS + " ASC, " + COLUMN_TIME + " ASC, rowid ASC LIMIT " + batch, null);
		if (c.moveToFirst()) {
			do {
				long[] row = {c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3)};
				doomed.add(row);
			} while (c.moveToNext());
		}
		c.close();

		final int deletable = (int) Math.min(doomed.size(), rowCount - 1);
		if (deletable <= 0)
			return false;

		long freed = 0;
		database.beginTransaction();
		try {
			for (int i = 0; i < deletable; i++) {
				long[] row = doomed.get(i);
				database.delete(TABLE_NAME, "x=? AND y=? AND z=?",
					new String[]{String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2])});
				freed += row[3];
			}
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}

		totalBytes = Math.max(0, totalBytes - freed);
		return true;
	}

	private long querySingleLong(String sql, String[] args) {
		final Cursor c = database.rawQuery(sql, args);
		long value = 0;
		if (c.moveToFirst())
			value = c.getLong(0);
		c.close();
		return value;
	}
}
