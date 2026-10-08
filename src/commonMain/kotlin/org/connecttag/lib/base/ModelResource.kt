package org.connecttag.lib.base

/**
 * Encapsulates UI/Domain model resource states (Success, Error, Loading).
 */
public sealed class ModelResource<T>(
    public val data: T? = null,
    public val messages: String? = null,
    public val errors: T? = null,
    public val success: Boolean? = true,
) {
    public class Success<T>(data: T?) : ModelResource<T>(data = data)
    public class Error<T>(messages: String?, data: T? = null, errors: T? = null) :
        ModelResource<T>(data = data, messages = messages, errors = errors, success = false)

    public class Loading<T> : ModelResource<T>()
}
