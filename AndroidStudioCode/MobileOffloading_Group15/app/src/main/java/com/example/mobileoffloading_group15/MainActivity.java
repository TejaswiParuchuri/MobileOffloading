package com.example.mobileoffloading_group15;


import android.accessibilityservice.FingerprintGestureController;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.method.ScrollingMovementMethod;
import android.os.Handler;
import android.os.Message;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;


public class MainActivity extends AppCompatActivity {

    Button listen,send,listDevices,showconnected,rejectMsg;
    ListView listView;
    TextView msg_box,status,batterystatus;
    EditText matrixSize;

    private FusedLocationProviderClient client;

   //available device information like socket, device name and availability status
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;
    int available_devices=0; //maintained at master to keep track of number of available slaves that were free

    ArrayAdapter<String> arrayAdapter; //maintained at master to show list of available deivces in list view
    ArrayList<BluetoothDevice> stringArrayList=new ArrayList<BluetoothDevice>(); //maintains the list of connected devices

    ArrayList<BluetoothSocket> connected_socket=new ArrayList<BluetoothSocket>(); //maintains the list of connected sockets

    SendReceive sendReceive;

    int rejectMsgFlag=0; //maintained at slave if it has rejected offloading

    public BluetoothServerSocket serverSocket;

    //connections status checks

    static final int STATE_LISTENING =1;
    static final int STATE_CONNECTING=2;
    static final int STATE_CONNECTED=3;
    static final int STATE_CONNECTION_FAILED=4;
    static final int STATE_MESSAGE_RECEIVED=5;
    static final int STATE_BATTERY_LOW=6;
    static final int STATE_DISCONNECTED=7;

    int REQUEST_ENABLE_BLUETOOTH=1;

    //matrices to be sent
    int[][] inputs_A;
    int[][] inputs_B;
    int[][] output_Array;

    int total_rowcount=0; //maintained at master to maintain how many rows were there in matrix
    //int index_inputs=0;
    String battery_details=""; //maitains the information related to battery details so that will be displayed in message box
    int output_rowcount=0; //maintained at master to check how many rows were received

    int broadcasting_started=1; //maintained at master so that if row result was not received even after 5 seconds from one slave it can be sent to other available slaves

    long start_time; //maintained at master to keep track of time at which matrix multiplication was started either by mobile offloading or without offloading
    long finish_time; //maintained at master to keep track of time at which matrix multiplication was completed either by mobile offloading or without offloading

    Map<BluetoothSocket,ArrayList<String>> connection_status=new HashMap<BluetoothSocket, ArrayList<String>>(); //maintained at master to keep track of whether the slave was busy and free
    Map<String,Integer> battery_final=new HashMap<String,Integer>(); //maintained at master to store batter level status of slaves once offloading is done
    Map<String,Integer> battery_initial=new HashMap<String,Integer>(); //maintained at master to store the battery level status of slaves when they are connected
    Map<Integer,Long> row_sent_time=new HashMap<Integer, Long>(); //maintained at master to check at what time row was sent to slave
    ArrayList<Integer> row_check=new ArrayList<Integer>(); //maintained at master to check what all rows are yet to be sent

    Map<Integer,String> outputRows_check=new HashMap<Integer, String>(); //maintained at master to check what all rows were received from slave

    private static final String APP_NAME= "BTChat";
    private static final UUID MY_UUID=UUID.fromString("31645ec6-c5ee-4476-8fde-18c0b4e5afea"); //UUID which helps in connection establishment between master and slave

    int battery_level=0; //maintains the battery level of the slave
    int battery_threshold=40; //battery threshold that should be maintained for the device to do offloading

    GPSTracker gps; //class to get gps location of the device

    String device_name;

    String display_msg=""; //maintains what message to be displayed in message box

    String client_server; //shows if it's client or server

    String location_device="";  //maintains latitude and longitude information
    int battery_check_count=1; //maintained at slave to check when it has clicked reject offloading and trying to accept offloading again whether offloading was already completed or not at master
    int temp_batterycheck;

    int battery_level_start=0;

    SerializationHandler test;
    ProxyResponse response_temp;
    private Handler offloadingHandler=new Handler();  //handles the connection status and messages received
    String connected_device;

    //below broadcast Receiver is getting the battery level of the device. It will always show the latest value of the batter level
    private BroadcastReceiver mBatInfoReceiver= new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            battery_level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
            batterystatus.setText("Battery level: "+String.valueOf(battery_level)+"%");
        }
    };


    /*At master below function will run in back ground at master and once offloading is started, it will keep on checking if the rows that were sent for calculation to slave was received or not.
    If the result of row send by master is not received with in 5 seconds that row will be again sent to slaves using sendMessage function
    It a recovery algorithm used by master*/
    private Runnable  offloadingReceiver=new Runnable() {
        @Override
        public void run() {
            if(broadcasting_started==0 && output_rowcount!=total_rowcount)
            {
                for(int row_number:row_sent_time.keySet())
                {
                    if (!row_check.contains(row_number) && !outputRows_check.containsKey(row_number))
                    {

                        long current_time=System.nanoTime();
                        if(TimeUnit.NANOSECONDS.toMillis(current_time-row_sent_time.get(row_number))>5000)
                        {

                            System.out.println("Row number not sent:"+row_number);
                            row_check.add(row_number);
                            System.out.println(row_check.get(0));
                            sendMessages();
                        }
                    }
                }
            }
            //this will make sure this fuction is always running without any delay when this function is started
            offloadingHandler.postDelayed(this,0);
        }
    };

    /*
    At master this functions will always check if there are any devices available to connect with bluetooth so that the devices can act as slaves. This list will be shown in the list view
    of the screen
     */
    BroadcastReceiver myReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getName()!=null)
                {
                    int count=0;
                    for(int i=0;i<stringArrayList.size();i++)
                    {
                        if(!(stringArrayList.get(i).getName()).equals(device.getName()))
                        {
                            count++;
                        }
                    }
                    if(count==stringArrayList.size())
                    {
                        stringArrayList.add(device);
                    }

                    //System.out.println("Device Name:"+device.getName()+" Device Address:"+device.getAddress());
                    String[] strings=new String[stringArrayList.size()];
                    btArray=new BluetoothDevice[stringArrayList.size()];
                    int index=0;

                    if(stringArrayList.size()>0)
                    {
                        for(BluetoothDevice device1: stringArrayList)
                        {
                            btArray[index]=device1;
                            strings[index]=device1.getName();
                            index++;
                        }

                        arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);

                        //ArrayAdapter<String> arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);
                        listView.setAdapter(arrayAdapter);
                    }
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.mipmap.ic_launcher);
        //adding allDevices will make sure whenever notification will be sent to the device in which this app is installed
        FirebaseMessaging.getInstance().subscribeToTopic("allDevices");
        test = new SerializationHandler();
        //initialize the components of the screen
        findViewByIdes();
        msg_box.setMovementMethod(ScrollingMovementMethod.getInstance());
        //below will help in getting the GPS location of the device
        gps = new GPSTracker(MainActivity.this);
        if(gps.canGetLocation()){

            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();

            location_device=Double.toString(latitude)+","+Double.toString(longitude);

        }
        else{
            gps.showSettingsAlert();
        }


        Toast.makeText(getApplicationContext(),location_device,Toast.LENGTH_LONG).show();

        //Below will enable the bluetooth in the device in case it's disables
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();

        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }

        this.registerReceiver(this.mBatInfoReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        device_name=BluetoothAdapter.getDefaultAdapter().getName(); //get the bluetooth name of the device
        //implements the functionalities of components on screen
        implementListeners();
    }



    private void implementListeners()
    {
        /*
        On clicking this button device will start as master and this functionality is mainly useful at master on clicking which shows list of all the devices available for it to connect.
        Also master will send notification to all the devices in which this app is installed with the help of Firebase Cloud Messaging
         */
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {

                stringArrayList=new ArrayList<BluetoothDevice>();
                bluetoothAdapter.startDiscovery();
                matrixSize.setEnabled(true);

                client_server="client";
                if(client_server=="client" && connected_socket.size()==0)
                {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

                    StrictMode.setThreadPolicy(policy);
                    HttpURLConnection get_connection=null;
                    URL url = null;
                    try {
                        WebView myView=(WebView) findViewById(R.id.webViewId);
                        myView.setVisibility(View.GONE);
                        myView.getSettings().setJavaScriptEnabled(true);
                        myView.setWebViewClient(new WebViewClient());
                        myView.loadUrl("http://192.168.0.81/fcm/send.php?title=Master%20needs%20your%20help%20for%20matrix%20multiplication"); //ip address of device in which local server is started

                    } catch (Exception e) {
                        System.out.println("Exception:"+e);
                        e.printStackTrace();
                    }

                }

                IntentFilter intentFilterConnection=new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(myReceiver,intentFilterConnection);


            }
        });

        /*
        This functionality is enabled in slave. On clicking even though slave is connected to master it can reject offloading request which is matrix multiplication sent by master by not
        calculating and sending the response
        */
        rejectMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //slave has clicked on reject offloading
                if(rejectMsgFlag==0)
                {
                    rejectMsg.setText("AcceptOffloading");
                    rejectMsgFlag=1;
                    temp_batterycheck=battery_check_count;
                }
                else
                {
                    /*
                    If slave again want to accept offloading if still offloading is continuing at master it will send the row result otherwise will ask the master to set it free so
                    that master can use it for the next offloading
                    */
                    if(response_temp!=null && temp_batterycheck==battery_check_count && connected_socket.size()>0)
                    {
                        try {
                            sendReceive.write(test.objectToByteArray(response_temp));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else if(temp_batterycheck!=battery_check_count && connected_socket.size()>0)
                    {
                        try {
                            sendReceive.write(test.objectToByteArray(device_name+":SetMeFree:SetMeFree"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    response_temp=null;
                    rejectMsg.setText("RejectOffloading");
                    rejectMsgFlag=0;
                }
            }
        });

        /*
        On clicking this button, device will start acting as slave. On clicking slave will start make it self discoverable for 5 mins so that any device with same UUID can try to connect to it
         */
        listen.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                rejectMsg.setEnabled(true);


                Intent intent_available=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent_available.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
                startActivity(intent_available);
                client_server="server";
                send.setEnabled(false);
                matrixSize.setEnabled(false);
                ServerClass serverClass=new ServerClass();
                serverClass.start();
            }
        });

        //At master this will shows all the devices that are available for connectivity and always shows the updated list of available devices
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(broadcasting_started==1)
                {
                    send.setEnabled(true);
                }

                client_server="client";

                int flag=0;
                //Below logic will disconnect the slave device if master clicks on the device that was already connected to slave before and even communicates to slave to disonnect
                for(BluetoothSocket temp_socket:connection_status.keySet())
                {
                    if(connection_status.get(temp_socket).get(0).equals(btArray[i].getName()))
                    {
                        flag=1;
                        sendReceive = new SendReceive(temp_socket);
                        sendReceive.start();
                        try {
                            sendReceive.write(test.objectToByteArray(device_name + ":Disconnect:Disconnect"));
                            //temp_socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        if(connection_status.get(temp_socket).get(1)=="free"){
                            available_devices--;
                        }
                        connection_status.remove(temp_socket);
                        connected_socket.remove(temp_socket);
                        status.setText("Disconnected");
                    }

                }
                //If in case master is not connected to that device it will start to connect to slave
                if(flag==0) {
                    ClientClass clientClass = new ClientClass(btArray[i]);
                    clientClass.start();
                    status.setText("Connecting");
                }
                rejectMsg.setEnabled(false);
            }
        });

        /*This functionality is enable in master only.
        On clicking master will send offloading if it has batter level more than threshold
        */

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //If batter level less than threshold disconnect from all the connected slaves and communicate to slaves to disconnect
                if(battery_level<battery_threshold)
                {
                    status.setText("Battery Low can't connect");
                    for(BluetoothSocket socket:connected_socket)
                    {
                        sendReceive = new SendReceive(socket);
                        sendReceive.start();
                        try {
                            sendReceive.write(test.objectToByteArray((device_name + ":Battery Level:Batter level is low").toString()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    connected_socket=new ArrayList<BluetoothSocket>();
                    connection_status=new HashMap<BluetoothSocket, ArrayList<String>>();
                    available_devices=0;

                }
                //If matrix size is entered with in the limits of 2 and 13 and if there are available devices master is ready to offload
                if(matrixSize.getText().toString()!=null && !matrixSize.getText().toString().isEmpty() && available_devices>0 && Integer.parseInt(matrixSize.getText().toString())<13 && Integer.parseInt(matrixSize.getText().toString())>=2) {
                    send.setEnabled(false);
                    output_rowcount = 0;
                    offloadingReceiver.run(); //start failure recovery algorithm in background which will continuosly check if any row result is not received in 5 seconds once sent till offloading is done
                    total_rowcount = Integer.parseInt(matrixSize.getText().toString());

                    for (int i = 0; i < total_rowcount; i++) {
                        row_check.add(i);
                    }

                    //randomly geenrate 2 square matrices of size total_rowcount*total_rowcount
                    inputs_A = new int[total_rowcount][total_rowcount];
                    inputs_B = new int[total_rowcount][total_rowcount];
                    output_Array = new int[total_rowcount][total_rowcount];
                    inputs_A = generateRandom(total_rowcount, total_rowcount);


                    inputs_B = generateRandom(total_rowcount, total_rowcount);

                    //Start sending messages to available slaves
                    if (client_server == "client") {
                        try {

                            battery_level_start=battery_level;
                            start_time = System.nanoTime();
                            sendMessages();
                            broadcasting_started = 0;


                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                //If there are not available devices display the message to master that no devices are available
                else if(available_devices<=0)
                {
                    Toast.makeText(getApplicationContext(),"No devices available",Toast.LENGTH_LONG).show();
                }
                //entered matrix size is not within the limits of 2 & 13
                else if((matrixSize.getText().toString()!=null && !matrixSize.getText().toString().isEmpty()) && (Integer.parseInt(matrixSize.getText().toString())>=13 || Integer.parseInt(matrixSize.getText().toString())<2))
                {
                    Toast.makeText(getApplicationContext(),"Please enter Matrix Size between 2 & 13",Toast.LENGTH_LONG).show();
                }
                //Matrix size is not given
                else
                {
                    Toast.makeText(getApplicationContext(),"Please enter Matrix Size",Toast.LENGTH_LONG).show();
                }
            }
        });
        //on clicking it will show the devices that were connected to this device
        showconnected.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                try
                {
                    connected_device="";

                    for (BluetoothSocket key : connection_status.keySet())
                    {

                        connected_device += connection_status.get(key).get(0) + "\n";
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                if (connected_device!="")
                {
                    msg_box.setText(connected_device.trim());
                }
                else
                {
                    msg_box.setText("No devices connected");
                }
            }
        });
    }

    //sendMessage function will send the row by row of matrixA and entire matrixB and row number as proxyrequest object to available slaves which are free
    private void  sendMessages()
    {
        Log.d("Inside message","I am in sendMessages");
        //If batter level less than threshold disconnect from all the connected slaves and communicate to slaves to disconnect
        if(battery_level<battery_threshold)
        {
            status.setText("Battery Low can't connect");
            for(BluetoothSocket socket1:connected_socket)
            {
                sendReceive = new SendReceive(socket1);
                sendReceive.start();
                try {
                    sendReceive.write(test.objectToByteArray((device_name + ":Battery Level:Batter level is low").toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            connected_socket=new ArrayList<BluetoothSocket>();
            connection_status=new HashMap<BluetoothSocket, ArrayList<String>>();
            available_devices=0;
            return;

        }
        //Checks if there are any free slaves to offload
        if(available_devices>0) {
            for (BluetoothSocket socket : connected_socket) {
                //Checks if there are any rows that are yet to be sent
                if (socket != null && connection_status.get(socket).get(1) == "free" && output_rowcount != total_rowcount) {
                    //If batter level less than threshold disconnect from all the connected slaves and communicate to slaves to disconnect
                    if(battery_level<battery_threshold)
                    {
                        status.setText("Battery Low can't connect");
                        for(BluetoothSocket socket1:connected_socket)
                        {
                            sendReceive = new SendReceive(socket1);
                            sendReceive.start();
                            try {
                                sendReceive.write(test.objectToByteArray((device_name + ":Battery Level:Batter level is low").toString()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        connected_socket=new ArrayList<BluetoothSocket>();
                        connection_status=new HashMap<BluetoothSocket, ArrayList<String>>();
                        available_devices=0;
                        break;

                    }
                    try {
                        //Check if there are any rows that are yet to be sent
                        if (row_check.size() > 0) {
                            sendReceive = new SendReceive(socket);
                            sendReceive.start();

                            ArrayList<String> temp = connection_status.get(socket);
                            temp.set(1, "busy");
                            available_devices--;
                            connection_status.put(socket, temp);
                            Log.d("MySocket", connection_status.get(socket).get(1));
                            int temp_row = row_check.get(0);
                            row_check.remove(0);
                            row_sent_time.put(temp_row, System.nanoTime());
                            ProxyRequest request = new ProxyRequest(inputs_A[temp_row], inputs_B, temp_row);

                            sendReceive.write(test.objectToByteArray(request));


                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //index_inputs++;
                }

            }
        }

    }

    //This function generate matrix of size rc*cc with random numbers between 1 & 10
    private int[][] generateRandom(int rc,int cc)
    {
        Random rand=new Random();
        int[][] random_array=new int[rc][cc];
        for(int i=0;i<rc;i++)
        {
            for(int j=0;j<cc;j++)
            {
                random_array[i][j]=rand.nextInt(10);
            }
        }
        return random_array;
    }

    //This handler will handle what to do based on the msg received and sets the message in status according to it
    Handler handler=new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(@NonNull Message msg)
        {
            switch(msg.what)
            {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_DISCONNECTED:
                    status.setText("Disconnected");
                    break;

                //this functionality will be called if slave has received message from master or viceversa
                case STATE_MESSAGE_RECEIVED:
                    //If the device is acting as master below logic holds
                    if(client_server=="client")
                    {
                        //If batter level less than threshold disconnect from all the connected slaves and communicate to slaves to disconnect
                        if(battery_level<battery_threshold)
                        {
                            status.setText("Battery Low can't connect");
                            for(BluetoothSocket socket:connected_socket)
                            {
                                sendReceive = new SendReceive(socket);
                                sendReceive.start();
                                try {
                                    sendReceive.write(test.objectToByteArray((device_name + ":Battery Level:Batter level is low").toString()));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            connected_socket=new ArrayList<BluetoothSocket>();
                            connection_status=new HashMap<BluetoothSocket, ArrayList<String>>();
                            available_devices=0;

                        }
                        byte[] readBuff = (byte[]) msg.obj;
                        try
                        {
                            //message received from slave
                            Object o=test.byteArrayToObject(readBuff);
                            //if object is of type string normal message related to connection status or battery level is rceived by master from slave
                            if(o instanceof String)
                            {
                                String tempMsg = (String) o;
                                String[] messages=tempMsg.split(":");
                                //If batter level low message is being received from slave disconnect to that slave and remove that slave from connected devices
                                if(messages[2].equals("Batter level is low"))
                                {
                                    ArrayList<String> temp= new ArrayList<>();
                                    ArrayList<String> temp1= new ArrayList<>();
                                    temp.add(messages[0]);
                                    temp.add("busy");
                                    temp1.add(messages[0]);
                                    temp1.add("free");
                                    for (BluetoothSocket key : connection_status.keySet()) {
                                        if (temp.equals(connection_status.get(key)) || temp1.equals(connection_status.get(key))) {
                                            available_devices--;
                                            System.out.println("Available devices count inside battery level" + available_devices);
                                            connected_socket.remove(key);
                                            connection_status.remove(key);
                                            Toast.makeText(getApplicationContext(),"Removing device"+connected_socket.size(),Toast.LENGTH_LONG).show();
                                            battery_details+="Batter level is low at "+messages[0]+" hence disconnected"+"\n";
                                            msg_box.setText(battery_details);
                                            break;
                                        }
                                    }

                                }
                                /*if slave has sent a msg to master asking it to set it to free master will set the availability status of slave to free.
                                This will happen when slave has accepted offloading after rejecting offloading and that time offloading at master is already done
                                 */
                                else if(messages[2].equals("SetMeFree"))
                                {
                                    ArrayList<String> temp = new ArrayList<>();
                                    temp.add(messages[0]);
                                    temp.add("busy");
                                    for (BluetoothSocket key : connection_status.keySet()) {
                                        if (temp.equals(connection_status.get(key))) {
                                            temp.set(1, "free");
                                            System.out.println("I am set to free");
                                            available_devices++;
                                            System.out.println("Available devices count" + available_devices);
                                            connection_status.put(key, temp);
                                            break;
                                        }
                                    }

                                }
                                /*
                                Otherwise slave has sent just it's battery level stats to master
                                 */
                                else
                                {
                                    if(!battery_initial.containsKey(messages[0])) {
                                        battery_details += messages[0]+":"+messages[1]+":"+messages[2] + "\n";
                                        msg_box.setText(battery_details.trim());
                                        //If slave is connecting to master for the first time slave will send it's battery level information and GPS location to master
                                        if(messages.length>3) {
                                            Double dist = distance_latlong(parseDouble(location_device.split(",")[0]), Double.parseDouble(location_device.split(",")[1]), Double.parseDouble(messages[4].split(",")[0]), Double.parseDouble(messages[4].split(",")[1]));
                                            //if(dist<=0.00195) {
                                            //if slave is within 0.1 mile radius then only connect
                                            if (dist <= 0.1) {
                                                //Toast.makeText(getApplicationContext(), messages[0] + ":" + dist, Toast.LENGTH_LONG).show();
                                                battery_initial.put(messages[0], Integer.parseInt(messages[2]));
                                            }
                                            //otherwise disconnect from slave
                                            else
                                            {
                                                for (BluetoothSocket socket : connection_status.keySet()) {
                                                    if (connection_status.get(socket).get(0).equals(messages[0])) {
                                                        sendReceive = new SendReceive(socket);
                                                        sendReceive.start();
                                                        try {
                                                            sendReceive.write(test.objectToByteArray(device_name + ":Disconnect:Disconnect"));
                                                            //temp_socket.close();
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                        }
                                                        status.setText("Disconnected");
                                                        Toast.makeText(getApplicationContext(), "Device too far....so disconnecting", Toast.LENGTH_LONG).show();
                                                        connection_status.remove(socket);
                                                        connected_socket.remove(socket);
                                                        available_devices--;
                                                    }
                                                }
                                            }

                                            System.out.println(dist);
                                        }
                                        //If slave is connecting to master for the first time slave will send it's battery level information and it hasn't sent it's GPS location
                                        else
                                        {
                                            battery_initial.put(messages[0], Integer.parseInt(messages[2]));
                                        }



                                    }
                                    //otherwise slave is sending it's battery stats post completion of offloading so that master can check the batter level drop at slaves
                                    if(battery_initial.containsKey(messages[0]) && output_rowcount==total_rowcount)
                                    {
                                        battery_final.put(messages[0],Math.abs(battery_initial.get(messages[0])-Integer.parseInt(messages[2])));
                                    }
                                }
                            }
                            //Master has received the matrix multiplication row result as response from slave
                            else
                            {
                                ProxyResponse tempMsg=(ProxyResponse)o;
                                ArrayList<String> temp = new ArrayList<>();
                                //if output result doesn't contain the row result sent by slave it will be stored in output result
                                if(!outputRows_check.containsKey(tempMsg.getRow())) {
                                    display_msg+=tempMsg.getDeviceName()+": Received row "+tempMsg.getRow()+": "+ Arrays.toString(tempMsg.getrowResult())+"\n";
                                    msg_box.setText(display_msg.trim());
                                    output_Array[tempMsg.getRow()]=tempMsg.getrowResult();
                                    output_rowcount++;
                                    outputRows_check.put(tempMsg.getRow(),"received");
                                }
                                //Once receiving result from slave master will set slaves availablity status to free
                                temp.add(tempMsg.getDeviceName());
                                temp.add("busy");
                                for (BluetoothSocket key : connection_status.keySet()) {
                                    if (temp.equals(connection_status.get(key))) {
                                        temp.set(1, "free");
                                        System.out.println("I am set to free");
                                        available_devices++;
                                        System.out.println("Available devices count" + available_devices);
                                        connection_status.put(key, temp);
                                        break;
                                    }
                                }
                                /*if master has received results of all the rows it will calculate the matrix multiplication without offloading at its end display below stats
                                    .  Output Result
                                    .  Battery level drop at servers
                                    .  Battery level drop at master because of offloading
                                    .  Time taken for matrix multiplication by offloading in nano seconds
                                    .  Time taken for matrix multiplication without offloading in nano seconds
                                    .  Battery level drop at master for matrix multiplication without offloading
                                 */
                                if(outputRows_check.size()==total_rowcount)
                                {
                                    finish_time=System.nanoTime();
                                    broadcasting_started=1;
                                    offloadingHandler.removeCallbacks(offloadingReceiver);
                                    int batter_change_off=battery_level_start-battery_level;
                                    for(BluetoothSocket socket:connected_socket)
                                    {
                                        sendReceive = new SendReceive(socket);
                                        sendReceive.start();
                                        sendReceive.write(test.objectToByteArray(device_name + ":Battery Level:Send Battery Level"));
                                    }

                                    String output_print="[";
                                    for(int i=0;i<output_Array.length;i++)
                                    {
                                        if(i!=output_Array.length-1) {
                                            output_print += Arrays.toString(output_Array[i]) + "\n";
                                        }
                                        else
                                        {
                                            output_print += Arrays.toString(output_Array[i]);
                                        }
                                    }
                                    output_print+="]\nBattery Levels drop at Slaves:\n";
                                    for(String server_device:battery_final.keySet())
                                    {
                                        output_print+=server_device+" : "+battery_final.get(server_device)+"\n";
                                    }
                                    output_print+="Battery Level drop at Master:"+batter_change_off+"\n";
                                    output_print+="Time taken by MobOff(ns)= "+ (finish_time-start_time)+"\n";

                                    start_time=System.nanoTime();
                                    battery_level_start=battery_level;
                                    int[][] output_Array_master=new int[total_rowcount][total_rowcount];
                                    for(int i=0;i<total_rowcount;i++)
                                    {
                                        for(int j=0;j<total_rowcount;j++)
                                        {
                                            output_Array_master[i][j]=0;
                                            for(int k=0;k<total_rowcount;k++)
                                            {
                                                output_Array_master[i][j]+=inputs_A[i][k]*inputs_B[k][j];
                                            }
                                        }
                                    }
                                    finish_time=System.nanoTime();

                                    output_print+="Time taken without MobOff(ns)= "+ (finish_time-start_time)+"\n";
                                    output_print+="Battery Level drop without offloading at Master:"+(battery_level_start-battery_level);
                                    //Set all the variables to null so that master will be available for next offloading
                                    battery_final=new HashMap<String,Integer>();
                                    outputRows_check=new HashMap<Integer, String>();
                                    row_check=new ArrayList<Integer>();
                                    row_sent_time=new HashMap<Integer, Long>();
                                    display_msg="";
                                    battery_details="";


                                    msg_box.setText("Output received from slaves: \n"+output_print.trim());

                                    send.setEnabled(true);




                                }
                                //if there are rows that are yet to be calculated send that rows to available devices
                                if(broadcasting_started==0 && available_devices>0 && connected_socket.size()>0 && output_rowcount!=total_rowcount && battery_level>=battery_threshold) {
                                    sendMessages();
                                }
                            }

                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    //If the device is acting as server
                    if(client_server=="server")
                    {
                        try
                        {
                            byte[] readBuff = (byte[]) msg.obj;

                            Object o=test.byteArrayToObject(readBuff);
                            //If the object received from client is of ProxyRequest then slave has received work to act on masters request
                            if(o instanceof ProxyRequest)
                            {
                                ProxyRequest tempMsg = (ProxyRequest) o;

                                display_msg += "Received row " + tempMsg.getRow() + " from Master" + "\n";
                                //calucate the matrix multiplication

                                msg_box.setText(display_msg.trim());
                                for (BluetoothSocket socket : connected_socket)
                                {
                                    if (socket != null)
                                    {
                                        int[] result_output = new int[tempMsg.getB()[0].length];
                                        for (int i = 0; i < tempMsg.getB()[0].length; i++) {
                                            int sum = 0;
                                            for (int j = 0; j < tempMsg.getA().length; j++) {
                                                sum += tempMsg.getA()[j] * tempMsg.getB()[j][i];

                                            }
                                            result_output[i] = sum;
                                            System.out.println("i" + i);
                                            // for(int )
                                            //System.out.println(result_output[i]);
                                        }
                                        //Create a response of object of type ProxyResponse
                                        ProxyResponse response = new ProxyResponse(result_output, tempMsg.getRow(), device_name);
                                        //If it hasn't rejcted offloading send the result row  and row number to master
                                        if (rejectMsgFlag == 0 && battery_level>=battery_threshold) {
                                            sendReceive.write(test.objectToByteArray(response));
                                        }
                                        //otherwise temporarily store the row result in case it has accepted offloading and has to send row result
                                        else
                                        {
                                            response_temp=response;
                                        }
                                        //If batter level less than threshold disconnect from master and communicate to master to disconnect from it
                                        if (battery_level < battery_threshold) {
                                            sendReceive.write(test.objectToByteArray(device_name + ":Battery Level:Batter level is low"));
                                            //bluetoothAdapter.disable();
                                            //connection_status = new HashMap<BluetoothSocket, ArrayList<String>>();
                                            if(connection_status.size()>0)
                                            {
                                                connection_status.remove(connected_socket.get(0));
                                                connected_socket.remove(0);
                                            }
                                        }

                                    }
                                }
                            }
                            //if object received is of type string
                            else if(o instanceof String)
                            {
                                String tempMsg=(String) o;
                                //Toast.makeText(getApplicationContext(),tempMsg,Toast.LENGTH_LONG).show();
                                String[] messages=tempMsg.split(":");
                                //If slave has received Battery level low from master disconnect from master
                                if(messages[2].equals("Batter level is low"))
                                {
                                    status.setText("Disconnected");
                                    msg_box.setText("Battery low at master");
                                    if(connection_status.size()>0)
                                    {
                                        serverSocket.close();
                                        connection_status.remove(connected_socket.get(0));
                                        connected_socket.remove(0);
                                    }
                                }
                                //else if slave has received message Disconnect from master, disconnect from master
                                else if(messages[2].equals("Disconnect"))
                                {
                                    status.setText("Disconnected");
                                    msg_box.setText("Disconnected");
                                    if(connection_status.size()>0)
                                    {
                                        serverSocket.close();
                                        connection_status.remove(connected_socket.get(0));
                                        connected_socket.remove(0);
                                    }

                                }
                                //else master is just asking for battery level stats
                                else {
                                    battery_check_count++;
                                    sendReceive.write(test.objectToByteArray(device_name + ":Battery Level:" + Integer.toString(battery_level)));
                                }
                            }
                            else
                            {
                                Toast.makeText(getApplicationContext(),o.getClass().getName(),Toast.LENGTH_LONG).show();
                            }

                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                    break;
                case STATE_BATTERY_LOW:
                    status.setText("Battery Low Can't connect");
                    break;
            }
            return true;
        }
    });

    //Initialize the components for the screen
    private void findViewByIdes()
    {
        listen=(Button) findViewById(R.id.listen);
        send=(Button) findViewById(R.id.sendButton);
        listView=(ListView) findViewById(R.id.peerListView);
        msg_box=(TextView) findViewById(R.id.readMsg);
        status=(TextView) findViewById(R.id.connectionStatus);
        batterystatus=(TextView) findViewById(R.id.batterystatus);
        listDevices=(Button) findViewById(R.id.list_devices);
        showconnected=(Button)findViewById(R.id.showConnected);
        matrixSize=(EditText) findViewById(R.id.matrixSize);
        rejectMsg=(Button) findViewById(R.id.RejectMsg);
    }

    //Get the distance between two locations here between two devices in miles
    private static double distance_latlong(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == lat2) && (lon1 == lon2)) {
            return 0;
        }
        else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;

            return (dist);
        }
    }


    /*On clicking listen this class will be called and if battery level is more than the threshold then slave will key on listening if any device is trying to connect to it
    with the same UUID. If any device is trying to connect to it it will connect to it and will store the socket information of the connected device so that it can communicate with
    that device later
     */
    private class ServerClass extends Thread
    {


        public ServerClass()
        {
            try
            {
                if(battery_level>=battery_threshold)
                {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket socket=null;

            if(battery_level<battery_threshold)
            {
                Message message=Message.obtain();
                message.what=STATE_BATTERY_LOW;
                handler.sendMessage(message);
            }
            //will keep on looking until it's connected to any device with the same UUID
            while(socket==null && battery_level>=battery_threshold)
            {
                try
                {
                    System.out.println("Inside sever");
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);

                    socket=serverSocket.accept();

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket!=null)
                {

                    System.out.println("Inside sever");
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);


                    connected_socket.add(socket);

                    if(!connection_status.containsKey(socket))
                    {
                        ArrayList<String> temp=new ArrayList<String>();
                        temp.add(socket.getRemoteDevice().getName());
                        temp.add("free");
                        available_devices++;
                        connection_status.put(socket,temp);
                    }
                    sendReceive=new SendReceive(socket);
                    sendReceive.start();

                    try {
                        //Toast.makeText(getApplicationContext(),"check"+location,Toast.LENGTH_LONG).show();
                        if(location_device!=null && !location_device.isEmpty()) {
                            sendReceive.write(test.objectToByteArray(device_name + ":Battery Level:" + Integer.toString(battery_level) + ":Location:" + location_device));
                        }
                        else
                        {
                            sendReceive.write(test.objectToByteArray(device_name + ":Battery Level:" + Integer.toString(battery_level)));
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }


    /*
    On clicking the device in list view at master, if battery level is greater than the threshold this class will start and based on the device name master will connect to the slave based on
    the UUID and get the socket information of the slave.
     */
    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass (BluetoothDevice device1)
        {
            device=device1;

            try {
                if(battery_level>=battery_threshold)
                {
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
                else
                {
                    listView.setAdapter(null);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            try
            {
                if(battery_level>=battery_threshold)
                {
                    socket.connect();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    sendReceive=new SendReceive(socket);
                    sendReceive.start();
                    handler.sendMessage(message);

                    connected_socket.add(socket);
                    //if the slave socket in not already in the connected devices socket list it will added to that list at master
                    if(!connection_status.containsKey(socket))
                    {
                        ArrayList<String> temp=new ArrayList<String>();
                        temp.add(device.getName());
                        temp.add("free");
                        available_devices++;
                        connection_status.put(socket,temp);
                    }

                }
                else
                {
                    Message message = Message.obtain();
                    message.what = STATE_BATTERY_LOW;
                    handler.sendMessage(message);
                    listView.setAdapter(null);
                }

            }
            catch (IOException e)
            {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    /*
    This class is mainly responsible for sending and receiving messages based on the socket with the help of input stream and output stream
     */
    private class SendReceive extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;


            try
            {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            }
            catch (IOException e)
            {
                if(connection_status.containsKey(bluetoothSocket))
                {
                    if(connection_status.get(bluetoothSocket).get(1)=="free"){
                        available_devices--;
                    }
                    connection_status.remove(bluetoothSocket);
                    connected_socket.remove(bluetoothSocket);

                }
                if(client_server=="client")
                {
                    send.setEnabled(true);
                }
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }


        //This will always check if there is any message that has to be sent to a particular socket form this device by always reading the output stream with the help of input stream
        public void run()
        {
            byte[] buffer=new byte[102400];
            int bytes;


            while(battery_level>=battery_threshold)
            {
                try
                {
                    if(connection_status.containsKey(bluetoothSocket)) {
                        bytes = inputStream.read(buffer);
                        System.out.println("I am here: " + bytes);
                        handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                    }

                }
                catch (IOException e)
                {
                    if(connection_status.containsKey(bluetoothSocket))
                    {
                        if(connection_status.get(bluetoothSocket).get(1)=="free"){
                            available_devices--;
                        }
                        connection_status.remove(bluetoothSocket);
                        connected_socket.remove(bluetoothSocket);
                    }
                    if(client_server=="client")
                    {
                        send.setEnabled(true);
                    }
                    e.printStackTrace();
                }
            }
            if(battery_level<battery_threshold)
            {

                try
                {
                    write(test.objectToByteArray(device_name+":Battery Level:Batter level is low"));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                try
                {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                }
                catch (IOException e)
                {
                    if(connection_status.containsKey(bluetoothSocket))
                    {
                        if(connection_status.get(bluetoothSocket).get(1)=="free"){
                            available_devices--;
                        }
                        connection_status.remove(bluetoothSocket);
                        connected_socket.remove(bluetoothSocket);
                    }
                    if(client_server=="client")
                    {
                        send.setEnabled(true);
                    }
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }
            }

        }
        //This function is used to write the message to the output stream of the particular device that has to be sent to certain socket
        public void write(byte[] bytes)
        {
            try
            {
                if(battery_level>=battery_threshold)
                {
                    System.out.println("In outputstream: "+bytes);
                    outputStream.write(bytes);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

    }
}
