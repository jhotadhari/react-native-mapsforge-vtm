package com.jhotadhari.reactnative.mapsforge.vtm;

import org.joda.time.DateTime;

public class Coordinate extends org.locationtech.jts.geom.Coordinate {

	public DateTime dateTime;

	public Coordinate(double x, double y, double z) {
		super( x, y, z );
	}

	public Coordinate(double x, double y, double z, DateTime dateTime) {
		super( x, y, z );
		this.dateTime = dateTime;
	}
}
