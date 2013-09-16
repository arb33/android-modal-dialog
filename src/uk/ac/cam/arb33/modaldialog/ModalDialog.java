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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.util.Log;

/**
 * A Modal Dialog. 
 * 
 * Dispatches messages from Android's main event loop whilst displayed so that it can safely block execution
 * of the caller. Default option is selected if the onPause method of the dialog the Activity is attached
 * to is called. This is not a class intended for general use; rather, it is useful when recompiling apps
 * whose source code is not available.
 * 
 * This class makes extensive use of reflection to call internal APIs within the Android framework. Whilst
 * these appear to be stable across releases, they may change at any point in the future. You have been warned.
 *
 */
public class ModalDialog extends DialogFragment {

	private final static String TAG = ModalDialog.class.getCanonicalName();
	private final Looper looper = Looper.getMainLooper();
	private final Handler newEvent = new LocalLoopHandler();
	private final Handler unblockQueue = new LocalLoopHandler();
	private final ArrayList<Message> savedMsgQueue = new ArrayList<Message>();
	private final long ident = Binder.clearCallingIdentity();
	private int chosenOption = -1;

	/**
	 * Dummy handler class whose instances are used to signal special events in the event queue.
	 */
	private static class LocalLoopHandler extends Handler {
		LocalLoopHandler() {
			super(Looper.getMainLooper());
		}
		@Override
		public void handleMessage(Message inputMessage) {
		}
	}

	/**
	 * Recommended method for creating a new ModalDialog.
	 * 
	 * @param title
	 * @param options
	 * @param defaultOptionIndex
	 * @return
	 */
	synchronized static ModalDialog newInstance(String title, String[] options, int defaultOptionIndex) {
		ModalDialog dialog = new ModalDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putCharSequenceArray("options", options);
		args.putInt("defaultOptionIndex", defaultOptionIndex);
		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		final ModalDialog modalDialog = this;
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style.Theme_DeviceDefault);
		String title = getArguments().getString("title");
		CharSequence[] options = getArguments().getCharSequenceArray("options");
		chosenOption = getArguments().getInt("defaultOptionIndex");
		
		builder.setTitle(title)
		.setItems(options, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				modalDialog.unblock(which);
			}
		});
		return builder.create();
	}


	private void unblock(int selectedOption) {		
		addInternalMessageToQueue(unblockQueue, selectedOption, "Attempted to unblock queue");
	}

	private void addInternalMessageToQueue(Handler target, int what, String errorMessage) {
		try {
			Message msg = target.obtainMessage(what);
			msg.sendToTarget();
		} catch (Exception e) {
			Log.e(TAG, "Error: " + errorMessage);
			logException(e);
			unrecoverableErrorSystemExit();
		}				
	}

	private MessageQueue getMessageQueue() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		Field f = looper.getClass().getDeclaredField("mQueue");
		f.setAccessible(true);
		return (MessageQueue) f.get(looper);
	}

	private Message getNextMessage(MessageQueue queue) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Method next = queue.getClass().getDeclaredMethod("next");
		next.setAccessible(true);
		return (Message) next.invoke(queue); //Blocking call
	}

	private void recycleMessage(Message msg) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Method m = msg.getClass().getDeclaredMethod("recycle");
		m.setAccessible(true);
		m.invoke(msg);
	}

	private void invokeDispatchMessage(Message msg) throws NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Handler target = msg.getTarget();
		Method m = target.getClass().getMethod("dispatchMessage", Message.class);
		m.setAccessible(true);
		m.invoke(target, msg);
	}

	private void flipInUseFlag(Message msg) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
		Field f = msg.getClass().getDeclaredField("FLAG_IN_USE");
		f.setAccessible(true);
		final int FLAG_IN_USE = f.getInt(null);
		f = msg.getClass().getDeclaredField("flags");
		f.setAccessible(true);
		int flags = f.getInt(msg);
		flags ^= FLAG_IN_USE;
		f.setInt(msg, flags);
	}

	private void enqueueMessage(MessageQueue queue, Message msg) throws NoSuchFieldException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Field f = msg.getClass().getDeclaredField("when");
		f.setAccessible(true);
		long when = f.getLong(msg);
		Method m = queue.getClass().getDeclaredMethod("enqueueMessage",  Message.class, long.class);
		m.setAccessible(true);
		m.invoke(queue, msg, when);
	}

	private void logException(Exception e) {
		Log.i(TAG, "" + e);
		for(StackTraceElement el: e.getStackTrace())
			Log.i(TAG, "" + el);
		Throwable cause = e.getCause();
		if (cause != null) {
			Log.i(TAG, "" + cause);
			for(StackTraceElement el: cause.getStackTrace())
				Log.i(TAG, "" + el);							
		}		
	}

	private void unrecoverableErrorSystemExit() {
		throw new RuntimeException("Unrecoverable error in Dexter");
	}

	private interface MessageProcessor {
		public void process(Message msg) throws IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException;
	}

	/**
	 * Simulates one run through the message processing loop, as found in android.os.Looper.loop().
	 * 
	 * @param mp implementation of interface which is given the msg object to process.
	 * @param msg msg to process; if null, pull next message from main message loop.
	 * @param a flag indicating whether the message should the message be recycled, as per Android framework requirements.
	 *
	 * @return flag indicating whether the message has been processed or not.
	 */
	private boolean processSingleMessage(MessageProcessor mp, Message msg, boolean recycle) {

		try {
			if (msg == null) {
				MessageQueue mQueue = getMessageQueue();
				msg = getNextMessage(mQueue); //Might block
				if (msg != null 
						&& msg.getTarget().getClass().getCanonicalName().equals("android.app.ActivityThread.H") 
						&& msg.what == 101) {
					flipInUseFlag(msg);
					enqueueMessage(mQueue, msg);
					return false;
				}
			}

			if (msg == null) {//Message Loop quitting
				return false;
			}

			mp.process(msg);

			final long newIdent = Binder.clearCallingIdentity();
			if (ident != newIdent) {
				Log.wtf(TAG, "Thread identity changed from 0x"
						+ Long.toHexString(ident) + " to 0x"
						+ Long.toHexString(newIdent) + " while dispatching");
			}

			if (recycle) {
				recycleMessage(msg);
			}			
		} catch (Exception e) {
			String error = "<null msg>";
			if (msg != null) {
				Handler target = msg.getTarget();
				if (target == null) {
					error = "<null target>";
				} else {
					error = target.getClass().getCanonicalName();
				}
			}
			Log.e(TAG, " Error processing messages: " + error);
			logException(e);
			unrecoverableErrorSystemExit();
		}
		return true;
	}

	/**
	 * Block current execution in event loop until dialog option is selected or onPause method of associated Activity is called.
	 * 
	 * Note: In the case where the onPause method is called, the default option is returned and execution resumes so that
	 * the onPause method can be executed cleanly.
	 * 
	 * @return The index of the option selected by the user (or the default in the case of an onPause event).
	 */
	public int block() {

		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			//TODO(arb33): Insert background blocking feature here rather than exit!
			unrecoverableErrorSystemExit();
		}

		//Save existing messages into a queue and process them later
		addInternalMessageToQueue(newEvent, 1, "Inserted a marker for events we can save for later.");
		super.show(ForegroundActivity.getCurrentActivity().getFragmentManager(), "ModalDialog");		
		final boolean[] saveMsgFlags = {true, true};
		savedMsgQueue.clear();
		while(saveMsgFlags[0] && saveMsgFlags[1]) {
			saveMsgFlags[0] = processSingleMessage(new MessageProcessor() {
				public void process(Message msg) {
					if (msg.getTarget() == newEvent) {
						saveMsgFlags[1] = false;
					} else {
						savedMsgQueue.add(msg);
					}
				}
			}, null, false);
		}

		//Process messages from the dialog box in order to determine option chosen by the user. 
		final boolean[] localLoopFlags = {saveMsgFlags[0], true};
		while(localLoopFlags[0] && localLoopFlags[1]) {			
			localLoopFlags[0] = processSingleMessage(new MessageProcessor() {
				public void process(Message msg) 
						throws IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
					if (msg.getTarget() == unblockQueue) {
						chosenOption = msg.what;
						localLoopFlags[1] = false;
					} else {
						invokeDispatchMessage(msg);
					}
				}
			}, null, true);
		}

		//Replay saved messages from the queue
		for(final Message savedMsg: savedMsgQueue) {
			processSingleMessage(new MessageProcessor() {
				public void process(Message msg) throws IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
					invokeDispatchMessage(msg);}
			}, savedMsg, true);
		}

		if (!localLoopFlags[0]) {
			//TODO(arb33): Display toast saying default selected?
		}
		
		super.dismiss();
		return chosenOption;
	}
}
