/*
 * Copyright (C) 2013 Alastair R. Beresford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.cam.arb33.modaldialog;

import java.util.ArrayList;

import android.app.Activity;
import android.util.Log;

public class ForegroundActivity {

	private final static String TAG = ForegroundActivity.class.getName();
	private final static ArrayList<Activity> activities = new ArrayList<Activity>();
	
	public static void onResume(Activity activity) {
		activities.add(activity);
	}
	
	public static void onPause(Activity activity) {
		if(!activities.remove(activity)) {
			Log.e(TAG, "Attempting to pause an activity which as not resumed. Implies Dexter did not instrument app correctly.");
		}
	}

	public static Activity getCurrentActivity() {
		if (activities.size() != 1) {
			throw new RuntimeException("Invariant activities.size() == 1 has failed:" + activities.size());
		}
		return activities.get(0);
	}
}
