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
		File[] mediaDirs = app.getExternalMediaDirs();
		if ( mediaDirs.length > 0 ) {
			return mediaDirs[0].toString();
		}
		return null;
	}

	private static void copyAssets( MainApplication app, String parent ) {
		try {
			AssetManager assetManager = app.getAssets();
			String[] files = assetManager.list( parent );
			for ( int i = 0; i < Objects.requireNonNull( files ).length; i++ ) {
				File file = new File( files[i] );
				if ( Objects.requireNonNull( assetManager.list(parent + File.separator + files[i] ) ).length > 0 ) {
					File dir = getOutFile( getMediaDirPath( app ), parent, file );
					if ( null != dir && ! dir.exists() ) {
						dir.mkdirs();
					}
					copyAssets( app, parent + File.separator + files[i] );
				} else {
					String mediaDirPath = getMediaDirPath( app );
					if ( null != mediaDirPath ) {
						File outFile = getOutFile( mediaDirPath, parent, file );
						if ( ! outFile.exists() ) {
							InputStream in = assetManager.open( parent + file.getAbsolutePath() );;
							FileOutputStream out = new FileOutputStream( outFile );;
							outFile.getParentFile().mkdirs();
							copyFile( in, out );
							in.close();
							out.flush();
							out.close();
						}
					}
				}
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	private static File getOutFile( String mediaDirPath, String parent, File file ) {
		if ( null == mediaDirPath ) {
			return null;
		}
		String[] parentArr = parent.split( File.separator );
		parentArr = Arrays.copyOfRange( parentArr, 1, parentArr.length );
		return new File(mediaDirPath + File.separator + String.join( File.separator, parentArr ) + file.getAbsolutePath() );
	}

	private static void copyFile( InputStream in, FileOutputStream out ) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while( ( read = in.read( buffer ) ) != -1 ){
			out.write(buffer, 0, read);
		}
	}

	public static void init(MainApplication app ) {
		String mediaDirPath = getMediaDirPath( app );
		if ( null != mediaDirPath ) {
			File mediaDir = new File( mediaDirPath );
			if ( ! mediaDir.exists() ) {
				mediaDir.mkdirs();
			}
			copyAssets( app, "dummy" );
		}
		copyAssets( app, "dummy" );
	}

}
