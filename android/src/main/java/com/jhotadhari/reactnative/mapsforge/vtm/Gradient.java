package com.jhotadhari.reactnative.mapsforge.vtm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;

public class Gradient {

	int[] colors_;
	float[] positions_;
	Bitmap bitmap;

	public Gradient(
		int[] colors,
		float[] positions
	) {
		colors_ = colors;
		positions_ = positions;
		initBitmap();
	}

	protected void initBitmap() {

		// Spread positions between 0 and 1.
		float[] positionsAdjusted = new float[positions_.length];
		for ( int i = 0; i < positions_.length; i++ ) {
			positionsAdjusted[i] = ( positions_[0] < 0
				? positions_[i] + ( positions_[0] * - 1 )
				: positions_[i] ) * ( 100 / ( positions_[positions_.length-1] - positions_[0] ) ) / 100;
		}

		// Define rect. As width as needed to cover all positions.
		RectF rect = new RectF(
			0,
			3,
			positions_[positions_.length-1] - positions_[0] + 1,
			0
		);

		// Paint with gradient. Gradient has size of rect and colors at adjusted positions.
		Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
		paint.setShader( new LinearGradient(
			rect.left,
			rect.top,
			rect.right,
			rect.bottom,
			colors_,
			positionsAdjusted,
			Shader.TileMode.CLAMP
		) );

		// Bitmap in size of rect
		bitmap = Bitmap.createBitmap(
			(int) ( positions_[positions_.length-1] - positions_[0] + 1),
			3,
			Bitmap.Config.ARGB_8888
		);

		// Canvas to draw rect with paint on. Holds the bitmap.
		Canvas canvas = new Canvas( bitmap );
		canvas.drawRect( rect, paint );

//		try (FileOutputStream out = new FileOutputStream("/storage/emulated/0/Documents/test.png")) {
//			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//			// PNG is a lossless format, the compression factor (100) is ignored
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

	}

	public int getColorAtPosition( int position ) {

		if ( position < positions_[0] ) {
			return colors_[0];
		}
		if ( position > positions_[positions_.length-1] ) {
			return colors_[positions_.length-1];
		}

		int positionAdjusted = ( positions_[0] < 0
			? position + ( (int) positions_[0] * - 1 )
			: position );

		if ( positionAdjusted < bitmap.getWidth() && ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) ) {
			return bitmap.getColor( (int) positionAdjusted, (int) 1 ).toArgb();
		} else {
			return 0;
		}
	}
}
