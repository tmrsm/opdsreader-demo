package com.example.opdsreader.di

import com.example.opdsreader.data.network.OpdsApi
import com.example.opdsreader.data.network.OpdsRepository
import okhttp3.OkHttpClient
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory

object NetworkModule {
    fun provideOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }

            .build()
    }

    private fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(
                SimpleXmlConverterFactory.createNonStrict(
                    Persister(AnnotationStrategy())
                )
            )
            .baseUrl("https://placeholder.example.com/") // 只是一个占位符，因为我们使用完整的URL
            .build()
    }

    private fun provideOpdsApi(): OpdsApi {
        val okHttpClient = provideOkHttpClient()
        val retrofit = provideRetrofit(okHttpClient)
        return retrofit.create(OpdsApi::class.java)
    }

    fun provideOpdsRepository(): OpdsRepository {
        return OpdsRepository(provideOpdsApi())
    }
}