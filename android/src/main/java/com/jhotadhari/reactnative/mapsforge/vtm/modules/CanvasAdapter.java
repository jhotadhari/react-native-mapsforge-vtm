package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeCanvasAdapterSpec;

@ReactModule( name = CanvasAdapter.NAME )
public class CanvasAdapter extends NativeCanvasAdapterSpec {

	public static final String NAME = "CanvasAdapter";

	public CanvasAdapter( ReactApplicationContext reactContext ) {
		super( reactContext );
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void setTextScale( double scale ) {
		org.oscim.backend.CanvasAdapter.textScale = (float) scale;
	}

	@Override
	public void setLineScale( double scale ) {
		org.oscim.backend.CanvasAdapter.lineScale = (float) scale;
	}

	@Override
	public void setSymbolScale( double scale ) {
		org.oscim.backend.CanvasAdapter.symbolScale = (float) scale;
	}

}
