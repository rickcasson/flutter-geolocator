package com.baseflow.geolocator;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.baseflow.geolocator.errors.ErrorCodes;
import com.baseflow.geolocator.errors.PermissionUndefinedException;
import com.baseflow.geolocator.permission.PermissionManager;
import com.baseflow.geolocator.nmea.NmeaManager;
import com.baseflow.geolocator.nmea.NmeaClient;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;

public class NmeaStreamHandlerImpl implements EventChannel.StreamHandler {
  private static final String TAG = "FlutterGeolocator";

  private final NmeaManager nmeaManager;
  private final PermissionManager permissionManager;

  @Nullable private EventChannel channel;
  @Nullable private Context context;
  @Nullable private Activity activity;
  @Nullable private NmeaClient nmeaClient;

  public NmeaStreamHandlerImpl(PermissionManager permissionManager) {
    this.nmeaManager = new NmeaManager();
    this.permissionManager = permissionManager;
  }

  private static Map<String, Object> toMap(String message, Long timestamp) {
    if (message == null || timestamp == null) {
      return null;
    }

    Map<String, Object> nmeaMap = new HashMap<>();

    nmeaMap.put("timestamp", timestamp);
    nmeaMap.put("message", message);

    return nmeaMap;
  }

  void setActivity(@Nullable Activity activity) {

    if (activity == null && nmeaClient != null && channel != null) {
      stopListening();
    }

    this.activity = activity;
  }

  /**
   * Registers this instance as event stream handler on the given {@code messenger}.
   *
   * <p>Stops any previously started and unstopped calls.
   *
   * <p>This should be cleaned with {@link #stopListening} once the messenger is disposed of.
   */
  void startListening(Context context, BinaryMessenger messenger) {
    if (channel != null) {
      Log.w(TAG, "Setting a event call handler before the last was disposed.");
      stopListening();
    }

    channel = new EventChannel(messenger, "flutter.baseflow.com/geolocator_nmea_updates_android");
    channel.setStreamHandler(this);
    this.context = context;
  }

  /**
   * Clears this instance from listening to method calls.
   *
   * <p>Does nothing if {@link #startListening} hasn't been called, or if we're already stopped.
   */
  void stopListening() {
    if (channel == null) {
      Log.d(TAG, "Tried to stop listening when no MethodChannel had been initialized.");
      return;
    }

    disposeListeners();
    channel.setStreamHandler(null);
    channel = null;
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    try {
      if (!permissionManager.hasPermission(this.context)){
        events.error(
            ErrorCodes.permissionDenied.toString(),
            ErrorCodes.permissionDenied.toDescription(),
            null);
        return;
      }
    } catch (PermissionUndefinedException e) {
      events.error(
          ErrorCodes.permissionDefinitionsNotFound.toString(),
          ErrorCodes.permissionDefinitionsNotFound.toDescription(),
          null);
      return;
    }

    this.nmeaClient = nmeaManager.createNmeaClient(this.context);

    nmeaManager.startNmeaUpdates(
        this.nmeaClient,
        activity,
        (String message, long timestamp) -> events.success(toMap(message, timestamp)),
        (ErrorCodes errorCodes) ->
            events.error(errorCodes.toString(), errorCodes.toDescription(), null));
  }

  @Override
  public void onCancel(Object arguments) {
    if (this.nmeaClient != null) {
      disposeListeners();
    }
  }

  private void disposeListeners() {
    Log.e(TAG, "Geolocator nmea updates stopped");
    if (nmeaClient != null && nmeaManager != null) {
      nmeaManager.stopNmeaUpdates(nmeaClient);
      nmeaClient = null;
    }
  }
}