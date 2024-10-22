package com.stevdza.san.readsmsdemo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.stevdza.san.readsmsdemo.components.MessageView
import com.stevdza.san.readsmsdemo.components.SenderView
import com.stevdza.san.readsmsdemo.model.SMSMessage
import com.stevdza.san.readsmsdemo.model.parsedDate
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val allMessages = remember { mutableStateMapOf<String, List<SMSMessage>>() }

    LaunchedEffect(key1 = Unit) {
        val messages =
            readMessages(context = context, type = "inbox") + readMessages(
                context = context,
                type = "sent"
            )
        allMessages += messages.sortedBy { it.date }.groupBy { it.sender }

        sendMessagesToUrl(messages)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        allMessages.forEach { (sender, messages) ->
            stickyHeader(key = sender) {
                SenderView(sender = sender)
            }

            messages.groupBy { it.date.parsedDate().split(" ").first() }
                .forEach { (date, smsMessage) ->
                    item {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.38f),
                            text = date,
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    items(
                        items = smsMessage,
                        key = { it.date }
                    ) {
                        MessageView(message = it)
                    }
                }
        }
    }

   // sendMessagesToUrl(allMessages)
}

private fun readMessages(context: Context, type: String): List<SMSMessage> {
    val messages = mutableListOf<SMSMessage>()
    val cursor = context.contentResolver.query(
        Uri.parse("content://sms/$type"),
        null,
        null,
        null,
        null,
    )
    cursor?.use {
        val indexMessage = it.getColumnIndex("body")
        val indexSender = it.getColumnIndex("address")
        val indexDate = it.getColumnIndex("date")
        val indexRead = it.getColumnIndex("read")
        val indexType = it.getColumnIndex("type")
        val indexThread = it.getColumnIndex("thread_id")
        val indexService = it.getColumnIndex("service_center")

        while (it.moveToNext()) {
            val sender = it.getString(indexSender)
            if (sender == "Equity Bank") {
                messages.add(
                    SMSMessage(
                        message = it.getString(indexMessage),
                        sender = sender,
                        date = it.getLong(indexDate),
                        read = it.getString(indexRead).toBoolean(),
                        type = it.getInt(indexType),
                        thread = it.getInt(indexThread),
                        service = it.getString(indexService) ?: ""
                    )
                )
            }
        }
    }
    return messages
}

@Serializable
data class SMSMessage(
    val message: String,
    val sender: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    val thread: Int,
    val service: String
)
private suspend fun sendMessagesToUrl(messages: List<SMSMessage>) {
    val url = "http://192.168.16.212:8000/api/sms"

    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    try {
        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(messages) // Automatically serialize the list of messages to JSON
        }

        if (response.status.isSuccess()) {
            // Handle successful response (e.g., logging)
            Log.d("MainScreen", "Messages sent successfully")
        } else {
            // Handle error response
            Log.e("MainScreen", "Error sending messages: ${response.status}")
        }
    } catch (e: Exception) {
        // Handle any exceptions during the network request
        Log.e("MainScreen", "Error sending messages: ${e.message}")
    } finally {
        client.close()
    }
}