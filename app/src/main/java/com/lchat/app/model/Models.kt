package com.lchat.app.model

import java.io.Serializable

data class SnapItem(
    var key: String = "",
    var fromUid: String = "",
    var fromName: String = "",
    var imageUrl: String = "",
    var imageName: String = "",
    var caption: String = "",
    var createdAt: Long = 0
) : Serializable

data class User(
    var uid: String = "",
    var username: String = "",
    var email: String = "",
    var photoUrl: String = ""
) : Serializable

data class ChatMessage(
    var fromUid: String = "",
    var text: String = "",
    var createdAt: Long = 0
) : Serializable

data class Conversation(
    var otherUid: String = "",
    var name: String = "",
    var lastMessage: String = "",
    var lastMessageAt: Long = 0,
    var unread: Boolean = false
) : Serializable

data class StoryItem(
    var id: String = "",
    var fromUid: String = "",
    var fromName: String = "",
    var imageUrl: String = "",
    var caption: String = "",
    var createdAt: Long = 0
) : Serializable

data class StoryGroup(
    var fromUid: String = "",
    var fromName: String = "",
    var stories: ArrayList<StoryItem> = ArrayList(),
    var viewed: Boolean = false
) : Serializable
