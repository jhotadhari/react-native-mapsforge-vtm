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
 * sets a byte budget. Eviction runs on a dedicated background trimmer
 * thread (bounded chunks, never on the caller thread — eager full pruning of
 * a far-over-budget cache OOM'd the native allocator on device, and doing it
 * on the save path caused 600 ms tile-loader contention).
 *
 * Initialization (schema v1->v2 migration, index, seed) also runs on the
 * background thread; until it finishes, reads miss and writes are skipped.
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

	/** Max rows the trimmer examines per pass; it stops early once under budget. */
	private static final int EVICT_BATCH = 16;

	/** Sleep between trimmer passes while over budget (also yields I/O). */
	private static final long TRIM_SLEEP_MS = 250;

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

	private final ArrayList<ByteArrayOutputStream> cacheBuffers = new ArrayList<ByteArrayOutputStream>();
	private final Object dbLock = new Object();

	/** Guarded by dbLock; null until init completes. */
	private SQLiteDatabase database;

	private SQLiteStatement stmtPutTile;
	private SQLiteStatement stmtUpdateAccess;

	/** Sum of the size column of all cached rows; guarded by dbLock. */
	private long totalBytes;

	/** 0 = unlimited (no eviction); volatile, set from setCacheSize. */
	private volatile long maxCacheBytes;

	private volatile boolean ready = false;
	private volatile boolean closed = false;

	private SQLiteHelper dbHelper;
	private Thread worker;

	private long saveCount;
	private long evictCount;

	public void dispose() {
		closed = true;
		synchronized (dbLock) {
			if (database != null && database.isOpen()) {
				try {
					database.close();
				} catch (Exception ignored) {
				}
				database = null;
			}
		}
	}

	public SizeLimitedTileCache(Context context, String cacheDirectory, String dbName) {
		if (cacheDirectory != null)
			dbName = new File(cacheDirectory, dbName).getAbsolutePath();
		Log.i(TAG, "constructing " + dbName);

		final Context appContext = context.getApplicationContext();
		final String dbPath = dbName;
		worker = new Thread(new Runnable() {
			@Override
			public void run() {
				initialize(appContext, dbPath);
				trimLoop();
			}
		}, "SizeLimitedTileCache");
		worker.setDaemon(true);
		worker.start();
	}

	/**
	 * Runs on the worker thread: opens the database (schema migration),
	 * creates the eviction index, seeds the running total, then hands over.
	 */
	private void initialize(Context context, String dbPath) {
		try {
			long t0 = System.currentTimeMillis();
			dbHelper = new SQLiteHelper(context, dbPath);
			if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN)
				dbHelper.setWriteAheadLoggingEnabled(true);

			SQLiteDatabase db = dbHelper.getWritableDatabase();
			Log.i(TAG, "db opened in " + (System.currentTimeMillis() - t0) + " ms");

			try {
				// Eviction orders by last_access; without an index that is a
				// full-table sort on every eviction pass (pathological on
				// gigabyte-sized caches).
				db.execSQL("CREATE INDEX IF NOT EXISTS idx_tiles_last_access ON " + TABLE_NAME + " (" + COLUMN_ACCESS + ")");
			} catch (SQLiteException e) {
				Log.w(TAG, "create index failed, eviction will be slow: " + e);
			}

			SQLiteStatement putStmt = db.compileStatement("" +
				"INSERT OR REPLACE INTO " + TABLE_NAME +
				" (x, y, z, time, last_access, size, data)" +
				" VALUES(?,?,?,?,?,?,?)");

			SQLiteStatement updateStmt = db.compileStatement("" +
				"UPDATE " + TABLE_NAME +
				"  SET " + COLUMN_ACCESS + "=?" +
				"  WHERE x=? AND y=? AND z=?");

			long rows = querySingleLong(db, "SELECT COUNT(*) FROM " + TABLE_NAME, null);
			long sum = querySingleLong(db, "SELECT COALESCE(SUM(" + COLUMN_SIZE + "),0) FROM " + TABLE_NAME, null);

			synchronized (dbLock) {
				database = db;
				stmtPutTile = putStmt;
				stmtUpdateAccess = updateStmt;
				totalBytes = sum;
				ready = true;
				dbLock.notifyAll();
			}
			Log.i(TAG, "opened " + dbPath + " rows=" + rows + " totalBytes=" + sum);
		} catch (Throwable t) {
			Log.e(TAG, "init failed — disk cache disabled: " + t, t);
			ready = false;
			closed = true;
		}
	}

	/**
	 * Runs on the worker thread after init: while over budget, evict one
	 * bounded chunk per pass, then sleep. Never on the caller thread.
	 */
	private void trimLoop() {
		while (!closed) {
			if (ready && maxCacheBytes > 0) {
				synchronized (dbLock) {
					if (database != null && totalBytes > maxCacheBytes) {
						try {
							final long before = totalBytes;
							evictOldestLocked(EVICT_BATCH);
							evictCount++;
							Log.i(TAG, "trim pass " + evictCount + " freed=" + (before - totalBytes)
								+ " total=" + totalBytes + " budget=" + maxCacheBytes);
						} catch (SQLiteException e) {
							Log.e(TAG, "eviction failed: " + e);
						}
					}
				}
			}
			try {
				Thread.sleep(TRIM_SLEEP_MS);
			} catch (InterruptedException ignored) {
			}
		}
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

	public void saveTile(Tile tile, ByteArrayOutputStream data, boolean success) {
		// Capture before reset(): the buffer is pooled below.
		final byte[] bytes = success ? data.toByteArray() : null;

		// Buffer pooling must always happen, whatever the SQL below does.
		synchronized (cacheBuffers) {
			data.reset();
			cacheBuffers.add(data);
		}

		if (!success)
			return;

		synchronized (dbLock) {
			if (database == null)
				return;

			try {
				// Accounting for INSERT OR REPLACE: subtract a replaced row's old size.
				final long oldSize = querySingleLong(database,
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

				saveCount++;
				if ((saveCount & 127) == 0)
					Log.i(TAG, "saved " + saveCount + " tiles, totalBytes=" + totalBytes);
				if (oldSize > 0 && (saveCount & 63) == 0)
					Log.i(TAG, "replaced " + tile + " oldSize=" + oldSize + " newSize=" + bytes.length);
			} catch (SQLiteException e) {
				Log.e(TAG, "saveTile failed for " + tile + ": " + e);
			}
		}
	}

	private final String[] queryVals = new String[3];

	@Override
	public TileReader getTile(Tile tile) {

		queryVals[0] = String.valueOf(tile.zoomLevel);
		queryVals[1] = String.valueOf(tile.tileX);
		queryVals[2] = String.valueOf(tile.tileY);

		synchronized (dbLock) {
			if (database == null)
				return null;

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
	}

	/**
	 * 0 or less clears the cache, matching vtm's TileCache.setCacheSize.
	 * A positive value sets the byte budget; the background trimmer enforces
	 * it continuously.
	 */
	@Override
	public void setCacheSize(long size) {
		if (size <= 0) {
			synchronized (dbLock) {
				if (database != null) {
					try {
						database.execSQL("DELETE FROM " + TABLE_NAME);
					} catch (SQLiteException e) {
						Log.e(TAG, "clear failed: " + e);
					}
					totalBytes = 0;
				}
			}
		} else {
			maxCacheBytes = size;
			Log.i(TAG, "cacheSize set to " + size + " bytes");
		}
	}

	/**
	 * Evicts up to {@code batch} least-recently-accessed rows, always keeping
	 * at least one row (a single tile larger than the budget stays cached).
	 * Caller must hold dbLock.
	 *
	 * @return true if anything was evicted.
	 */
	private boolean evictOldestLocked(int batch) {
		final SQLiteDatabase db = database;
		if (db == null)
			return false;

		final long rowCount = querySingleLong(db, "SELECT COUNT(*) FROM " + TABLE_NAME, null);
		if (rowCount <= 1)
			return false;

		final List<long[]> doomed = new ArrayList<>();
		Cursor c = db.rawQuery("SELECT x,y,z," + COLUMN_SIZE +
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
		int deleted = 0;
		db.beginTransaction();
		try {
			for (int i = 0; i < deletable; i++) {
				// Stop as soon as we are within budget: evicting a fixed batch
				// here emptied a ~50 MB cache in one pass (only ~200-300 rows
				// fit at that size), which looked like the cache clearing itself.
				if (totalBytes - freed <= maxCacheBytes)
					break;
				long[] row = doomed.get(i);
				db.delete(TABLE_NAME, "x=? AND y=? AND z=?",
					new String[]{String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2])});
				freed += row[3];
				deleted++;
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		totalBytes = Math.max(0, totalBytes - freed);
		return deleted > 0;
	}

	private static long querySingleLong(SQLiteDatabase db, String sql, String[] args) {
		final Cursor c = db.rawQuery(sql, args);
		long value = 0;
		if (c.moveToFirst())
			value = c.getLong(0);
		c.close();
		return value;
	}
}
