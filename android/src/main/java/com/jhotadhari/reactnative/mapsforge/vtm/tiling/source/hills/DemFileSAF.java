package com.jhotadhari.reactnative.mapsforge.vtm.tiling.source.hills;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.mapsforge.core.util.IOUtils;
import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFileFS;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

public class DemFileSAF implements DemFile {

	private static final Logger LOGGER = Logger.getLogger(DemFileSAF.class.getName());

	final private String filePath;
	final private Context context;

	public DemFileSAF(Context context, String filePath) {
		this.context = context;
		this.filePath = filePath;
	}

	@Override
	public String getName() {
		DocumentFile documentFile = DocumentFile.fromTreeUri(context, Uri.parse(filePath));
		return documentFile.getName();
	}

	@Override
	public InputStream openInputStream() throws FileNotFoundException {
		return new BufferedInputStream( context.getContentResolver().openInputStream( Uri.parse( filePath ) ) );
	}

	@Override
	public long getSize() {
		DocumentFile documentFile = DocumentFile.fromTreeUri(context, Uri.parse(filePath));
		return documentFile.length();
	}

	@Override
	public ByteBuffer asByteBuffer() throws IOException {
		FileChannel channel = null;
		FileInputStream stream = null;
		try {
			DocumentFile documentFile = DocumentFile.fromTreeUri(context, Uri.parse(filePath));
			String nameLowerCase = documentFile.getName().toLowerCase();
			if (nameLowerCase.endsWith(".zip")) {
				return DemFileFS.tryZippedSingleHgt(documentFile.getName(), (FileInputStream) context.getContentResolver().openInputStream( Uri.parse( filePath ) ));
			} else {
				FileInputStream fileInputStream = (FileInputStream) context.getContentResolver().openInputStream( Uri.parse( filePath ) );
				channel = fileInputStream.getChannel();
				stream = fileInputStream;
				ByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, documentFile.length());
				map.order(ByteOrder.BIG_ENDIAN);
				return map;
			}
		} finally {
			IOUtils.closeQuietly(channel);
			IOUtils.closeQuietly(stream);
		}
	}
}
