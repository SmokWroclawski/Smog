package ovh.olo.smok.smokwroclawski.Service;

import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ovh.olo.smok.smokwroclawski.Activity.DeviceActivity;
import ovh.olo.smok.smokwroclawski.Parser.PacketParser;
import ovh.olo.smok.smokwroclawski.R;
import ovh.olo.smok.smokwroclawski.Refresher;
import ovh.olo.smok.smokwroclawski.ThingSpeak.SendingQueue;

public class ChatService extends Service {
	private final static String TAG = ChatService.class.getSimpleName();

	private String mDeviceName;
	private String mDeviceAddress;
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<UUID, BluetoothGattCharacteristic>();

	private PacketParser packetParser;
	private SendingQueue sendingQueue;

	private final static String MAGIC_WORD = "measure";

	private Refresher refresher;


	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((RBLService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				stopSelf();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(mDeviceAddress);

		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), R.string.gatt_disconnected, Toast.LENGTH_SHORT).show();
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				getGattService(mBluetoothLeService.getSupportedGattService());

				refresher.start();

			} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent.getByteArrayExtra(RBLService.EXTRA_DATA));
			}
		}
	};

	public void requestData() {
		sendData(MAGIC_WORD);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int toReturn = super.onStartCommand(intent, flags, startId);

		mDeviceAddress = intent.getStringExtra(DeviceActivity.EXTRA_DEVICE_ADDRESS);
		mDeviceName = intent.getStringExtra(DeviceActivity.EXTRA_DEVICE_NAME);
		return toReturn;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		System.out.println("DEVICE ACTIVTY!");

		refresher = new Refresher(ChatService.this);

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		packetParser = new PacketParser();
		sendingQueue = new SendingQueue();

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

	}

	private void close() {
		unregisterReceiver(mGattUpdateReceiver);
		unbindService(mServiceConnection);
		mBluetoothLeService.disconnect();
		mBluetoothLeService.close();
	}

	@Override
	public void onDestroy() {
		System.out.println("ONDESTROY!");
		super.onDestroy();
		close();
		this.stopSelf();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}

	private void sendData(String str) {
		BluetoothGattCharacteristic characteristic = map
				.get(RBLService.UUID_BLE_SHIELD_TX);

		byte[] tmp = str.getBytes();
		characteristic.setValue(tmp);
		mBluetoothLeService.writeCharacteristic(characteristic);
	}

	private void displayData(byte[] byteArray) {
		if (byteArray == null) return;

		String data = new String(byteArray);
		if(data.equals("error")) {
//			textViewAnswer.append(mDeviceName + ": " + data + "\n");
		} else {
			refresher.stop();
			sendingQueue.send(packetParser.parsePacket(byteArray));

			this.stopSelf();
			sendBroadcast();
		}
	}

	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null) return;

		BluetoothGattCharacteristic characteristic = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
		map.put(characteristic.getUuid(), characteristic);

		BluetoothGattCharacteristic characteristicRx = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx,
				true);
		mBluetoothLeService.readCharacteristic(characteristicRx);

	}

	private IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);

		return intentFilter;
	}

	private void sendBroadcast (){
		Intent intent = new Intent ("message");
		sendBroadcast(intent);
	}
}