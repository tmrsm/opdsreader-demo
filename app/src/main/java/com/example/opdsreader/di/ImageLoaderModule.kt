package com.example.opdsreader.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import android.util.Log

object ImageLoaderModule {
    fun provideImageLoader(context: Context): ImageLoader {
        // 使用与NetworkModule相同的OkHttpClient配置
        val okHttpClient = NetworkModule.provideOkHttpClient()
        
        // 创建内存缓存
        val memoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 使用25%的可用内存作为缓存
            .build()
        
        // 创建磁盘缓存
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizePercent(0.02) // 使用2%的可用磁盘空间作为缓存
            .build()
        
        Log.d("ImageLoaderModule", "Creating ImageLoader with memory and disk cache")
        
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient) // 使用配置好的OkHttpClient
            .memoryCache(memoryCache) // 添加内存缓存
            .diskCache(diskCache) // 添加磁盘缓存
            .respectCacheHeaders(false) // 忽略HTTP缓存头，优先使用本地缓存
            .logger(DebugLogger()) // 添加日志记录以便调试
            .allowHardware(false) // 禁用硬件加速，避免某些緩存問題
            .crossfade(false) // 禁用默認的crossfade效果
            .build()
    }
}