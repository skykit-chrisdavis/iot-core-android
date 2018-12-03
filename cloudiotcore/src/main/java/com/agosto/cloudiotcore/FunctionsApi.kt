package com.agosto.cloudiotcore

import io.reactivex.Observable
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class DeviceRequest(val deviceId: String, val rsaCertificate: String)

data class DeviceRequestResult(val deviceId: String, val name: String, val numId: String, val registryId: String, val projectId:String)


interface FunctionsApi {


    @POST("registerIotDevice")
    fun registerIotDevice(@Body deviceRequest: DeviceRequest): Observable<DeviceRequestResult>

    companion object {
        fun create(baseUrl:String, token : String = ""): FunctionsApi {
            val httpClient = OkHttpClient().newBuilder()
            val interceptor = Interceptor { chain ->
                val request = chain.request().newBuilder().addHeader("Authorization", token).build();
                chain.proceed(request)
            }
            httpClient.networkInterceptors().add(interceptor)

            // causes memory errors with large files
            if (BuildConfig.DEBUG) {
                val logInterceptor = HttpLoggingInterceptor()
                logInterceptor.level = HttpLoggingInterceptor.Level.BODY
                httpClient.addInterceptor(logInterceptor)
            }

            val retrofit = Retrofit.Builder()
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                    .client(httpClient.build())
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .baseUrl(baseUrl)
                    .build()

            return retrofit.create(FunctionsApi::class.java)
        }
    }

}
