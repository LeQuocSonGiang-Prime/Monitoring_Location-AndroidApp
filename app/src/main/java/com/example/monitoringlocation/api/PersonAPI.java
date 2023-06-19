package com.example.monitoringlocation.api;

import com.example.monitoringlocation.ResponseData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.List;

public interface PersonAPI {

    @Headers("Content-Type: text/plain")
    @POST("person/check_person")
    Call<ResponseData> check_token(@Body String token);
}
