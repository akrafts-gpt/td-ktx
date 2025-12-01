package kotlinx.telegram.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Class that converts results handler from [TdApi] client to [Flow]
 * of the [TdApi.Object] using [MutableSharedFlow]
 */
class ResultHandlerStateFlow(
    private val sharedFlow: MutableSharedFlow<TdApi.Object> = MutableSharedFlow(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
) : TelegramFlow.ResultHandlerFlow, Flow<TdApi.Object> by sharedFlow {

    override val defaultExceptionHandler: Client.ExceptionHandler = Client.ExceptionHandler { throwable ->
        emitException(TelegramFlow.DefaultException(throwable), throwable)
    }

    override val updatesExceptionHandler: Client.ExceptionHandler = Client.ExceptionHandler { throwable ->
        emitException(TelegramFlow.TelegramFlowUpdateException(throwable), throwable)
    }

    override fun onResult(result: TdApi.Object?) {
        result?.let(sharedFlow::tryEmit)
    }

    private fun emitException(exceptionObject: TdApi.Object, throwable: Throwable?) {
        throwable?.printStackTrace()
        sharedFlow.tryEmit(exceptionObject)
    }
}
