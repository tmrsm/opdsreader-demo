package com.example.opdsreader.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url
import com.example.opdsreader.data.model.OpdsFeed

interface OpdsApi {
    @GET
    suspend fun getFeed(@Url url: String): Response<OpdsFeed>
}

class OpdsRepository(
    private val api: OpdsApi
) {
    suspend fun getFeed(url: String): Result<OpdsFeed> = try {
        val response = api.getFeed(url)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("Failed to fetch OPDS feed: ${response.code()} ${response.message()}"))
        }
    } catch (e: Exception) {
        // 处理不同类型的网络异常，提供更具体的错误信息
        val errorMessage = when (e) {
            is java.net.UnknownHostException -> "无法解析主机名: ${e.message}. 请检查网络连接或服务器地址是否正确。"
            is java.net.SocketTimeoutException -> "连接超时: ${e.message}. 服务器响应时间过长。"
            is java.io.IOException -> "网络错误: ${e.message}"
            is retrofit2.HttpException -> "HTTP错误: ${e.code()} ${e.message()}"
            else -> "未知错误: ${e.message}"
        }
        Result.failure(Exception(errorMessage, e))
    }
}