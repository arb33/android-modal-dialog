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

import uk.ac.cam.arb33.modaldialog.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main_activity);
		((Button) findViewById(R.id.clear)).setOnClickListener(mClearListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		ForegroundActivity.onResume(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		ForegroundActivity.onPause(this);
	}

	/**
	 * A call-back for when the user presses the clear button.
	 */
	OnClickListener mClearListener = new OnClickListener() {

		public void onClick(final View v) {
			Log.i("Skeleton", "Blocking...");
			String title = "This app wants to use your contacts";
			String[] options = {"Grant access", "Mock access", "Exit application"};
			int decision = ModalDialog.newInstance(title, options, 1).block();
			String toastText = "Selected: " + options[decision];
			Toast.makeText(ForegroundActivity.getCurrentActivity(), toastText, Toast.LENGTH_SHORT).show();
			Log.i("Skeleton", "Finished...");
		}
	};
}
