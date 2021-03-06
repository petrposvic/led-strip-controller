package cz.posvic.ledstripcontroller;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chiralcode.colorpicker.ColorPickerDialog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = MainActivity.class.getName();
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// My bluetooth device (on LED strip) address
	private static final String ADDRESS = "00:00:12:09:18:48";

	private final int RECEIVE_MESSAGE = 1;

	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothSocket mBluetoothSocket;
	private ConnectedThread mConnectedThread;

	private StringBuilder sb = new StringBuilder();
	private Handler mReceiveHandler;

	private ProgressDialog mProgress;
	private TextView tvState;
	private int mLastColor = Color.RED;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		tvState = (TextView) findViewById(R.id.tvState);

		final Button butTurnOffStrip = (Button) findViewById(R.id.butTurnOffStrip);
		final Button butSetColor = (Button) findViewById(R.id.butSetColor);
		final SeekBar seekSpeed = (SeekBar) findViewById(R.id.seekSpeed);

		butTurnOffStrip.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("1" + (char) 0 + (char) 0 + (char) 0);
				butTurnOffStrip.setEnabled(false);
				butSetColor.setEnabled(true);
			}
		});

		butSetColor.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ColorPickerDialog colorPickerDialog = new ColorPickerDialog(MainActivity.this, mLastColor, new ColorPickerDialog.OnColorSelectedListener() {
					@Override
					public void onColorSelected(int color) {
						mLastColor = color;
						Log.d(TAG, Color.red(color) + ", " + Color.green(color) + ", " + Color.blue(color));

						bluetoothSend(
								"1" +
								(char) (Color.red(color) / 2) +
								(char) (Color.green(color) / 2) +
								(char) (Color.blue(color) / 2)
						);
						butTurnOffStrip.setEnabled(true);
					}
				});
				colorPickerDialog.show();
			}
		});

		seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				Log.d(TAG, "-- onProgressChanged(...," + progress + "," + fromUser + ") --");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				int progress = seekBar.getProgress();
				Log.d(TAG, "-- onStopTrackingTouch(...) --");
				Log.d(TAG, "progress = " + progress);

				if (progress == 0) {
					bluetoothSend("1" + (char) 0 + (char) 0 + (char) 0);
					butTurnOffStrip.setEnabled(false);
					butSetColor.setEnabled(true);
				} else {
					bluetoothSend("0" + progress);
					butTurnOffStrip.setEnabled(true);
					butSetColor.setEnabled(true);
				}
			}
		});

		// --------------------------------------------------------------------

		mReceiveHandler = new Handler() {
			public void handleMessage(android.os.Message msg) {
				switch (msg.what) {
					case RECEIVE_MESSAGE:
						byte[] buffer = (byte[]) msg.obj;
						sb.append(new String(buffer, 0, msg.arg1));

						int endOfLineIndex = sb.indexOf("\r\n");
						if (endOfLineIndex > 0) {
							Log.d(TAG, "received = " + sb.substring(0, endOfLineIndex));
							sb.delete(0, sb.length());
						}

						break;
				}
			}
		};

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Create bluetooth socket
		try {
			mBluetoothSocket = createBluetoothSocket(mBluetoothAdapter.getRemoteDevice(ADDRESS));
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}

		// Discovery is resource intensive. Make sure it isn't going on
		// when you attempt to connect and pass your message.
		mBluetoothAdapter.cancelDiscovery();

		new BluetoothConnect().execute();
	}

	private class BluetoothConnect extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected void onPreExecute() {
			mProgress = ProgressDialog.show(MainActivity.this, "Connecting", "Please wait");
			mProgress.setCancelable(true);

			tvState.setText(R.string.bt_state_connecting);
		}

		@Override
		protected Boolean doInBackground(Void... params) {

			// Establish the connection. This will block until it connects.
			try {
				mBluetoothSocket.connect();
				return Boolean.TRUE;
			} catch (IOException e1) {
				Log.e(TAG, e1.toString());
				try {
					mBluetoothSocket.close();
				} catch (IOException e2) {
					Log.e(TAG, e2.toString());
				}
			}

			return Boolean.FALSE;
		}

		@Override
		protected void onPostExecute(Boolean success) {
			mProgress.dismiss();

			if (success) {
				tvState.setText(R.string.bt_state_connected);

				mConnectedThread = new ConnectedThread(mBluetoothSocket);
				mConnectedThread.start();
			} else {
				tvState.setText(R.string.bt_state_disconnected);
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "-- onPause() --");

		// Close bluetooth socket
		try {
			tvState.setText(R.string.bt_state_disconnected);
			mBluetoothSocket.close();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_settings) {
			Toast.makeText(MainActivity.this, "Created by Petr Pošvic", Toast.LENGTH_SHORT).show();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	// ------------------------------------------------------------------------
	// Bluetooth
	// ------------------------------------------------------------------------

	private void bluetoothSend(String str) {
		Log.d(TAG, "-- bluetoothSend(" + str + ") --");

		if (mConnectedThread == null) {
			Log.e(TAG, "no bluetooth connection");
			return;
		}

		mConnectedThread.write(str);
	}

	private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
		if (Build.VERSION.SDK_INT >= 10){
			try {
				final Method m = device
						.getClass()
						.getMethod(
								"createInsecureRfcommSocketToServiceRecord",
								new Class[] { UUID.class }
						);
				return (BluetoothSocket) m.invoke(device, MY_UUID);
			} catch (Exception e) {
				Log.e(TAG, "Could not create Insecure RFComm Connection", e);
			}
		}

		return  device.createRfcommSocketToServiceRecord(MY_UUID);
	}

	private class ConnectedThread extends Thread {
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				// Ignore
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			byte[] buffer = new byte[256];
			int bytes;

			while (true) {
				try {
					bytes = mmInStream.read(buffer);
					mReceiveHandler.obtainMessage(
							RECEIVE_MESSAGE,
							bytes, -1, buffer
					).sendToTarget();
				} catch (IOException e) {
					break;
				}
			}
		}

		public void write(String message) {
			Log.d(TAG, "...Data to send: " + message + "...");
			byte[] msgBuffer = message.getBytes();
			try {
				mmOutStream.write(msgBuffer);
			} catch (IOException e) {
				Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
			}
		}
	}
}
