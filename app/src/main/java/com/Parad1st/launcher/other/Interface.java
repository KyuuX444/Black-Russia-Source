package com.byparad1st.launcher.other;

import com.byparad1st.launcher.model.Servers;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface Interface {

    @GET("https://raw.githubusercontent.com/KyuuX444/Black-Russia-Source/main/Json%20files/servers.json")
    Call<List<Servers>> getServers();
}
