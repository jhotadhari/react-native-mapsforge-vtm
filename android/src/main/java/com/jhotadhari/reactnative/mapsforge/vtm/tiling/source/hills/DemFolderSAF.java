package com.jhotadhari.reactnative.mapsforge.vtm.tiling.source.hills;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class DemFolderSAF implements DemFolder {

	public final Context context;
	public final String filePath;

	public DemFolderSAF( Context context, String filePath) {
		this.context = context;
		this.filePath = filePath;
	}

	@Override
	public Iterable<DemFolder> subs() {

		DocumentFile dir = DocumentFile.fromTreeUri(context, Uri.parse(filePath));
		if (!dir.isDirectory()) {
			return Collections.emptyList();
		}

		DocumentFile[] files = dir.listFiles();
		List<DocumentFile> filesFilteredList = new ArrayList<>();
		for ( int i = 0; i < files.length; i++) {
			if ( files[i].isDirectory() ) {
				filesFilteredList.add( files[i] );
			}
		}

		if ( filesFilteredList.isEmpty() ) {
			return Collections.emptyList();
		}

		return new Iterable<DemFolder>() {
			@Override
			public Iterator<DemFolder> iterator() {
				return new Iterator<DemFolder>() {
					int nextidx = 0;

					@Override
					public boolean hasNext() {
						return nextidx < filesFilteredList.size();
					}

					@Override
					public DemFolder next() {
						DemFolderSAF ret = new DemFolderSAF( context, filesFilteredList.get(nextidx).getUri().toString() );
						nextidx++;
						return ret;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	@Override
	public Iterable<DemFile> files() {
		DocumentFile dir = DocumentFile.fromTreeUri(context, Uri.parse(filePath));
		if (!dir.isDirectory()) {
			return Collections.emptyList();
		}

		DocumentFile[] files = dir.listFiles();
		List<DocumentFile> filesFilteredList = new ArrayList<>();
		for ( int i = 0; i < files.length; i++) {
			if ( files[i].isFile() ) {
				filesFilteredList.add( files[i] );
			}
		}

		if ( filesFilteredList.isEmpty() ) {
			return Collections.emptyList();
		}

		return new Iterable<DemFile>() {
			@Override
			public Iterator<DemFile> iterator() {
				return new Iterator<DemFile>() {
					int nextidx = 0;

					@Override
					public boolean hasNext() {
						return nextidx < filesFilteredList.size();
					}

					@Override
					public DemFile next() {
						DemFileSAF ret = new DemFileSAF(context, filesFilteredList.get(nextidx).getUri().toString() );
						nextidx++;
						return ret;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (!(obj instanceof DemFolderSAF)) {
			return false;
		}
		return filePath.equals( ( (DemFolderSAF) obj ).filePath );	// ???
	}
}
