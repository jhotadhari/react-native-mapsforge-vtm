package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import org.oscim.backend.CanvasAdapter;

public class CanvasAdapterModule extends ReactContextBaseJavaModule {

	public CanvasAdapterModule( ReactApplicationContext context) {
		super(context);
	}

	@NonNull
	@Override
	public String getName() {
		return "CanvasAdapterModule";
	}

	@ReactMethod
	public void setTextScale( float scale ) {
		CanvasAdapter.textScale = scale;
	}

	@ReactMethod
	public void setLineScale( float scale ) {
		CanvasAdapter.lineScale = scale;
	}

	@ReactMethod
	public void setSymbolScale( float scale ) {
		CanvasAdapter.symbolScale = scale;
	}

}
