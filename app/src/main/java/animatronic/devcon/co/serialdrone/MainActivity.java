package animatronic.devcon.co.serialdrone;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;
import java.util.Set;

import animatronic.devcon.co.serialdrone.adapter.LogAdapter;
import animatronic.devcon.co.serialdrone.model.MLog;
import animatronic.devcon.co.serialdrone.service.RCService;
import animatronic.devcon.co.serialdrone.service.UsbService;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MAIN";

    private LogAdapter mAdapter;

    private UsbService usbService;
    private MyHandler mHandler;

    private MqttAndroidClient mMQTTClient;
    private final static String MQTT_SERVER = "tcp://xfero.xyz:1883";
    private final static String MQTT_TOPIC_CONTROL = "devcon/drone/control";
    private final static String MQTT_TOPIC_RESPOND = "devcon/drone/respond";
    private final static String MQTT_TOPIC_RC = "devcon/drone/rc";

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
            addLog("Service Connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
            addLog("Service Disconnected");
        }
    };

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    addLog("USB Ready");
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    addLog("USB Permission not granted");
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    addLog("No USB connected");
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    addLog("USB disconnected");
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    addLog("USB device not supported");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupLogView();

        addLog("STARTING...");

        mqttConnect();
        mHandler = new MyHandler(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mqttDisconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private void setupLogView() {

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv_log);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        mAdapter = new LogAdapter(getApplicationContext());

        recyclerView.setAdapter(mAdapter);
    }

    private void addLog(String desc) {
        mAdapter.add(new MLog(desc));
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    byte[] data = (byte[]) msg.obj;
                    addLog("USB READ: " + new String(data));
                    publishMessage(data);
                    break;
                case UsbService.CTS_CHANGE:
                    addLog("CTS_CHANGE");
                    break;
                case UsbService.DSR_CHANGE:
                    addLog("DSR_CHANGE");
                    break;
            }
        }
    }

    private void mqttConnect(){
        String clientId = MqttClient.generateClientId();
        mMQTTClient = new MqttAndroidClient(getApplicationContext(), MQTT_SERVER, clientId);
        mMQTTClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if(topic.equalsIgnoreCase(MQTT_TOPIC_CONTROL)) {
                    addLog(MQTT_TOPIC_CONTROL + ": " + message.toString());
                    if (usbService != null) { // if UsbService was correctly binded, Send data
                        addLog("USB WRITE: " + message.toString());
                        usbService.write(message.getPayload());
                    }
                } else if(topic.equalsIgnoreCase(MQTT_TOPIC_RC)) {
                    addLog(MQTT_TOPIC_RC + ": " + message.toString());

                    // stop running service
                    Intent rcService = new Intent(getApplicationContext(), RCService.class);
                    stopService(rcService);
                    // create a new loop when there is data by passing the data
                    if(message.getPayload().length != 0) {
                        rcService.putExtra(RCService.KEY_DATA, message.getPayload());
                        startService(rcService);
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        try {
            IMqttToken token = mMQTTClient.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribe(MQTT_TOPIC_CONTROL);
                    subscribe(MQTT_TOPIC_RC);
                    addLog("MQTT Connected");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addLog("MQTT Failure: " + exception.getLocalizedMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void mqttDisconnect() {

        try {
            IMqttToken token = mMQTTClient.disconnect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addLog("MQTT Successfully Connected");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addLog("MQTT Failure Connected");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /***
     * Publish MSP respond to respond channel
     * @param payload
     */
    private void publishMessage(byte[] payload) {
        try {
//            byte[] encodedPayload = payload.getBytes("UTF-8");
            MqttMessage mqttMessage = new MqttMessage(payload);
            mMQTTClient.publish(MQTT_TOPIC_RESPOND, mqttMessage);
            addLog(MQTT_TOPIC_RESPOND + ": " + mqttMessage.toString());
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /***
     * Subscribe to channel
     */
    private void subscribe(final String channel) {
        try {
            IMqttToken token = mMQTTClient.subscribe(channel, 1);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addLog(channel + " subscribed");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addLog("Failed Subscribe");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
