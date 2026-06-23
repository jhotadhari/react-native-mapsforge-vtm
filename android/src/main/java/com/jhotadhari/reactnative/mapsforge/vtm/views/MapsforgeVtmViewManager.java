package com.jhotadhari.reactnative.mapsforge.vtm.views;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewManagerDelegate;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.viewmanagers.MapsforgeVtmViewManagerInterface;
import com.facebook.react.viewmanagers.MapsforgeVtmViewManagerDelegate;

@ReactModule(name = MapsforgeVtmViewManager.NAME)
public class MapsforgeVtmViewManager extends SimpleViewManager<MapsforgeVtmView> implements MapsforgeVtmViewManagerInterface<MapsforgeVtmView> {

	public static final String NAME = "MapsforgeVtmView";

	private final ViewManagerDelegate<MapsforgeVtmView> mDelegate;

	public MapsforgeVtmViewManager() {
		mDelegate = new MapsforgeVtmViewManagerDelegate( this );
	}

	@Override
	public ViewManagerDelegate<MapsforgeVtmView> getDelegate() {
		return mDelegate;
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@NonNull
	@Override
	public MapsforgeVtmView createViewInstance( ThemedReactContext context ) {
		return new MapsforgeVtmView( context );
	}

	@ReactProp( name = "width" )
	@Override
	public void setWidth( MapsforgeVtmView view, double value ) {
		if ( view != null ) {
			view.setDimension( "width", value );
		}
	}

	@ReactProp( name = "height" )
	@Override
	public void setHeight( MapsforgeVtmView view, double value ) {
		if ( view != null ) {
			view.setDimension( "height", value );
		}
	}

	@ReactProp( name = "center" )
	@Override
	public void setCenter( MapsforgeVtmView view, @Nullable ReadableArray value ) {
		if ( null != value && view != null ) {
			view.setCenter( value );
		}
	}

	@ReactProp( name = "zoomLevel" )
	@Override
	public void setZoomLevel( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setZoomLevel( value );
		}
	}

	@ReactProp( name = "zoomMin" )
	@Override
	public void setZoomMin( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setZoomBounds( "min", value );
		}
	}

	@ReactProp( name = "zoomMax" )
	@Override
	public void setZoomMax( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setZoomBounds( "max", value );
		}
	}

	@ReactProp( name = "moveEnabled" )
	@Override
	public void setMoveEnabled( MapsforgeVtmView view, boolean value ) {
		if ( null != view ) {
			view.setInteractionEnabled( "move", value );
		}
	}

	@ReactProp( name = "tiltEnabled" )
	@Override
	public void setTiltEnabled( MapsforgeVtmView view, boolean value ) {
		if ( null != view ) {
			view.setInteractionEnabled( "tilt", value );
		}
	}

	@ReactProp( name = "rotationEnabled" )
	@Override
	public void setRotationEnabled( MapsforgeVtmView view, boolean value ) {
		if ( null != view ) {
			view.setInteractionEnabled( "rotation", value );
		}
	}

	@ReactProp( name = "zoomEnabled" )
	@Override
	public void setZoomEnabled( MapsforgeVtmView view, boolean value ) {
		if ( null != view ) {
			view.setInteractionEnabled( "zoom", value );
		}
	}

	@ReactProp( name = "tilt" )
	@Override
	public void setTilt( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportValue( "tilt", value );
		}
	}

	@ReactProp( name = "minTilt" )
	@Override
	public void setMinTilt( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "tilt", "min", value );
		}
	}

	@ReactProp( name = "maxTilt" )
	@Override
	public void setMaxTilt( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "tilt", "max", value );
		}
	}

	@ReactProp( name = "bearing" )
	@Override
	public void setBearing( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportValue( "bearing", value );
		}
	}

	@ReactProp( name = "minBearing" )
	@Override
	public void setMinBearing( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "bearing", "min", value );
		}
	}

	@ReactProp( name = "maxBearing" )
	@Override
	public void setMaxBearing( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "bearing", "max", value );
		}
	}

	@ReactProp( name = "roll" )
	@Override
	public void setRoll( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportValue( "roll", value );
		}
	}

	@ReactProp( name = "minRoll" )
	@Override
	public void setMinRoll( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "roll", "min", value );
		}
	}

	@ReactProp( name = "maxRoll" )
	@Override
	public void setMaxRoll( MapsforgeVtmView view, double value ) {
		if ( null != view ) {
			view.setViewportBounds( "roll", "max", value );
		}
	}

	@ReactProp( name = "hgtDirPath" )
	@Override
	public void setHgtDirPath( MapsforgeVtmView view, String value ) {
		if ( view != null ) {
			view.setHgtDirPath( value );
		}
	}

	@ReactProp( name = "hgtInterpolation" )
	@Override
	public void setHgtInterpolation( MapsforgeVtmView view, boolean value ) {
		if ( view != null ) {
			view.setHgtInterpolation( value );
		}
	}

	@ReactProp( name = "hgtReadFileRate" )
	@Override
	public void setHgtReadFileRate( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setHgtReadFileRate( value );
		}
	}

	@ReactProp( name = "hgtFileInfoPurgeThreshold" )
	@Override
	public void setHgtFileInfoPurgeThreshold( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setFileInfoPurgeThreshold( value );
		}
	}

	@ReactProp( name = "responseInclude" )
	@Override
	public void setResponseInclude( MapsforgeVtmView view, @Nullable ReadableMap value ) {
		if ( null != value && view != null ) {
			view.setResponseInclude( value );
		}
	}

	@ReactProp( name = "mapEventRate" )
	@Override
	public void setMapEventRate( MapsforgeVtmView view, int value ) {
		if ( view != null ) {
			view.setMapEventRate( value );
		}
	}

	@ReactProp( name = "emitsMapUpdateEvents" )
	@Override
	public void setEmitsMapUpdateEvents( MapsforgeVtmView view, boolean value ) {
		if ( view != null ) {
			view.setEmitsMapUpdateEvents( value );
		}
	}

}
