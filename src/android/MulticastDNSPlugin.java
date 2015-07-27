package com.koalasafe.cordova.plugin.multicastdns;

import android.content.Context;
import android.util.Log;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

public class MulticastDNSPlugin extends CordovaPlugin {
	private static final String TAG = "MulticastDNSPlugin";

	@Override
	public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
		try {
			Log.i(TAG, "Entered plugin execute. Action is: " + action);
			if (action.equals("query")) {
				Log.v(TAG, "Getting context...");
				final Context context = this.cordova.getActivity().getApplicationContext();
				Log.v(TAG, "Getting host...");
				final String host = data.getString(0);
				Log.v(TAG, "Getting multicastIP...");
				final String multicastIP = data.getString(1);
				Log.v(TAG, "Getting port...");
				final int port = data.getInt(2);
				Log.i(TAG, String.format("Will query for %s on %s:%s", host, multicastIP, port));
				final CallbackContext cb = callbackContext;
				Log.i(TAG, "Starting new thread for request");
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try {
							Log.i(TAG, "New thread for request");
							MulticastDnsRequestor r = new MulticastDnsRequestor(multicastIP, port, context);
							String answer = r.query(host);
							cb.success(answer); // Thread-safe.
						} catch (Exception ex) {
							Log.e(TAG, "Failed to lookup host. " + ex.getStackTrace());
							cb.error(ex.getMessage());
						}
					}
				});
				return true;
			} else {
				callbackContext.error("unknown action: " + action);
				return false;
			}
		} catch (Exception ex) {
			callbackContext.error("Failed to execute plugin: " + ex.getMessage());
			return false;
		}
	}
}