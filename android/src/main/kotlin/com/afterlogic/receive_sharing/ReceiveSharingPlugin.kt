package com.afterlogic.receive_sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLConnection


class ReceiveSharingPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler,
    PluginRegistry.NewIntentListener {
    private lateinit var channel: MethodChannel
    private lateinit var mediaEventChannel: EventChannel
    private lateinit var textEventChannel: EventChannel
    private lateinit var applicationContext: Context

    private var latestMedia: JSONArray? = null
    private var latestText: JSONArray? = null
    private var eventSinkMedia: EventChannel.EventSink? = null
    private var eventSinkText: EventChannel.EventSink? = null


    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "receive_sharing/messages")
        channel.setMethodCallHandler(this)
        mediaEventChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "receive_sharing/events-media")
        mediaEventChannel.setStreamHandler(this)
        textEventChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "receive_sharing/events-text")
        textEventChannel.setStreamHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        mediaEventChannel.setStreamHandler(null)
        textEventChannel.setStreamHandler(null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getInitialMedia" -> result.success(latestMedia?.toString())
            "getInitialText" -> result.success(latestText)
            "reset" -> {
                latestMedia = null
                latestText = null
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }


    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        when (arguments) {
            "media" -> eventSinkMedia = events
            "text" -> eventSinkText = events
        }
    }

    override fun onCancel(arguments: Any?) {
        when (arguments) {
            "media" -> eventSinkMedia = null
            "text" -> eventSinkText = null
        }
    }

    override fun onNewIntent(intent: Intent): Boolean {
        handleIntent(applicationContext, intent)
        return false
    }

    private fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return;
        try {
            if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                val value = getMediaUris(context, intent)
                latestMedia = value
                latestMedia?.let {
                    eventSinkMedia?.success(it.toString())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun getTextJson(intent: Intent): JSONArray? {
        var subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "text"
        if (subject.isEmpty()) {
            subject = "text"
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return JSONArray().put(
                JSONObject()
                        .put("name", subject)
                        .put("text", text)
                        .put("type", MimeType.Text.ordinal)
        )
    }

    private fun getMediaUris(context: Context, intent: Intent): JSONArray? {
        if (intent.type?.startsWith("text") == true) {
            getTextJson(intent)?.let {
                return it
            }
        }
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: arrayListOf()
        uri?.let { uris.add(it) }
        val value = uris.mapNotNull { item ->
            val path = FileDirectory.getAbsolutePath(context, item) ?: return@mapNotNull null
            val name = path.split("/").last()
            val type = getMediaType(path)
            JSONObject()
                    .put("name", name)
                    .put("path", path)
                    .put("type", type.ordinal)
        }.toList()
        return JSONArray(value)
    }

    private fun getMediaType(path: String?): MimeType {
        val mimeType = URLConnection.guessContentTypeFromName(path) ?: return MimeType.Any
        return when {
            mimeType.startsWith("image") -> {
                MimeType.Image
            }
            mimeType.startsWith("video") -> {
                MimeType.Video
            }
            mimeType.startsWith("text") -> {
                MimeType.Text
            }
            else -> {
                MimeType.Any
            }
        }
    }
}
