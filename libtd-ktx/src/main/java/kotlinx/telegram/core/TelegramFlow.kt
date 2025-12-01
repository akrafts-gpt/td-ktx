package kotlinx.telegram.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Main class to interact with Telegram API client
 * @param resultHandler transforms updates from [TdApi] client to [Flow] of the [TdApi.Object]
 * @param defaultExceptionHandler handles exceptions thrown by TDLib's default handler.
 * Emits [DefaultException] by default.
 * @param updatesExceptionHandler handles exceptions thrown by TDLib's updates handler.
 * Emits [TelegramFlowUpdateException] by default.
 */
class TelegramFlow(
    private val resultHandler: ResultHandlerFlow = ResultHandlerStateFlow(),
    private val defaultExceptionHandler: Client.ExceptionHandler = resultHandler.defaultExceptionHandler,
    private val updatesExceptionHandler: Client.ExceptionHandler = resultHandler.updatesExceptionHandler,
) : Flow<TdApi.Object> by resultHandler, Closeable {

    class TelegramFlowUpdateException(val exception: Throwable?) : TdApi.Object() {
        override fun getConstructor(): Int = CONSTRUCTOR

        companion object {
            const val CONSTRUCTOR: Int = Int.MIN_VALUE
        }
    }

    class DefaultException(val exception: Throwable?) : TdApi.Object() {
        override fun getConstructor(): Int = CONSTRUCTOR

        companion object {
            const val CONSTRUCTOR: Int = Int.MIN_VALUE + 1
        }
    }

    interface ResultHandlerFlow : Client.ResultHandler, Flow<TdApi.Object> {
        val defaultExceptionHandler: Client.ExceptionHandler
        val updatesExceptionHandler: Client.ExceptionHandler
    }

    /**
     * Telegram [Client] instance. Null if instance is not attached
     */
    var client: Client? = null

    /**
     * Attach instance to the existing native Telegram client or create one
     * @param existingClient set an existing client to attach, null by default
     */
    fun attachClient(
        existingClient: Client? = null
    ) {
        if (client != null) return // client is already attached

        client = existingClient
            ?: Client.create(
                resultHandler,
                defaultExceptionHandler,
                updatesExceptionHandler
            )
    }

    /**
     * Return data flow from Telegram API of the given type [T]
     */
    inline fun <reified T : TdApi.Object> getUpdatesFlowOfType() =
        filterIsInstance<T>()

    /**
     * Sends a request to the TDLib and expect a result.
     *
     * @param function [TdApi.Function] representing a TDLib interface function-class.
     * @param ExpectedResult result type expecting from given [function].
     * @throws TelegramException.Error if TdApi request returns an exception
     * @throws TelegramException.UnexpectedResult if TdApi request returns an unexpected result
     * @throws TelegramException.ClientNotAttached if TdApi client has not attached yet
     */
    suspend inline fun <reified ExpectedResult : TdApi.Object>
        sendFunctionAsync(function: TdApi.Function<ExpectedResult>): ExpectedResult =
        suspendCoroutine { continuation ->
            val resultHandler: (TdApi.Object) -> Unit = { result ->
                when (result) {
                    is ExpectedResult -> continuation.resume(result)
                    is TdApi.Error -> continuation.resumeWithException(
                        TelegramException.Error(result.message)
                    )
                    else -> continuation.resumeWithException(
                        TelegramException.UnexpectedResult(result)
                    )
                }
            }
            client?.send(function, resultHandler) { throwable ->
                continuation.resumeWithException(
                    TelegramException.Error(throwable?.message ?: "unknown")
                )
            } ?: throw TelegramException.ClientNotAttached
        }

    /**
     * Sends a request to the TDLib and expect [TdApi.Ok]
     *
     * @param function [TdApi.Function] representing a TDLib interface function-class.
     * @throws TelegramException.Error if TdApi request returns an exception
     * @throws TelegramException.UnexpectedResult if TdApi request returns an unexpected result
     * @throws TelegramException.ClientNotAttached if TdApi client has not attached yet
     */
    suspend fun sendFunctionLaunch(function: TdApi.Function<TdApi.Ok>) {
        sendFunctionAsync<TdApi.Ok>(function)
    }

    /**
     * Closes Client.
     */
    override fun close() {
        client?.close()
        client = null
    }
}