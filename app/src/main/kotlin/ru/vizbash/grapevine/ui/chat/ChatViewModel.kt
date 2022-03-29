package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.message.MessageWithOrig
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val profileProvider: ProfileProvider,
) : ViewModel() {
    val myId = 1L

    val pagedMessages = Pager(PagingConfig(pageSize = 8, enablePlaceholders = false)) {
        FakePageSource()
    }.flow.cachedIn(viewModelScope)

    inner class FakePageSource : PagingSource<Int, MessageWithOrig>() {
        override fun getRefreshKey(state: PagingState<Int, MessageWithOrig>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageWithOrig> {
            return LoadResult.Page(
                prevKey = null,
                nextKey = null,
                data = listOf(
                    MessageWithOrig(
                        msg = Message(
                            id = 1,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 1 Сообщение 1 Сообщение 1 Сообщение 1 Сообщение 1 Сообщение 1",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                        orig = null,
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 2,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 2",
                            origMsgId = 1,
                            file = null,
                            state = Message.State.DELIVERED,
                        ),
                        orig = Message(
                            id = 1,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 1",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 3,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 3",
                            origMsgId = null,
                            file = MessageFile(
                                uri = null,
                                name = "test.jpg",
                                size = 325253,
                                isDownloaded = false,
                            ),
                            state = Message.State.READ,
                        ),
                        orig = null,
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 4,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 4",
                            origMsgId = 1,
                            file = MessageFile(
                                uri = null,
                                name = "test.jpg",
                                size = 325253,
                                isDownloaded = false,
                            ),
                            state = Message.State.DELIVERY_FAILED,
                        ),
                        orig = Message(
                            id = 1,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 1",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                    ),


                    MessageWithOrig(
                        msg = Message(
                            id = 5,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = 23,
                            text = "Сообщение 5",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                        orig = null,
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 6,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = 23,
                            text = "Сообщение 6",
                            origMsgId = 1,
                            file = null,
                            state = Message.State.DELIVERED,
                        ),
                        orig = Message(
                            id = 1,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 1",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 7,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = 23,
                            text = "Сообщение 7",
                            origMsgId = null,
                            file = MessageFile(
                                uri = null,
                                name = "test.jpg",
                                size = 325253,
                                isDownloaded = false,
                            ),
                            state = Message.State.READ,
                        ),
                        orig = null,
                    ),
                    MessageWithOrig(
                        msg = Message(
                            id = 8,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = 23,
                            text = "Сообщение 8",
                            origMsgId = 1,
                            file = MessageFile(
                                uri = null,
                                name = "test.jpg",
                                size = 325253,
                                isDownloaded = false,
                            ),
                            state = Message.State.DELIVERY_FAILED,
                        ),
                        orig = Message(
                            id = 1,
                            timestamp = Date(),
                            chatId = 2,
                            senderId = myId,
                            text = "Сообщение 1",
                            origMsgId = null,
                            file = null,
                            state = Message.State.SENT,
                        ),
                    ),
                ),
            )
        }
    }
}