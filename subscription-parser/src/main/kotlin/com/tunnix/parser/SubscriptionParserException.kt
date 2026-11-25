package com.tunnix.parser

sealed class SubscriptionParserException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidDomainException(domain: String?) :
        SubscriptionParserException("دامنه '$domain' غیرمجاز است. / Domain '$domain' is not allowed.")

    class NetworkException(message: String, cause: Throwable? = null) :
        SubscriptionParserException("خطا در برقراری ارتباط با سرور سابسکریپشن: $message / Network error: $message", cause)

    class EmptySubscriptionException :
        SubscriptionParserException("سابسکریپشن خالی است یا سروری یافت نشد. / Subscription is empty or contains no valid servers.")

    class UnsupportedFormatException(message: String) :
        SubscriptionParserException("فرمت لینک یا محتوا نامعتبر است: $message / Invalid format: $message")
}
