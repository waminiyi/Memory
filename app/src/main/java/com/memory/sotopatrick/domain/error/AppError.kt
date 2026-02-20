package com.memory.sotopatrick.domain.error

data class FailureContext(
    val code: String,
    val message: String,
    val recoverable: Boolean,
    val cause: Throwable? = null
)

enum class RecoveryAction {
    Retry,
    GoLobby,
    RequestPermission,
    Reconnect
}

enum class ErrorSeverity {
    Inline,
    Banner,
    Blocking
}

sealed interface AppError {
    val context: FailureContext
    val action: RecoveryAction
    val severity: ErrorSeverity
}

data class PermissionError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.RequestPermission,
    override val severity: ErrorSeverity = ErrorSeverity.Blocking
) : AppError

data class BluetoothUnavailableError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.Reconnect,
    override val severity: ErrorSeverity = ErrorSeverity.Blocking
) : AppError

data class ConnectionTimeoutError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.Retry,
    override val severity: ErrorSeverity = ErrorSeverity.Banner
) : AppError

data class GameRuleViolationError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.Retry,
    override val severity: ErrorSeverity = ErrorSeverity.Inline
) : AppError

data class SerializationError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.Reconnect,
    override val severity: ErrorSeverity = ErrorSeverity.Banner
) : AppError

data class UnexpectedError(
    override val context: FailureContext,
    override val action: RecoveryAction = RecoveryAction.GoLobby,
    override val severity: ErrorSeverity = ErrorSeverity.Banner
) : AppError
