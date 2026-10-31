package com.jhotadhari.reactnative.mapsforge.vtm.gnss;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wraps Android's rotation-vector sensor (fused accelerometer + gyro +
 * magnetometer) to deliver the device's compass azimuth: the direction
 * the device's top edge faces, in degrees 0-360 clockwise from magnetic
 * north — ready to be emitted as an {@code onDeviceAzimuth} event.
 *
 * <p>Emissions are throttled to whole-degree changes and at most every
 * {@link #MIN_EMIT_INTERVAL_MS} (the native marker rotation is quantized
 * to whole degrees anyway); nothing is emitted while the device lies
 * still. Events arrive on the main looper.
 *
 * <p>Devices without a rotation-vector sensor never emit:
 * {@link #create} returns {@code null} there and the consumer falls back
 * to a static north marker.
 */
public class CompassManager implements SensorEventListener {

	/** Callback invoked on azimuth changes (throttled, whole degrees). */
	public interface Callback {
		void onAzimuth(double degrees);
	}

	/** Min interval between emissions (ms) — caps the bridge rate at
	 * ~30 events/sec even while spinning the device fast. */
	private static final long MIN_EMIT_INTERVAL_MS = 33;

	private final SensorManager sensorManager;
	private final Sensor rotationVectorSensor;
	private final Callback callback;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private volatile boolean started;

	private int lastEmittedDegrees = Integer.MIN_VALUE;
	private long lastEmitAt = 0L;

	private final float[] rotationMatrix = new float[16];
	private final float[] orientation = new float[3];

	/**
	 * @return a configured instance, or {@code null} when the device has
	 *         no rotation-vector sensor (compass events stay disabled).
	 */
	@Nullable
	public static CompassManager create(
		@NonNull SensorManager sensorManager,
		@NonNull Callback callback
	) {
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		if (sensor == null) {
			android.util.Log.w("CompassManager",
				"Device has no rotation-vector sensor — compass events disabled");
			return null;
		}
		return new CompassManager(sensorManager, sensor, callback);
	}

	private CompassManager(
		@NonNull SensorManager sensorManager,
		@NonNull Sensor rotationVectorSensor,
		@NonNull Callback callback
	) {
		this.sensorManager = sensorManager;
		this.rotationVectorSensor = rotationVectorSensor;
		this.callback = callback;
	}

	/** Start delivering azimuth events. Idempotent. */
	public void start() {
		if (started) return;
		sensorManager.registerListener(
			this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME, mainHandler
		);
		started = true;
	}

	/** Stop delivering azimuth events. Idempotent. */
	public void stop() {
		if (!started) return;
		sensorManager.unregisterListener(this);
		started = false;
		lastEmittedDegrees = Integer.MIN_VALUE;
		lastEmitAt = 0L;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

		// getOrientation on the rotation-vector matrix directly yields the
		// device heading: the angle between magnetic north and the device's
		// Y axis (top edge), 0=north, 90=east, clockwise positive. No
		// remapCoordinateSystem here — the rotation-vector sensor is
		// expressed in the device frame regardless of screen orientation,
		// and a remap would rotate the result onto another device axis.
		SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
		SensorManager.getOrientation(rotationMatrix, orientation);

		double degrees = Math.toDegrees(orientation[0]);
		if (degrees < 0) degrees += 360;
		int rounded = (int) Math.round(degrees);
		if (rounded >= 360) rounded = 0;

		if (rounded == lastEmittedDegrees) return;
		long now = SystemClock.uptimeMillis();
		if (lastEmitAt != 0L && now - lastEmitAt < MIN_EMIT_INTERVAL_MS) return;

		lastEmittedDegrees = rounded;
		lastEmitAt = now;
		callback.onAzimuth(rounded);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
