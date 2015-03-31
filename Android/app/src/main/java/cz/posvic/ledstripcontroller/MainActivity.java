package cz.posvic.ledstripcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

	private TextView tvState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		tvState = (TextView) findViewById(R.id.tvState);

		final Button butTurnOffStrip = (Button) findViewById(R.id.butTurnOffStrip);
		final Button butRed = (Button) findViewById(R.id.butRed);
		final Button butGreen = (Button) findViewById(R.id.butGreen);
		final Button butBlue = (Button) findViewById(R.id.butBlue);
		final Button butTurnOnChanging = (Button) findViewById(R.id.butTurnOnChanging);
		final SeekBar seekSpeed = (SeekBar) findViewById(R.id.seekSpeed);

		butTurnOffStrip.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("1");
				butTurnOffStrip.setEnabled(false);
				butRed.setEnabled(true);
				butGreen.setEnabled(true);
				butBlue.setEnabled(true);
				butTurnOnChanging.setEnabled(true);
			}
		});

		butRed.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("1r");
				butTurnOffStrip.setEnabled(true);
				butRed.setEnabled(false);
				butGreen.setEnabled(true);
				butBlue.setEnabled(true);
				butTurnOnChanging.setEnabled(true);
			}
		});

		butGreen.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("1g");
				butTurnOffStrip.setEnabled(true);
				butRed.setEnabled(true);
				butGreen.setEnabled(false);
				butBlue.setEnabled(true);
				butTurnOnChanging.setEnabled(true);
			}
		});

		butBlue.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("1b");
				butTurnOffStrip.setEnabled(true);
				butRed.setEnabled(true);
				butGreen.setEnabled(true);
				butBlue.setEnabled(false);
				butTurnOnChanging.setEnabled(true);
			}
		});

		butTurnOnChanging.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bluetoothSend("0");
				butTurnOffStrip.setEnabled(true);
				butRed.setEnabled(true);
				butGreen.setEnabled(true);
				butBlue.setEnabled(true);
				butTurnOnChanging.setEnabled(false);
				seekSpeed.setProgress(5);
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
					bluetoothSend("1");
					butTurnOffStrip.setEnabled(false);
					butRed.setEnabled(true);
					butGreen.setEnabled(true);
					butBlue.setEnabled(true);
				} else {
					bluetoothSend("0" + progress);
					butTurnOffStrip.setEnabled(true);
					butRed.setEnabled(true);
					butGreen.setEnabled(true);
					butBlue.setEnabled(true);
					butTurnOnChanging.setEnabled(false);
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

		// Establish the connection. This will block until it connects.
		tvState.setText(R.string.bt_state_connecting);
		try {
			mBluetoothSocket.connect();
			tvState.setText(R.string.bt_state_connected);
		} catch (IOException e1) {
			tvState.setText(R.string.bt_state_disconnected);
			Log.e(TAG, e1.toString());
			try {
				mBluetoothSocket.close();
			} catch (IOException e2) {
				Log.e(TAG, e2.toString());
			}
		}

		mConnectedThread = new ConnectedThread(mBluetoothSocket);
		mConnectedThread.start();
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
