/// A simple Result type for error handling without exceptions
///
/// This is a lightweight alternative to using try-catch for expected errors.
/// Use this when you want to make error handling explicit in the type system.
sealed class Result<E, T> {
  const Result();

  /// Check if this result is a success
  bool get isSuccess => this is Success<E, T>;

  /// Check if this result is an error
  bool get isError => this is Error<E, T>;

  /// Get the success value (throws if error)
  T get value => switch (this) {
        Success(value: final v) => v,
        Error() => throw StateError('Called value on Error result'),
      };

  /// Get the error value (throws if success)
  E get error => switch (this) {
        Error(error: final e) => e,
        Success() => throw StateError('Called error on Success result'),
      };

  /// Transform the success value
  Result<E, R> map<R>(R Function(T value) transform) {
    return switch (this) {
      Success(value: final v) => Success(transform(v)),
      Error(error: final e) => Error(e),
    };
  }

  /// Transform the error value
  Result<R, T> mapError<R>(R Function(E error) transform) {
    return switch (this) {
      Success(value: final v) => Success(v),
      Error(error: final e) => Error(transform(e)),
    };
  }

  /// Execute a function if this is a success
  Result<E, R> flatMap<R>(Result<E, R> Function(T value) transform) {
    return switch (this) {
      Success(value: final v) => transform(v),
      Error(error: final e) => Error(e),
    };
  }

  /// Execute a function based on success or error
  R fold<R>({
    required R Function(T value) onSuccess,
    required R Function(E error) onError,
  }) {
    return switch (this) {
      Success(value: final v) => onSuccess(v),
      Error(error: final e) => onError(e),
    };
  }

  /// Get the value or a default
  T getOrElse(T defaultValue) {
    return switch (this) {
      Success(value: final v) => v,
      Error() => defaultValue,
    };
  }

  /// Get the value or compute a default from the error
  T getOrElseGet(T Function(E error) defaultValue) {
    return switch (this) {
      Success(value: final v) => v,
      Error(error: final e) => defaultValue(e),
    };
  }
}

/// Represents a successful result
final class Success<E, T> extends Result<E, T> {
  final T value;

  const Success(this.value);

  @override
  String toString() => 'Success($value)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Success<E, T> && value == other.value;

  @override
  int get hashCode => value.hashCode;
}

/// Represents an error result
final class Error<E, T> extends Result<E, T> {
  final E error;

  const Error(this.error);

  @override
  String toString() => 'Error($error)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Error<E, T> && error == other.error;

  @override
  int get hashCode => error.hashCode;
}

/// Helper function to create a Success result
Success<E, T> success<E, T>(T value) => Success(value);

/// Helper function to create an Error result
Error<E, T> error<E, T>(E error) => Error(error);

/// Extension to convert nullable values to Results
extension NullableToResult<T> on T? {
  Result<E, T> toResult<E>(E error) {
    final value = this;
    if (value != null) {
      return Success(value);
    } else {
      return Error(error);
    }
  }
}
