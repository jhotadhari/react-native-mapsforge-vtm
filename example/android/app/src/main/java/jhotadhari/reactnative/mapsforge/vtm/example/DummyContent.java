package jhotadhari.reactnative.mapsforge.vtm.example;

import android.content.res.AssetManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

public class DummyContent {

	public static String getMediaDirPath( MainApplication app ) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return Arrays.stream(app.getExternalMediaDirs()).findFirst().get().toString();
		}
		return null;
	}

	private static void copyAssets( MainApplication app, String parent ) {
		try {
			AssetManager assetManager = app.getAssets();
			String[] files = assetManager.list( parent );
			for ( int i = 0; i < Objects.requireNonNull( files ).length; i++ ) {
				if ( Objects.requireNonNull( assetManager.list(parent + "/" + files[i] ) ).length > 0 ) {
					copyAssets( app, parent + "/" + files[i] );
				} else {
					File file = new File( files[i] );
					String[] parentArr = parent.split( "/" );
					parentArr = Arrays.copyOfRange( parentArr, 1, parentArr.length );
					File outFile = new File(getMediaDirPath( app ) + "/" + String.join( "/", parentArr ) + file.getAbsolutePath() );
					if ( ! outFile.exists() ) {
						InputStream in = assetManager.open( parent + file.getAbsolutePath() );;
						OutputStream out = new FileOutputStream( outFile );;
						outFile.getParentFile().mkdirs();
						copyFile( in, out );
						in.close();
						out.flush();
						out.close();
					}
				}
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	private static void copyFile( InputStream in, OutputStream out ) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while( ( read = in.read( buffer ) ) != -1 ){
			out.write(buffer, 0, read);
		}
	}

	public static void init(MainApplication app ) {
		File mediaDir = new File( getMediaDirPath( app ) );
		if ( ! mediaDir.exists() ) {
			mediaDir.mkdirs();
		}
		copyAssets( app, "dummy" );
	}

}
