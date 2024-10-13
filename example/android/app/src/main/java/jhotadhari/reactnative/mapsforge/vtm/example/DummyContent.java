package jhotadhari.reactnative.mapsforge.vtm.example;

import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class DummyContent {

	public static String getMediaDirPath( MainApplication app ) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return Arrays.stream(app.getExternalMediaDirs()).findFirst().get().toString();
		}
		return null;
	}

	// https://gist.github.com/thinzaroo/5aef6e81638529a89995
	private static void copyAssets( MainApplication app ) {
		AssetManager assetManager = app.getAssets();
		String[] files = null;
		try {
			files = assetManager.list("dummy");
		} catch (IOException e) {
			Log.e("TTEST", e.getMessage() );
			Log.e("TTEST", "Failed to get asset file list.", e);
		}

		for(String filename : files) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = assetManager.open("dummy/" + filename);
				File outFile = new File(getMediaDirPath(app) + "/dummy/" + filename);
				outFile.getParentFile().mkdirs();
				out = new FileOutputStream(outFile);
				copyFile(in, out);
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;
			} catch( IOException e) {
				Log.e("TTEST", e.getMessage() );
				Log.e("TTEST", "Failed to copy asset file: " + filename );
			}
		}
	}

	private static void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while((read = in.read(buffer)) != -1){
			out.write(buffer, 0, read);
		}
	}

	public static void maybeInit( MainApplication app ) {
		File mediaDummyDir = new File(getMediaDirPath(app) + "/dummy" );
		if ( ! mediaDummyDir.exists() ) {
			copyAssets( app );
		}
	}


}
