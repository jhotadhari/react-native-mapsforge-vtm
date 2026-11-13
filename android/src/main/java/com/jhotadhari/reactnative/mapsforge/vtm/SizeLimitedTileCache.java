/*
 * Derived from org.oscim.android.cache.TileCache (vtm, LGPL-3.0+,
 * Copyright 2013 Hannes Janetzek, 2016 Stephan Leuschner, 2016 devemux86),
 * Copyright 2026 jhotadhari.
 *
 * Differences: enforces a real byte budget. vtm's TileCache.setCacheSize
 * only clears the table when passed 0 and otherwise does nothing (see
 * https://github.com/mapsforge/vtm/discussions/1212), so the on-disk cache
 * grows without bound (hillshading caches reaching the gigabyte range).
 * Here setCacheSize keeps that clear-on-<=0 semantics but a positive value
 * sets a byte budget that is enforced lazily after every save by evicting a
 * bounded batch of the least-recently-accessed tiles (an over-budget cache
 * converges over subsequent saves; eager full pruning of a far-over-budget
 * cache OOMs on the caller thread).
 *
 * All SQL errors are swallowed and logged: a tile cache must never break or
 * storm the tile pipeline.
 *
 * Local, opt-in replacement: wired in Utils.buildTileCache. Delete this
 * class once an upstream vtm release implements the same limit.
 */
package com.jhotadhari.reactnative.mapsforge.vtm;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.util.Log;

import org.oscim.core.Tile;
import org.oscim.tiling.ITileCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SizeLimitedTileCache implements ITileCache {

	private static final String TAG = "SizeLimitedTileCache";

	/** How many least-recently-accessed tiles to evict per save while over budget. */
	private static final int EVICT_BATCH = 256;

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
	private final SQLiteStatement stmtPutTile;
	private final SQLiteStatement stmtUpdateAccess;

	/** Sum of the size column of all cached rows; guarded by this. */
	private long totalBytes;

	/** 0 = unlimited (no eviction). */
	private long maxCacheBytes;

	private long saveCount;

	private long evictCount;

	public void dispose() {
		if (database.isOpen())
			database.close();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public SizeLimitedTileCache(Context context, String cacheDirectory, String dbName) {
		if (cacheDirectory != null)
			dbName = new File(cacheDirectory, dbName).getAbsolutePath();
		dbHelper = new SQLiteHelper(context, dbName);

		if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN)
			dbHelper.setWriteAheadLoggingEnabled(true);

		database = dbHelper.getWritableDatabase();

		try {
			// Eviction orders by last_access; without an index that is a
			// full-table sort on every eviction pass (pathological on
			// gigabyte-sized caches).
			database.execSQL("CREATE INDEX IF NOT EXISTS idx_tiles_last_access ON " + TABLE_NAME + " (" + COLUMN_ACCESS + ")");
		} catch (SQLiteException e) {
			Log.w(TAG, "create index failed, eviction will be slow: " + e);
		}

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
		long rows = 0;
		long sum = 0;
		try {
			sum = querySingleLong("SELECT COALESCE(SUM(" + COLUMN_SIZE + "),0) FROM " + TABLE_NAME, null);
			rows = querySingleLong("SELECT COUNT(*) FROM " + TABLE_NAME, null);
		} catch (SQLiteException e) {
			Log.e(TAG, "open/seed failed: " + e);
		}
		totalBytes = sum;
		Log.i(TAG, "opened " + dbName + " rows=" + rows + " totalBytes=" + sum);
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
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}
	}

	public synchronized void saveTile(Tile tile, ByteArrayOutputStream data, boolean success) {
		// Capture before reset(): the buffer is pooled below.
		final byte[] bytes = success ? data.toByteArray() : null;

		// Buffer pooling must always happen, whatever the SQL below does.
		synchronized (cacheBuffers) {
			data.reset();
			cacheBuffers.add(data);
		}

		if (!success)
			return;

		try {
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

			saveCount++;
			if ((saveCount & 127) == 0)
				Log.i(TAG, "saved " + saveCount + " tiles, totalBytes=" + totalBytes);
		} catch (SQLiteException e) {
			Log.e(TAG, "saveTile failed for " + tile + ": " + e);
		}
	}

	private final String[] queryVals = new String[3];

	@Override
	public synchronized TileReader getTile(Tile tile) {

		queryVals[0] = String.valueOf(tile.zoomLevel);
		queryVals[1] = String.valueOf(tile.tileX);
		queryVals[2] = String.valueOf(tile.tileY);

		try {
			Cursor cursor = database.rawQuery("SELECT " + COLUMN_DATA +
				" FROM " + TABLE_NAME +
				" WHERE z=? AND x=? AND y=?", queryVals);

			if (!cursor.moveToFirst()) {
				cursor.close();
				return null;
			}

			InputStream in = new ByteArrayInputStream(cursor.getBlob(0));
			cursor.close();

			// Refresh recency so LRU eviction keeps whatever region is being panned.
			try {
				synchronized (stmtUpdateAccess) {
					stmtUpdateAccess.bindLong(1, System.currentTimeMillis());
					stmtUpdateAccess.bindLong(2, tile.tileX);
					stmtUpdateAccess.bindLong(3, tile.tileY);
					stmtUpdateAccess.bindLong(4, tile.zoomLevel);
					stmtUpdateAccess.execute();
					stmtUpdateAccess.clearBindings();
				}
			} catch (SQLiteException e) {
				Log.w(TAG, "last_access update failed for " + tile + ": " + e);
			}

			return new CacheTileReader(tile, in);
		} catch (SQLiteException e) {
			Log.e(TAG, "getTile failed for " + tile + ": " + e);
			return null;
		}
	}

	/**
	 * 0 or less clears the cache, matching vtm's TileCache.setCacheSize.
	 * A positive value sets the byte budget, enforced lazily after each save
	 * (bounded work per save — a far-over-budget cache converges over the
	 * next writes instead of blocking the caller on one huge prune).
	 */
	@Override
	public synchronized void setCacheSize(long size) {
		if (size <= 0) {
			try {
				database.execSQL("DELETE FROM " + TABLE_NAME);
			} catch (SQLiteException e) {
				Log.e(TAG, "clear failed: " + e);
			}
			totalBytes = 0;
		} else {
			maxCacheBytes = size;
			Log.i(TAG, "cacheSize set to " + size + " bytes");
		}
	}

	/**
	 * Lazy enforcement: evict at most one batch of least-recently-accessed
	 * rows per call, so a cache far over a newly lowered budget converges over
	 * subsequent saves with bounded work on the caller thread.
	 */
	private void enforceMaxSizeLocked() {
		if (maxCacheBytes <= 0 || totalBytes <= maxCacheBytes)
			return;
		try {
			if (evictOldestLocked(EVICT_BATCH)) {
				evictCount++;
				if ((evictCount & 31) == 0)
					Log.i(TAG, "evicted (pass " + evictCount + "), totalBytes=" + totalBytes + " budget=" + maxCacheBytes);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, "eviction failed: " + e);
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
