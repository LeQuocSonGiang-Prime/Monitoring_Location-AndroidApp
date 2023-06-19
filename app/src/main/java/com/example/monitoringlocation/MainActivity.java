package com.example.monitoringlocation;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.monitoringlocation.api.LocationAPI;
import com.example.monitoringlocation.api.PersonAPI;
//import com.example.monitoringlocation.service.ForegroundService;
import com.example.monitoringlocation.service.ForegroundService;
import com.google.android.gms.location.*;
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


public class MainActivity extends AppCompatActivity {
    private LocationCallback locationCallback;
    String token;

    LocationDatabaseHelper dbHelper;

    private EditText tokenEditText;
    private ProgressDialog progressDialog;
    private long countdownTimeRemaining = 0;

    private Intent serviceIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        // Tao  ForegroundService
        serviceIntent = new Intent(this, ForegroundService.class);

        dbHelper = new LocationDatabaseHelper(getApplicationContext());

        tokenEditText = findViewById(R.id.tokenEditText);
        Button confirmButton = findViewById(R.id.confirmButton);
        if (!(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                !(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(">>>>>>>>>>>>>>>>>>>>>", "Dang yeu cau nhan quyen");
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            Log.d(">>>>>>>>>>>>>>>>>>>>>", "da co quyen truy cap roi ma");
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(">>>>>>>>>>>>>>>>>>>>>", "da co quyen truy cap BACKGROUND");
        } else {
            Log.d(">>>>>>>>>>>>>>>>>>>>>", "Chua co quyen truy cap BACKGROUND");
        }
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                token = tokenEditText.getText().toString();
                getIdPerson(token);
            }
        });
    }


    public void getIdPerson(String token) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang kết nối máy chủ...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        Log.d("=========================> get id person", countdownTimeRemaining + "");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://e9ac-113-161-41-79.ngrok-free.app/monitoring-location/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        PersonAPI api = retrofit.create(PersonAPI.class);
        Call<ResponseData> call = api.check_token(token);
        call.enqueue(new Callback<ResponseData>() {
            @Override
            public void onResponse(Call<ResponseData> call, Response<ResponseData> response) {
                Log.d("Generic Method+>>>>>>>>>>>>>>>", response.code() + "");
                ResponseData responseBody = response.body();
                if (response.isSuccessful()) {
                    // Status code : OK
                    double double_ID = (double) responseBody.getData();
                    int idPerson = (int) double_ID;
                    serviceIntent.putExtra("idPerson", idPerson);

                    startService(serviceIntent);
                } else {
                    int errorCode = response.code();
                    if (errorCode == 404) {
                        Toast.makeText(getApplicationContext(), "Server không phản hồi", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Token không hợp lệ, vui lòng thử lại", Toast.LENGTH_SHORT).show();
                    }
                }
                progressDialog.dismiss();
            }

            @Override
            public void onFailure(Call<ResponseData> call, Throwable t) {
                // Xử lý lỗi kết nối
                Toast.makeText(getApplicationContext(), " lỗi kết nối", Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
            }
        });
    }


}