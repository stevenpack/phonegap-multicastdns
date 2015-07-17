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

		if (action.equals("query")) {
			final Context context = this.cordova.getActivity().getApplicationContext();
			final String host = data.getString(0);
			final String multicastIP = data.getString(1);
            final int port = data.getInt(2);
			Log.d(TAG, String.format("Will query for %s on %s:%s", host, multicastIP, port));
			final CallbackContext cb = callbackContext;
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					try {
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
	}
}