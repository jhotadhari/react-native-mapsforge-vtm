package com.jhotadhari.reactnative.mapsforge.vtm;

import com.goebl.simplify.Point;

import org.joda.time.DateTime;

public class Coordinate extends org.locationtech.jts.geom.Coordinate implements Point {

	public DateTime dateTime;

	public Coordinate(double x, double y, double z) {
		super( x, y, z );
	}

	public Coordinate(double x, double y, double z, DateTime dateTime) {
		super( x, y, z );
		this.dateTime = dateTime;
	}

	@Override
	public double getX() {
		return x;
	}

	@Override
	public double getY() {
		return y;
	}
}
